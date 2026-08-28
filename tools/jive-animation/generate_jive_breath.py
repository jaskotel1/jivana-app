#!/usr/bin/env python3
"""Generate Jive Breath confined to Jive's inner torso texture.

The silhouette, bristles, face, limbs, feet, and shadow remain pixel-locked to
the master PNG. A safely inset torso region receives a visible belly lift and
internal expansion, rendered directly from the master for every frame.
"""

from __future__ import annotations

import argparse
import math
import time
from dataclasses import dataclass, replace
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

import jive_animation_core as core


PROTOTYPE_FPS = 12
PROTOTYPE_DURATION_MS = 3_000
PROTOTYPE_FRAME_COUNT = 36
PROTOTYPE_INHALE_END_FRAME = 14
FINAL_FPS = 25
FINAL_DURATION_MS = 5_000
FINAL_FRAME_COUNT = 125
FINAL_INHALE_END_FRAME = 50
BELLY_UPWARD_SHIFT_PX = 1.5
BELLY_EXPANSION_SHIFT_PX = 3.0
BELLY_EXPANSION_CENTER_Y = 405.0
BELLY_EXPANSION_SPREAD_PX = 42.0
BELLY_HORIZONTAL_EXPANSION_PX = 4.0
BELLY_HORIZONTAL_SPREAD_PX = 55.0
SAFETY_MARGIN_FROM_TORSO_MASK_PX = 22.0
VISIBLE_EDGE_LOCK_WIDTH_PX = 15.0
FALLOFF_RAMP_PX = 28.0
INTERNAL_MOTION_TOP = 315
INTERNAL_MOTION_BOTTOM = 475


@dataclass(frozen=True)
class InternalBreathAssets:
    source_rgba: np.ndarray
    influence: np.ndarray
    editable_mask: np.ndarray
    locked_mask: np.ndarray
    outer_edge_mask: np.ndarray
    face_mask: np.ndarray
    arm_region_mask: np.ndarray
    lower_region_mask: np.ndarray


@dataclass(frozen=True)
class PixelLockDiagnostics:
    changed_edge_pixels: int
    changed_face_pixels: int
    changed_arm_pixels: int
    changed_lower_pixels: int
    changed_outside_internal_pixels: int
    changed_alpha_pixels: int
    new_white_flash_pixels: int
    changed_internal_pixels_at_inhale: int


@dataclass(frozen=True)
class OversampledInternalAssets:
    scale: int
    bbox: tuple[int, int, int, int]
    source_high_rgba: np.ndarray
    source_high_rgb: np.ndarray
    source_roundtrip_rgb: np.ndarray
    influence_high: np.ndarray
    editable_high: np.ndarray


@dataclass(frozen=True)
class FinalValidationResult:
    file_size_bytes: int
    dimensions: tuple[int, int]
    duration_ms: int
    fps: int
    timeline_frames: int
    encoded_frames: int
    is_animated: bool
    loop: int
    has_alpha: bool
    transparent_background: bool
    has_anim_chunk: bool
    has_anmf_chunk: bool


def breath_strength(frame_number: int, frame_count: int, inhale_end: int) -> float:
    """One smooth inhale followed by a longer smooth exhale."""
    final_frame = frame_count - 1
    if frame_number <= inhale_end:
        progress = frame_number / inhale_end
        return 0.5 - 0.5 * math.cos(math.pi * progress)
    progress = (frame_number - inhale_end) / (final_frame - inhale_end)
    return 0.5 + 0.5 * math.cos(math.pi * progress)


def smoothstep(values: np.ndarray) -> np.ndarray:
    clipped = np.clip(values, 0.0, 1.0)
    return clipped * clipped * (3.0 - 2.0 * clipped)


def build_internal_breath_assets(source: Image.Image) -> InternalBreathAssets:
    source_rgba = np.asarray(source.convert("RGBA"))
    height, width = source_rgba.shape[:2]
    torso_mask = core.torso_motion_mask(source)
    torso_distance = ndimage.distance_transform_edt(torso_mask)

    yy, xx = np.indices((height, width))
    vertical_progress = (
        (yy.astype(np.float32) - INTERNAL_MOTION_TOP)
        / (INTERNAL_MOTION_BOTTOM - INTERNAL_MOTION_TOP)
    )
    vertical_weight = np.zeros((height, width), dtype=np.float32)
    vertical_active = (vertical_progress > 0.0) & (vertical_progress < 1.0)
    vertical_weight[vertical_active] = np.sin(
        np.pi * vertical_progress[vertical_active]
    ) ** 2

    edge_progress = (
        torso_distance - SAFETY_MARGIN_FROM_TORSO_MASK_PX
    ) / FALLOFF_RAMP_PX
    edge_weight = smoothstep(edge_progress.astype(np.float32))
    influence = edge_weight * vertical_weight

    editable_mask = (
        (torso_distance >= SAFETY_MARGIN_FROM_TORSO_MASK_PX)
        & (yy > INTERNAL_MOTION_TOP)
        & (yy < INTERNAL_MOTION_BOTTOM)
        & (source_rgba[:, :, 3] >= 250)
    )

    visible_character = source_rgba[:, :, 3] > 12
    visible_edge_distance = ndimage.distance_transform_edt(visible_character)
    outer_edge_mask = visible_character & (
        visible_edge_distance <= VISIBLE_EDGE_LOCK_WIDTH_PX
    )

    face_mask = core.bbox_mask((width, height), core.FACE_BBOX)
    arm_region_mask = ndimage.binary_dilation(
        core.static_arm_mask(source),
        iterations=4,
    )
    lower_region_mask = yy >= core.BODY_ACTIVE_BOTTOM

    protected_masks = (
        outer_edge_mask,
        face_mask,
        arm_region_mask,
        lower_region_mask,
    )
    protected_union = np.logical_or.reduce(protected_masks)
    arm_clearance = ndimage.distance_transform_edt(~arm_region_mask)
    influence *= smoothstep((arm_clearance / 12.0).astype(np.float32))
    editable_mask &= ~protected_union
    influence[~editable_mask] = 0.0
    locked_mask = ~editable_mask
    for protected in protected_masks:
        if np.any(editable_mask & protected):
            raise AssertionError("Internal Breath mask intersects a locked region")

    return InternalBreathAssets(
        source_rgba=source_rgba,
        influence=influence,
        editable_mask=editable_mask,
        locked_mask=locked_mask,
        outer_edge_mask=outer_edge_mask,
        face_mask=face_mask,
        arm_region_mask=arm_region_mask,
        lower_region_mask=lower_region_mask,
    )


def internal_sampling_coordinates(
    yy: np.ndarray,
    xx: np.ndarray,
    influence: np.ndarray,
    strength: float,
) -> tuple[np.ndarray, np.ndarray]:
    expansion_direction = -np.tanh(
        (yy - BELLY_EXPANSION_CENTER_Y) / BELLY_EXPANSION_SPREAD_PX
    )
    vertical_displacement = strength * influence * (
        BELLY_UPWARD_SHIFT_PX
        + BELLY_EXPANSION_SHIFT_PX * expansion_direction
    )
    horizontal_direction = np.tanh(
        (xx - core.BODY_CENTER_X) / BELLY_HORIZONTAL_SPREAD_PX
    )
    horizontal_displacement = (
        BELLY_HORIZONTAL_EXPANSION_PX
        * strength
        * influence
        * horizontal_direction
    )
    return yy + vertical_displacement, xx - horizontal_displacement


def prepare_oversampled_assets(
    source: Image.Image,
    assets: InternalBreathAssets,
    scale: int,
) -> OversampledInternalAssets:
    if scale <= 1:
        raise ValueError("Oversampled assets require scale > 1")
    ys, xs = np.where(assets.editable_mask)
    padding = int(
        math.ceil(
            max(
                BELLY_UPWARD_SHIFT_PX + BELLY_EXPANSION_SHIFT_PX,
                BELLY_HORIZONTAL_EXPANSION_PX,
            )
        )
    ) + 4
    x0 = max(0, int(xs.min()) - padding)
    y0 = max(0, int(ys.min()) - padding)
    x1 = min(source.width, int(xs.max()) + padding + 1)
    y1 = min(source.height, int(ys.max()) + padding + 1)
    bbox = (x0, y0, x1, y1)
    crop_size = (x1 - x0, y1 - y0)
    high_size = (crop_size[0] * scale, crop_size[1] * scale)

    source_high = core.resize_rgba(
        source.crop(bbox),
        high_size,
        Image.Resampling.LANCZOS,
    )
    influence_crop = assets.influence[y0:y1, x0:x1].astype(np.float32)
    influence_high = np.asarray(
        Image.fromarray(influence_crop, "F").resize(
            high_size,
            Image.Resampling.BICUBIC,
        ),
        dtype=np.float32,
    ).copy()
    editable_crop = assets.editable_mask[y0:y1, x0:x1]
    editable_high = np.asarray(
        Image.fromarray(editable_crop.astype(np.uint8) * 255, "L").resize(
            high_size,
            Image.Resampling.NEAREST,
        )
    ) > 0
    influence_high[~editable_high] = 0.0
    source_high_rgba = np.asarray(source_high.convert("RGBA"))
    source_roundtrip_rgb = np.asarray(
        core.resize_rgba(
            source_high,
            crop_size,
            Image.Resampling.LANCZOS,
        ).convert("RGBA")
    )[:, :, :3].astype(np.int16)
    return OversampledInternalAssets(
        scale=scale,
        bbox=bbox,
        source_high_rgba=source_high_rgba,
        source_high_rgb=source_high_rgba[:, :, :3].astype(np.float32),
        source_roundtrip_rgb=source_roundtrip_rgb,
        influence_high=influence_high,
        editable_high=editable_high,
    )


def render_frame(
    source: Image.Image,
    assets: InternalBreathAssets,
    strength: float,
) -> Image.Image:
    """Sample the inner texture from the master; never reuse another frame."""
    if strength < 1e-12:
        return source.copy()

    source_rgba = assets.source_rgba
    height, width = source_rgba.shape[:2]
    yy, xx = np.indices((height, width), dtype=np.float32)
    sample_y, sample_x = internal_sampling_coordinates(
        yy,
        xx,
        assets.influence,
        strength,
    )

    frame = source_rgba.copy()
    source_float = source_rgba[:, :, :3].astype(np.float32)
    for channel in range(3):
        sampled = ndimage.map_coordinates(
            source_float[:, :, channel],
            (sample_y, sample_x),
            order=1,
            mode="nearest",
            prefilter=False,
        )
        frame[:, :, channel][assets.editable_mask] = np.clip(
            np.rint(sampled[assets.editable_mask]),
            0,
            255,
        ).astype(np.uint8)

    # Alpha is never sampled or blended. The silhouette therefore cannot move.
    frame[assets.locked_mask] = source_rgba[assets.locked_mask]
    return Image.fromarray(frame, "RGBA")


def render_frame_oversampled(
    source: Image.Image,
    assets: InternalBreathAssets,
    high: OversampledInternalAssets,
    strength: float,
) -> Image.Image:
    """Render only the accepted inner motion at high resolution."""
    if strength < 1e-12:
        return source.copy()

    height_high, width_high = high.source_high_rgba.shape[:2]
    yy_high, xx_high = np.indices((height_high, width_high), dtype=np.float32)
    x0, y0, x1, y1 = high.bbox
    yy_global = y0 + (yy_high + 0.5) / high.scale - 0.5
    xx_global = x0 + (xx_high + 0.5) / high.scale - 0.5
    sample_y_global, sample_x_global = internal_sampling_coordinates(
        yy_global,
        xx_global,
        high.influence_high,
        strength,
    )
    sample_y_high = (sample_y_global - y0 + 0.5) * high.scale - 0.5
    sample_x_high = (sample_x_global - x0 + 0.5) * high.scale - 0.5

    rendered_high = high.source_high_rgba.copy()
    for channel in range(3):
        sampled = ndimage.map_coordinates(
            high.source_high_rgb[:, :, channel],
            (sample_y_high, sample_x_high),
            order=1,
            mode="nearest",
            prefilter=False,
        )
        rendered_high[:, :, channel][high.editable_high] = np.clip(
            np.rint(sampled[high.editable_high]),
            0,
            255,
        ).astype(np.uint8)

    crop_size = (x1 - x0, y1 - y0)
    downsampled = core.resize_rgba(
        Image.fromarray(rendered_high, "RGBA"),
        crop_size,
        Image.Resampling.LANCZOS,
    )
    downsampled_rgba = np.asarray(downsampled.convert("RGBA"))
    frame = assets.source_rgba.copy()
    editable_crop = assets.editable_mask[y0:y1, x0:x1]
    frame_crop = frame[y0:y1, x0:x1]
    # Apply only the deformation delta produced at high resolution. Replacing
    # the crop directly would also apply the 8x resize/downsample round-trip to
    # every editable pixel, creating a differently colored belly patch and a
    # one-frame flash when Breath leaves the neutral pose.
    deformation_delta = (
        downsampled_rgba[:, :, :3].astype(np.int16)
        - high.source_roundtrip_rgb
    )
    corrected_rgb = np.clip(
        frame_crop[:, :, :3].astype(np.int16) + deformation_delta,
        0,
        255,
    ).astype(np.uint8)
    for channel in range(3):
        frame_crop[:, :, channel][editable_crop] = corrected_rgb[
            :, :, channel
        ][editable_crop]
    frame[assets.locked_mask] = assets.source_rgba[assets.locked_mask]
    return Image.fromarray(frame, "RGBA")


def render_frames(
    source: Image.Image,
    frame_count: int,
    inhale_end: int,
    oversampling: int = 1,
) -> tuple[list[Image.Image], list[float], InternalBreathAssets]:
    assets = build_internal_breath_assets(source)
    high = (
        prepare_oversampled_assets(source, assets, oversampling)
        if oversampling > 1
        else None
    )
    strengths = [
        breath_strength(index, frame_count, inhale_end)
        for index in range(frame_count)
    ]
    if high is None:
        frames = [render_frame(source, assets, strength) for strength in strengths]
    else:
        frames = [
            render_frame_oversampled(source, assets, high, strength)
            for strength in strengths
        ]
    return frames, strengths, assets


def prototype_durations() -> list[int]:
    """Represent exactly 3 seconds at an average cadence of 12 FPS."""
    return [83, 83, 84] * (PROTOTYPE_FRAME_COUNT // 3)


def final_durations() -> list[int]:
    return [40] * FINAL_FRAME_COUNT


def changed_pixel_mask(frame: Image.Image, reference: np.ndarray) -> np.ndarray:
    return np.any(np.asarray(frame.convert("RGBA")) != reference, axis=2)


def pixel_lock_diagnostics(
    frames: list[Image.Image],
    strengths: list[float],
    assets: InternalBreathAssets,
) -> PixelLockDiagnostics:
    reference = assets.source_rgba
    max_edge = 0
    max_face = 0
    max_arms = 0
    max_lower = 0
    max_outside = 0
    max_alpha = 0
    max_white_flash = 0
    for frame in frames:
        rendered = np.asarray(frame.convert("RGBA"))
        changed = np.any(rendered != reference, axis=2)
        max_edge = max(max_edge, int(np.count_nonzero(changed & assets.outer_edge_mask)))
        max_face = max(max_face, int(np.count_nonzero(changed & assets.face_mask)))
        max_arms = max(max_arms, int(np.count_nonzero(changed & assets.arm_region_mask)))
        max_lower = max(max_lower, int(np.count_nonzero(changed & assets.lower_region_mask)))
        max_outside = max(
            max_outside,
            int(np.count_nonzero(changed & assets.locked_mask)),
        )
        max_alpha = max(
            max_alpha,
            int(np.count_nonzero(rendered[:, :, 3] != reference[:, :, 3])),
        )
        rendered_white = np.all(rendered[:, :, :3] >= 245, axis=2)
        reference_white = np.all(reference[:, :, :3] >= 245, axis=2)
        max_white_flash = max(
            max_white_flash,
            int(np.count_nonzero(changed & rendered_white & ~reference_white)),
        )

    inhale_index = int(np.argmax(strengths))
    inhale_changed = changed_pixel_mask(frames[inhale_index], reference)
    changed_internal = int(
        np.count_nonzero(inhale_changed & assets.editable_mask)
    )
    result = PixelLockDiagnostics(
        changed_edge_pixels=max_edge,
        changed_face_pixels=max_face,
        changed_arm_pixels=max_arms,
        changed_lower_pixels=max_lower,
        changed_outside_internal_pixels=max_outside,
        changed_alpha_pixels=max_alpha,
        new_white_flash_pixels=max_white_flash,
        changed_internal_pixels_at_inhale=changed_internal,
    )
    locked_counts = (
        result.changed_edge_pixels,
        result.changed_face_pixels,
        result.changed_arm_pixels,
        result.changed_lower_pixels,
        result.changed_outside_internal_pixels,
        result.changed_alpha_pixels,
        result.new_white_flash_pixels,
    )
    if any(locked_counts):
        raise AssertionError(f"Pixel lock failed: {result}")
    if result.changed_internal_pixels_at_inhale == 0:
        raise AssertionError("Internal Breath produced no visible pixel changes")
    return result


def quick_prototype_checks(
    frames: list[Image.Image],
    source: Image.Image,
    strengths: list[float],
    assets: InternalBreathAssets,
) -> PixelLockDiagnostics:
    """Cheap in-memory guards only; no WebP container or production checks."""
    reference = assets.source_rgba
    if len(frames) != PROTOTYPE_FRAME_COUNT:
        raise AssertionError("Unexpected prototype frame count")
    if not np.array_equal(np.asarray(frames[0]), reference):
        raise AssertionError("Prototype must start from neutral source")
    if not np.array_equal(np.asarray(frames[-1]), reference):
        raise AssertionError("Prototype must end at neutral source")
    return pixel_lock_diagnostics(frames, strengths, assets)


def motion_energy(
    frame_rgba: np.ndarray,
    reference: np.ndarray,
    editable_mask: np.ndarray,
) -> float:
    difference = np.abs(
        frame_rgba[:, :, :3].astype(np.float32)
        - reference[:, :, :3].astype(np.float32)
    )
    return float(difference[editable_mask].mean())


def validate_motion_curve(
    frames: list[Image.Image],
    strengths: list[float],
    assets: InternalBreathAssets,
) -> None:
    energies = [
        motion_energy(
            np.asarray(frame.convert("RGBA")),
            assets.source_rgba,
            assets.editable_mask,
        )
        for frame in frames
    ]
    peak = int(np.argmax(strengths))
    tolerance = 0.03
    if any(
        current + tolerance < previous
        for previous, current in zip(energies[:peak], energies[1 : peak + 1])
    ):
        raise AssertionError("Internal motion jitters during inhale")
    if any(
        current > previous + tolerance
        for previous, current in zip(energies[peak:], energies[peak + 1 :])
    ):
        raise AssertionError("Internal motion jitters during exhale")


def validate_final_webp(
    output: Path,
    source: Image.Image,
    assets: InternalBreathAssets,
) -> FinalValidationResult:
    container = output.read_bytes()
    has_anim_chunk = b"ANIM" in container
    has_anmf_chunk = b"ANMF" in container
    if not has_anim_chunk or not has_anmf_chunk:
        raise AssertionError("Animated WebP chunks are missing")

    reference = assets.source_rgba
    first_frame: np.ndarray | None = None
    last_frame: np.ndarray | None = None
    durations: list[int] = []
    energies: list[float] = []
    has_alpha = True
    transparent_background = True
    max_edge = 0
    max_face = 0
    max_arms = 0
    max_lower = 0
    max_outside = 0
    max_alpha = 0
    max_white_flash = 0

    with Image.open(output) as animation:
        is_animated = bool(animation.is_animated)
        encoded_frames = int(animation.n_frames)
        dimensions = animation.size
        loop = int(animation.info.get("loop", -1))
        if animation.format != "WEBP" or not is_animated:
            raise AssertionError("Final output is not an animated WebP")
        if encoded_frames <= 1 or encoded_frames > FINAL_FRAME_COUNT:
            raise AssertionError((encoded_frames, FINAL_FRAME_COUNT))
        if dimensions != core.JIVE_SIZE:
            raise AssertionError((dimensions, core.JIVE_SIZE))
        if loop != 0:
            raise AssertionError("Final WebP loop is not infinite")

        for index in range(encoded_frames):
            animation.seek(index)
            animation.load()
            durations.append(int(animation.info.get("duration", 0)))
            decoded_image = animation.convert("RGBA")
            decoded = np.asarray(decoded_image)
            has_alpha &= "A" in decoded_image.getbands()
            transparent_background &= bool(
                decoded[0, 0, 3] == 0 and np.any(decoded[:, :, 3] < 255)
            )
            changed = np.any(decoded != reference, axis=2)
            max_edge = max(
                max_edge,
                int(np.count_nonzero(changed & assets.outer_edge_mask)),
            )
            max_face = max(
                max_face,
                int(np.count_nonzero(changed & assets.face_mask)),
            )
            max_arms = max(
                max_arms,
                int(np.count_nonzero(changed & assets.arm_region_mask)),
            )
            max_lower = max(
                max_lower,
                int(np.count_nonzero(changed & assets.lower_region_mask)),
            )
            max_outside = max(
                max_outside,
                int(np.count_nonzero(changed & assets.locked_mask)),
            )
            max_alpha = max(
                max_alpha,
                int(np.count_nonzero(decoded[:, :, 3] != reference[:, :, 3])),
            )
            decoded_white = np.all(decoded[:, :, :3] >= 245, axis=2)
            reference_white = np.all(reference[:, :, :3] >= 245, axis=2)
            max_white_flash = max(
                max_white_flash,
                int(np.count_nonzero(changed & decoded_white & ~reference_white)),
            )
            energies.append(
                motion_energy(decoded, reference, assets.editable_mask)
            )
            if index == 0:
                first_frame = decoded.copy()
            if index == encoded_frames - 1:
                last_frame = decoded.copy()

    duration_ms = sum(durations)
    if duration_ms != FINAL_DURATION_MS:
        raise AssertionError((duration_ms, FINAL_DURATION_MS))
    if any(duration <= 0 or duration % 40 != 0 for duration in durations):
        raise AssertionError("Final WebP timing is not aligned to 25 FPS")
    if not has_alpha or not transparent_background:
        raise AssertionError("Final WebP lost transparency")
    if any(
        (
            max_edge,
            max_face,
            max_arms,
            max_lower,
            max_outside,
            max_alpha,
            max_white_flash,
        )
    ):
        raise AssertionError(
            "Round-trip pixel lock failed: "
            f"edge={max_edge}, face={max_face}, arms={max_arms}, "
            f"lower={max_lower}, outside={max_outside}, alpha={max_alpha}, "
            f"white_flash={max_white_flash}"
        )
    if first_frame is None or last_frame is None:
        raise AssertionError("Final WebP contains no decoded frames")
    if not np.array_equal(first_frame, reference):
        raise AssertionError("Final WebP does not start neutral")
    if not np.array_equal(last_frame, reference):
        raise AssertionError("Final WebP does not end neutral")
    if not np.array_equal(first_frame, last_frame):
        raise AssertionError("Final WebP loop endpoints differ")

    timeline_energies: list[float] = []
    for energy, duration in zip(energies, durations):
        timeline_energies.extend([energy] * (duration // 40))
    if len(timeline_energies) != FINAL_FRAME_COUNT:
        raise AssertionError((len(timeline_energies), FINAL_FRAME_COUNT))
    peak = FINAL_INHALE_END_FRAME
    tolerance = 0.03
    if any(
        current + tolerance < previous
        for previous, current in zip(
            timeline_energies[:peak],
            timeline_energies[1 : peak + 1],
        )
    ):
        raise AssertionError("Decoded final jitters during inhale")
    if any(
        current > previous + tolerance
        for previous, current in zip(
            timeline_energies[peak:],
            timeline_energies[peak + 1 :],
        )
    ):
        raise AssertionError("Decoded final jitters during exhale")

    return FinalValidationResult(
        file_size_bytes=output.stat().st_size,
        dimensions=dimensions,
        duration_ms=duration_ms,
        fps=FINAL_FPS,
        timeline_frames=FINAL_FRAME_COUNT,
        encoded_frames=encoded_frames,
        is_animated=is_animated,
        loop=loop,
        has_alpha=has_alpha,
        transparent_background=transparent_background,
        has_anim_chunk=has_anim_chunk,
        has_anmf_chunk=has_anmf_chunk,
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    core.add_common_cli(parser, "breath")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    started = time.perf_counter()
    config = core.config_for(args.mode)
    source = core.load_master(args.source)

    if config.mode is core.RenderMode.PROTOTYPE:
        frame_count = PROTOTYPE_FRAME_COUNT
        inhale_end = PROTOTYPE_INHALE_END_FRAME
        duration_ms = PROTOTYPE_DURATION_MS
        render_oversampling = 1
        durations = prototype_durations()
        if args.output is None:
            args.output = core.DEFAULT_OUTPUT / "jive_breath_internal_test.webp"
    else:
        frame_count = FINAL_FRAME_COUNT
        inhale_end = FINAL_INHALE_END_FRAME
        duration_ms = FINAL_DURATION_MS
        render_oversampling = config.oversampling
        durations = final_durations()

    frames, strengths, assets = render_frames(
        source,
        frame_count,
        inhale_end,
        oversampling=render_oversampling,
    )
    if len(frames) != frame_count:
        raise AssertionError((len(frames), frame_count))
    if not np.array_equal(np.asarray(frames[0]), assets.source_rgba):
        raise AssertionError("Breath must start from the master asset")
    if not np.array_equal(np.asarray(frames[-1]), assets.source_rgba):
        raise AssertionError("Breath must end at the master asset")

    diagnostics = pixel_lock_diagnostics(frames, strengths, assets)
    validate_motion_curve(frames, strengths, assets)
    output: Path = core.resolve_output(args)

    if config.mode is core.RenderMode.PROTOTYPE:
        preview_config = replace(
            config,
            fps=PROTOTYPE_FPS,
            oversampling=min(config.oversampling, 2),
            full_validation=False,
            lossless=True,
            quality=100,
            method=0,
        )
        core.save_webp(
            frames,
            output,
            preview_config,
            durations=durations,
        )
        print(
            f"Created {output} in {time.perf_counter() - started:.3f}s: "
            f"prototype, {len(frames)} frames, {PROTOTYPE_FPS} FPS, "
            f"{duration_ms} ms, oversampling={preview_config.oversampling}x"
        )
        print(
            "Pixel lock: "
            f"edge={diagnostics.changed_edge_pixels}, "
            f"face={diagnostics.changed_face_pixels}, "
            f"arms={diagnostics.changed_arm_pixels}, "
            f"lower={diagnostics.changed_lower_pixels}, "
            f"outside_internal={diagnostics.changed_outside_internal_pixels}, "
            f"alpha={diagnostics.changed_alpha_pixels}, "
            f"white_flash={diagnostics.new_white_flash_pixels}"
        )
        return

    core.save_webp(
        frames,
        output,
        config,
        durations=durations,
    )
    validation = validate_final_webp(output, source, assets)
    print(
        f"Created {output} in {time.perf_counter() - started:.3f}s: "
        f"FINAL, size={validation.file_size_bytes}, "
        f"dimensions={validation.dimensions}, duration={validation.duration_ms}ms, "
        f"fps={validation.fps}, timeline_frames={validation.timeline_frames}, "
        f"encoded_frames={validation.encoded_frames}, "
        f"animated={validation.is_animated}, loop={validation.loop}, "
        f"oversampling={config.oversampling}x, alpha={validation.has_alpha}, "
        f"transparent={validation.transparent_background}, "
        f"ANIM={validation.has_anim_chunk}, ANMF={validation.has_anmf_chunk}, "
        "validation=PASS"
    )


if __name__ == "__main__":
    main()
