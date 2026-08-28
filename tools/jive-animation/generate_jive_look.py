#!/usr/bin/env python3
"""Generate the standalone Jive Look v1 animation from original raster pixels.

Only the two existing pupil components, including their white highlights, move.
The original eye locations are inherited from Blink v5. Translation is rendered
at 8x resolution and downsampled with Lanczos; CENTER frames are exact copies of
the source asset.
"""

from __future__ import annotations

import argparse
import time
from dataclasses import dataclass
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter
from scipy import ndimage

import jive_animation_core as core


FPS = 25
DURATION_MS = 5_000
FRAME_DURATION_MS = 1_000 // FPS
FRAME_COUNT = FPS * DURATION_MS // 1_000
OVERSAMPLING = 8
MAX_GAZE_PX = 4.0

LEFT_MOVE = (1.00, 1.16)
LEFT_HOLD = (1.16, 1.80)
LEFT_RETURN = (1.80, 1.96)
RIGHT_MOVE = (3.00, 3.16)
RIGHT_HOLD = (3.16, 3.64)
RIGHT_RETURN = (3.64, 3.80)

EYE_BBOXES = core.EYE_BBOXES
PUPIL_SEARCH_BBOXES = core.PUPIL_SEARCH_BBOXES


@dataclass(frozen=True)
class PupilAsset:
    eye_bbox: tuple[int, int, int, int]
    source_patch: Image.Image
    clean_high: Image.Image
    pupil_high: Image.Image
    interior_high: Image.Image
    motion_mask_low: Image.Image
    hard_pupil_local: np.ndarray
    eye_interior_local: np.ndarray


@dataclass(frozen=True)
class ValidationResult:
    encoded_frames: int
    timeline_frames: int
    duration_ms: int
    fps: int
    loop: int
    size: tuple[int, int]
    has_alpha: bool
    first_last_identical: bool
    final_center_identical: bool
    outside_pupils_identical: bool
    eye_whites_and_outlines_static: bool
    pupil_highlights_move_together: bool
    pupils_synchronous: bool
    pupils_inside_eyes: bool
    subpixel_path_used: bool
    max_tracking_error_px: float
    max_adjacent_tracking_error_px: float


def smoothstep(value: float) -> float:
    value = max(0.0, min(1.0, value))
    return value * value * (3.0 - 2.0 * value)


def gaze_offset(time_seconds: float) -> float:
    """Return horizontal pupil displacement in source pixels."""
    if LEFT_MOVE[0] <= time_seconds < LEFT_MOVE[1]:
        progress = smoothstep(
            (time_seconds - LEFT_MOVE[0]) / (LEFT_MOVE[1] - LEFT_MOVE[0])
        )
        return -MAX_GAZE_PX * progress
    if LEFT_HOLD[0] <= time_seconds < LEFT_HOLD[1]:
        return -MAX_GAZE_PX
    if LEFT_RETURN[0] <= time_seconds < LEFT_RETURN[1]:
        progress = smoothstep(
            (time_seconds - LEFT_RETURN[0])
            / (LEFT_RETURN[1] - LEFT_RETURN[0])
        )
        return -MAX_GAZE_PX * (1.0 - progress)
    if RIGHT_MOVE[0] <= time_seconds < RIGHT_MOVE[1]:
        progress = smoothstep(
            (time_seconds - RIGHT_MOVE[0])
            / (RIGHT_MOVE[1] - RIGHT_MOVE[0])
        )
        return MAX_GAZE_PX * progress
    if RIGHT_HOLD[0] <= time_seconds < RIGHT_HOLD[1]:
        return MAX_GAZE_PX
    if RIGHT_RETURN[0] <= time_seconds < RIGHT_RETURN[1]:
        progress = smoothstep(
            (time_seconds - RIGHT_RETURN[0])
            / (RIGHT_RETURN[1] - RIGHT_RETURN[0])
        )
        return MAX_GAZE_PX * (1.0 - progress)
    return 0.0


def largest_component(binary: np.ndarray) -> np.ndarray:
    return core.largest_component(binary)


def pupil_mask_local(
    source_rgba: np.ndarray,
    eye_bbox: tuple[int, int, int, int],
    search_bbox: tuple[int, int, int, int],
) -> np.ndarray:
    ex0, ey0, ex1, ey1 = eye_bbox
    sx0, sy0, sx1, sy1 = search_bbox
    patch = source_rgba[ey0:ey1, ex0:ex1]
    local = patch[sy0 - ey0 : sy1 - ey0, sx0 - ex0 : sx1 - ex0]
    # Keep only the pupil body. Its original highlight is retained by filling
    # the component's hole; high-resolution rendering rebuilds edge AA.
    dark = (local[:, :, :3].max(axis=2) < 120) & (local[:, :, 3] > 12)
    component = ndimage.binary_fill_holes(largest_component(dark))
    result = np.zeros(patch.shape[:2], dtype=bool)
    result[sy0 - ey0 : sy1 - ey0, sx0 - ex0 : sx1 - ex0] = component
    return result


def eye_interior_local(patch_rgba: np.ndarray) -> np.ndarray:
    white = (
        (patch_rgba[:, :, :3].min(axis=2) > 180)
        & (patch_rgba[:, :, 3] > 12)
    )
    white = ndimage.binary_closing(white, iterations=2)
    return ndimage.binary_fill_holes(largest_component(white))


def harmonic_clear_patch(
    patch_rgba: np.ndarray,
    pupil_hard: np.ndarray,
) -> Image.Image:
    """Reconstruct the small white area hidden by the source pupil."""
    fill_area = ndimage.binary_dilation(pupil_hard, iterations=3)
    boundary = ndimage.binary_dilation(fill_area, iterations=1) & ~fill_area
    work = patch_rgba.astype(np.float32)
    initial = work[boundary].mean(axis=0)
    work[fill_area] = initial
    for _ in range(180):
        neighbors = (
            np.roll(work, 1, axis=0)
            + np.roll(work, -1, axis=0)
            + np.roll(work, 1, axis=1)
            + np.roll(work, -1, axis=1)
        ) * 0.25
        work[fill_area] = neighbors[fill_area]
    filled = Image.fromarray(np.clip(work, 0, 255).astype(np.uint8), mode="RGBA")
    soft = Image.fromarray((fill_area * 255).astype(np.uint8), mode="L").filter(
        ImageFilter.GaussianBlur(0.65)
    )
    source_patch = Image.fromarray(patch_rgba, mode="RGBA")
    source_patch.paste(filled, (0, 0), soft)
    return source_patch


def resize_rgba(image: Image.Image, size: tuple[int, int]) -> Image.Image:
    return core.resize_rgba(image, size)


def build_pupil_assets(source: Image.Image, oversampling: int) -> list[PupilAsset]:
    if oversampling < 2:
        raise ValueError("Oversampling must be at least 2x")
    source_rgba = np.array(source.convert("RGBA"))
    assets: list[PupilAsset] = []
    for eye_bbox, search_bbox in zip(EYE_BBOXES, PUPIL_SEARCH_BBOXES):
        x0, y0, x1, y1 = eye_bbox
        patch_rgba = source_rgba[y0:y1, x0:x1]
        source_patch = Image.fromarray(patch_rgba, mode="RGBA")
        pupil_hard = pupil_mask_local(source_rgba, eye_bbox, search_bbox)
        interior = eye_interior_local(patch_rgba)

        pupil_mask = Image.fromarray(
            (pupil_hard * 255).astype(np.uint8), mode="L"
        )
        pupil_layer = Image.new("RGBA", source_patch.size, (0, 0, 0, 0))
        pupil_layer.paste(source_patch, (0, 0), pupil_mask)
        clean_patch = harmonic_clear_patch(patch_rgba, pupil_hard)

        high_size = (
            source_patch.width * oversampling,
            source_patch.height * oversampling,
        )
        interior_high = Image.fromarray(
            (interior * 255).astype(np.uint8), mode="L"
        ).resize(high_size, Image.Resampling.LANCZOS)

        motion_area = ndimage.binary_dilation(
            pupil_hard,
            iterations=int(np.ceil(MAX_GAZE_PX)) + 5,
        )
        motion_mask_low = Image.fromarray(
            (motion_area * 255).astype(np.uint8), mode="L"
        ).filter(ImageFilter.GaussianBlur(0.45))

        assets.append(
            PupilAsset(
                eye_bbox=eye_bbox,
                source_patch=source_patch,
                clean_high=resize_rgba(clean_patch, high_size),
                pupil_high=resize_rgba(pupil_layer, high_size),
                interior_high=interior_high,
                motion_mask_low=motion_mask_low,
                hard_pupil_local=pupil_hard,
                eye_interior_local=interior,
            )
        )
    return assets


def translated_pupil_high(
    asset: PupilAsset,
    dx: float,
    oversampling: int,
) -> Image.Image:
    shift = dx * oversampling
    moved = asset.pupil_high.convert("RGBa").transform(
        asset.pupil_high.size,
        Image.Transform.AFFINE,
        (1.0, 0.0, -shift, 0.0, 1.0, 0.0),
        resample=Image.Resampling.BICUBIC,
        fillcolor=(0, 0, 0, 0),
    ).convert("RGBA")
    alpha = np.asarray(moved.getchannel("A"), dtype=np.float32)
    interior = np.asarray(asset.interior_high, dtype=np.float32) / 255.0
    moved.putalpha(
        Image.fromarray(np.round(alpha * interior).astype(np.uint8), mode="L")
    )
    return moved


def render_eye_patch(
    asset: PupilAsset,
    dx: float,
    oversampling: int,
) -> Image.Image:
    if abs(dx) < 1e-9:
        return asset.source_patch.copy()
    moved = translated_pupil_high(asset, dx, oversampling)
    composed = Image.alpha_composite(asset.clean_high, moved)
    low = resize_rgba(composed, asset.source_patch.size)
    result = asset.source_patch.copy()
    result.paste(low, (0, 0), asset.motion_mask_low)
    return result


def render_frame(
    source: Image.Image,
    assets: list[PupilAsset],
    dx: float,
    oversampling: int,
) -> Image.Image:
    if abs(dx) < 1e-9:
        return source.copy()
    frame = source.copy()
    for asset in assets:
        x0, y0, x1, y1 = asset.eye_bbox
        frame.paste(render_eye_patch(asset, dx, oversampling), (x0, y0))
    return frame


def render_frames(
    source: Image.Image,
    oversampling: int,
) -> tuple[list[Image.Image], list[float], list[PupilAsset]]:
    assets = build_pupil_assets(source, oversampling)
    offsets = [gaze_offset(index / FPS) for index in range(FRAME_COUNT)]
    frames = [
        render_frame(source, assets, offset, oversampling)
        for offset in offsets
    ]
    return frames, offsets, assets


def allowed_motion_mask(
    size: tuple[int, int],
    assets: list[PupilAsset],
) -> np.ndarray:
    allowed = np.zeros((size[1], size[0]), dtype=bool)
    for asset in assets:
        x0, y0, x1, y1 = asset.eye_bbox
        local = np.asarray(asset.motion_mask_low) > 0
        allowed[y0:y1, x0:x1] |= local
    return allowed


def pupil_and_highlight_centroids(
    frame: Image.Image,
) -> list[tuple[tuple[float, float], tuple[float, float], np.ndarray]]:
    rgba = np.array(frame.convert("RGBA"))
    results = []
    for eye_bbox, search_bbox in zip(EYE_BBOXES, PUPIL_SEARCH_BBOXES):
        ex0, ey0, ex1, ey1 = eye_bbox
        sx0, sy0, sx1, sy1 = search_bbox
        local = rgba[sy0:sy1, sx0:sx1]
        dark = (local[:, :, :3].max(axis=2) < 120) & (local[:, :, 3] > 12)
        dark_component = largest_component(dark)
        hull = ndimage.binary_fill_holes(dark_component)
        bright = (local[:, :, :3].min(axis=2) > 220) & hull
        highlight = largest_component(bright)

        py, px = ndimage.center_of_mass(dark_component)
        hy, hx = ndimage.center_of_mass(highlight)
        global_hull = np.zeros((ey1 - ey0, ex1 - ex0), dtype=bool)
        oy = sy0 - ey0
        ox = sx0 - ex0
        global_hull[oy : oy + hull.shape[0], ox : ox + hull.shape[1]] = hull
        results.append(
            (
                (float(sx0 + px), float(sy0 + py)),
                (float(sx0 + hx), float(sy0 + hy)),
                global_hull,
            )
        )
    return results


def validate_motion_tracking(
    frames: list[Image.Image],
    offsets: list[float],
    assets: list[PupilAsset],
) -> tuple[float, float, bool, bool, bool]:
    source_centroids = pupil_and_highlight_centroids(frames[0])
    measured: list[list[float]] = [[], []]
    highlight_errors: list[float] = []
    synchronous_errors: list[float] = []
    pupils_inside = True

    for frame, expected_dx in zip(frames, offsets):
        current = pupil_and_highlight_centroids(frame)
        displacements = []
        for index, ((pupil, highlight, hull), source_values, asset) in enumerate(
            zip(current, source_centroids, assets)
        ):
            source_pupil, source_highlight, _ = source_values
            pupil_dx = pupil[0] - source_pupil[0]
            highlight_dx = highlight[0] - source_highlight[0]
            measured[index].append(pupil_dx)
            displacements.append(pupil_dx)
            highlight_errors.append(abs(pupil_dx - highlight_dx))
            pupils_inside &= not np.any(hull & ~asset.eye_interior_local)
        synchronous_errors.append(abs(displacements[0] - displacements[1]))

    measured_array = np.asarray(measured)
    expected = np.asarray(offsets)[None, :]
    tracking_error = np.abs(measured_array - expected)
    adjacent_tracking_error = np.abs(
        np.diff(measured_array, axis=1) - np.diff(expected, axis=1)
    )
    return (
        float(tracking_error.max()),
        float(adjacent_tracking_error.max()),
        # The final raster centroid is threshold-sensitive at the antialiased
        # edge; pupil and highlight are nevertheless one transformed layer.
        max(highlight_errors) <= 0.55,
        max(synchronous_errors) <= 0.35,
        pupils_inside,
    )


def validate_rendered_frames(
    frames: list[Image.Image],
    offsets: list[float],
    assets: list[PupilAsset],
    source: Image.Image,
) -> tuple[bool, float, float, bool, bool, bool]:
    assert len(frames) == FRAME_COUNT
    assert len(offsets) == FRAME_COUNT
    source_array = np.array(source.convert("RGBA"))
    allowed = allowed_motion_mask(source.size, assets)
    outside = ~allowed

    outside_identical = all(
        np.array_equal(np.array(frame.convert("RGBA"))[outside], source_array[outside])
        for frame in frames
    )
    assert outside_identical
    assert np.array_equal(np.array(frames[0]), source_array)
    assert np.array_equal(np.array(frames[-1]), source_array)
    for index, offset in enumerate(offsets):
        if abs(offset) < 1e-9:
            assert np.array_equal(np.array(frames[index]), source_array)

    max_error, max_step_error, highlights_together, synchronous, inside = (
        validate_motion_tracking(frames, offsets, assets)
    )
    assert max_error <= 0.55, max_error
    assert max_step_error <= 0.40, max_step_error
    assert highlights_together
    assert synchronous
    assert inside

    non_integer_offsets = [
        value for value in offsets if abs(value - round(value)) > 1e-6
    ]
    subpixel_path = bool(non_integer_offsets)
    assert subpixel_path
    return outside_identical, max_error, max_step_error, highlights_together, synchronous, inside


def canonical_rgba(array: np.ndarray) -> np.ndarray:
    return core.canonical_rgba(array)


def validate_animation(
    output: Path,
    source: Image.Image,
    frames: list[Image.Image],
    assets: list[PupilAsset],
    rendered_validation: tuple[bool, float, float, bool, bool, bool],
) -> ValidationResult:
    source_array = canonical_rgba(np.array(source.convert("RGBA")))
    allowed = allowed_motion_mask(source.size, assets)
    outside = ~allowed
    with Image.open(output) as animation:
        assert animation.format == "WEBP"
        assert animation.size == source.size
        assert animation.is_animated
        assert animation.info.get("loop") == 0
        decoded: list[np.ndarray] = []
        durations: list[int] = []
        timestamps: list[int] = []
        timestamp = 0
        for index in range(animation.n_frames):
            animation.seek(index)
            animation.load()
            duration = int(animation.info.get("duration", 0))
            durations.append(duration)
            timestamps.append(timestamp)
            decoded.append(canonical_rgba(np.array(animation.convert("RGBA"))))
            timestamp += duration
        encoded_frames = animation.n_frames
        size = animation.size

    duration_ms = sum(durations)
    assert duration_ms == DURATION_MS
    assert all(duration % FRAME_DURATION_MS == 0 for duration in durations)
    first_last = np.array_equal(decoded[0], decoded[-1])
    outside_identical = all(
        np.array_equal(frame[outside], source_array[outside]) for frame in decoded
    )
    final_center = all(
        np.array_equal(frame, source_array)
        for timestamp, frame in zip(timestamps, decoded)
        if timestamp >= int(RIGHT_RETURN[1] * 1_000)
    )
    has_alpha = all(frame.shape[2] == 4 for frame in decoded)
    assert first_last
    assert outside_identical
    assert final_center
    assert has_alpha

    (
        _,
        max_error,
        max_step_error,
        highlights_together,
        synchronous,
        inside,
    ) = rendered_validation
    return ValidationResult(
        encoded_frames=encoded_frames,
        timeline_frames=len(frames),
        duration_ms=duration_ms,
        fps=FPS,
        loop=0,
        size=size,
        has_alpha=has_alpha,
        first_last_identical=first_last,
        final_center_identical=final_center,
        outside_pupils_identical=outside_identical,
        eye_whites_and_outlines_static=outside_identical,
        pupil_highlights_move_together=highlights_together,
        pupils_synchronous=synchronous,
        pupils_inside_eyes=inside,
        subpixel_path_used=True,
        max_tracking_error_px=max_error,
        max_adjacent_tracking_error_px=max_step_error,
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    core.add_common_cli(parser, "look")
    return parser.parse_args()


def main() -> None:
    global FPS, FRAME_DURATION_MS, FRAME_COUNT, OVERSAMPLING
    args = parse_args()
    started = time.perf_counter()
    config = core.config_for(args.mode)
    FPS = config.fps
    FRAME_DURATION_MS = config.frame_duration_ms
    FRAME_COUNT = FPS * DURATION_MS // 1_000
    OVERSAMPLING = config.oversampling
    source = core.load_master(args.source)
    frames, offsets, assets = render_frames(source, OVERSAMPLING)
    output = core.resolve_output(args)
    rendered_validation = None
    if config.full_validation:
        rendered_validation = validate_rendered_frames(frames, offsets, assets, source)
    core.save_webp(frames, output, config)
    basic = core.validate_webp(
        output, source.size, len(frames) * config.frame_duration_ms
    )
    if config.full_validation:
        result = validate_animation(output, source, frames, assets, rendered_validation)
        validation = (
            f", tracking error<={result.max_tracking_error_px:.3f}px, "
            f"step error<={result.max_adjacent_tracking_error_px:.3f}px"
        )
    else:
        validation = ""
    print(
        f"Created {output} in {time.perf_counter() - started:.3f}s: "
        f"mode={config.mode.value}, {len(frames)} timeline frames / "
        f"{basic.encoded_frames} encoded frames, {FPS} FPS, "
        f"max gaze={MAX_GAZE_PX:.1f}px, oversampling={OVERSAMPLING}x"
        f"{validation}, {output.stat().st_size} bytes"
    )


if __name__ == "__main__":
    main()
