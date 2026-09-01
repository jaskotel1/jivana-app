#!/usr/bin/env python3
"""Compose the accepted Jive Breath, Blink v5, and Look v1 modules."""

from __future__ import annotations

import argparse
import math
import shutil
import time
from dataclasses import dataclass, replace
from pathlib import Path

import numpy as np
from PIL import Image

import generate_jive_blink as blink
import generate_jive_breath as breath
import generate_jive_look as look
import jive_animation_core as core


PROTOTYPE_DURATION_MS = 6_000
FINAL_DURATION_MS = 6_000
PROTOTYPE_BLINK_CLOSED_SECONDS = (29 / 12, 59 / 12)
FINAL_BREATH_FRAME_COUNT = 75
FINAL_BREATH_INHALE_END_SECONDS = breath.PROTOTYPE_INHALE_END_FRAME / 12
FINAL_BREATH_NEUTRAL_SECONDS = (breath.PROTOTYPE_FRAME_COUNT - 1) / 12
SELECTED_FINAL_VARIANT = "lossless"


@dataclass(frozen=True)
class IdleRender:
    frames: list[Image.Image]
    breath_frames: list[Image.Image]
    look_offsets: list[float]
    blink_poses: list[blink.EyePose]
    breath_assets: breath.InternalBreathAssets
    duration_ms: int
    durations: list[int]
    blink_closed_seconds: tuple[float, ...]


@dataclass(frozen=True)
class SmokeResult:
    encoded_frames: int
    duration_ms: int
    size: tuple[int, int]
    loop: int
    has_alpha: bool
    first_last_identical: bool
    module_conflicts: int
    changed_hair_pixels: int
    changed_arm_pixels: int
    changed_lower_pixels: int
    changed_edge_pixels: int
    new_white_flash_pixels: int


@dataclass(frozen=True)
class VariantMetrics:
    name: str
    path: Path
    file_size_bytes: int
    encoded_frames: int
    psnr_db: float
    visible_rgb_mae: float
    edge_rgb_mae: float
    eyes_rgb_mae: float
    hair_rgb_mae: float
    arms_rgb_mae: float
    legs_rgb_mae: float
    temporal_delta_mae: float
    alpha_changed_pixels: int
    white_flash_pixels: int
    first_last_identical: bool
    loop_rgb_mae: float


def exact_prototype_durations(frame_count: int) -> list[int]:
    if frame_count % 3:
        raise ValueError("The 12 FPS prototype timeline must use full seconds")
    return [83, 83, 84] * (frame_count // 3)


def blink_timeline(
    frame_count: int,
    fps: int,
    closed_seconds: tuple[float, ...],
) -> list[blink.EyePose]:
    sequence = (
        blink.EyePose.LID_50,
        blink.EyePose.LID_94,
        blink.EyePose.CLOSED,
        blink.EyePose.LID_94,
        blink.EyePose.LID_50,
    )
    poses = [blink.EyePose.OPEN] * frame_count
    for closed_time in closed_seconds:
        closed_frame = round(closed_time * fps)
        for offset, pose in enumerate(sequence, start=-2):
            index = closed_frame + offset
            if not 0 <= index < frame_count:
                raise ValueError("Blink would overlap the loop boundary")
            poses[index] = pose
    return poses


def look_offset_for_idle(time_seconds: float) -> float:
    """Use only Look v1's accepted CENTER -> LEFT -> CENTER segment."""
    if time_seconds < look.RIGHT_MOVE[0]:
        return look.gaze_offset(time_seconds)
    return 0.0


def render_final_breath_cycle(
    source: Image.Image,
    oversampling: int,
) -> tuple[list[Image.Image], breath.InternalBreathAssets]:
    """Sample the accepted 3-second prototype Breath at production cadence."""
    assets = breath.build_internal_breath_assets(source)
    high = breath.prepare_oversampled_assets(source, assets, oversampling)
    frames: list[Image.Image] = []
    for index in range(FINAL_BREATH_FRAME_COUNT):
        time_seconds = index / 25
        if time_seconds >= FINAL_BREATH_NEUTRAL_SECONDS:
            strength = 0.0
        else:
            virtual_frame = time_seconds * 12
            strength = breath.breath_strength(
                virtual_frame,
                breath.PROTOTYPE_FRAME_COUNT,
                breath.PROTOTYPE_INHALE_END_FRAME,
            )
        frames.append(
            breath.render_frame_oversampled(source, assets, high, strength)
        )
    return frames, assets


def resampled_prototype_blink_timeline(
    frame_count: int,
    fps: int,
) -> list[blink.EyePose]:
    """Preserve the accepted 12 FPS Blink timing on a denser timeline."""
    prototype = blink_timeline(
        PROTOTYPE_DURATION_MS * 12 // 1_000,
        12,
        PROTOTYPE_BLINK_CLOSED_SECONDS,
    )
    return [prototype[min(int(index * 12 / fps), len(prototype) - 1)] for index in range(frame_count)]


def render_idle(source: Image.Image, config: core.RenderConfig) -> IdleRender:
    if config.mode is core.RenderMode.PROTOTYPE:
        duration_ms = PROTOTYPE_DURATION_MS
        breath_count = breath.PROTOTYPE_FRAME_COUNT
        inhale_end = breath.PROTOTYPE_INHALE_END_FRAME
        breath_oversampling = 1
        blink_closed_seconds = PROTOTYPE_BLINK_CLOSED_SECONDS
        breath_cycle, _, breath_assets = breath.render_frames(
            source,
            breath_count,
            inhale_end,
            oversampling=breath_oversampling,
        )
    else:
        duration_ms = FINAL_DURATION_MS
        breath_count = FINAL_BREATH_FRAME_COUNT
        blink_closed_seconds = PROTOTYPE_BLINK_CLOSED_SECONDS
        breath_cycle, breath_assets = render_final_breath_cycle(
            source,
            config.oversampling,
        )

    frame_count = config.fps * duration_ms // 1_000
    if frame_count % breath_count:
        raise ValueError("Idle must contain complete accepted Breath cycles")

    breath_frames = [
        breath_cycle[index % breath_count].copy()
        for index in range(frame_count)
    ]

    look_assets = look.build_pupil_assets(source, config.oversampling)
    blink_assets = blink.build_assets(source, config.oversampling)
    look_offsets = [
        look_offset_for_idle(index / config.fps)
        for index in range(frame_count)
    ]
    blink_poses = (
        blink_timeline(frame_count, config.fps, blink_closed_seconds)
        if config.mode is core.RenderMode.PROTOTYPE
        else resampled_prototype_blink_timeline(frame_count, config.fps)
    )

    frames: list[Image.Image] = []
    for base, gaze, eye_pose in zip(
        breath_frames,
        look_offsets,
        blink_poses,
    ):
        # Ownership order: torso Breath, pupil-only Look, eyelid-only Blink.
        frame = look.render_frame(
            base,
            look_assets,
            gaze,
            config.oversampling,
        )
        frame = blink.render_pose(frame, blink_assets, eye_pose)
        frames.append(frame)

    if any(
        abs(gaze) > 1e-9 and pose is not blink.EyePose.OPEN
        for gaze, pose in zip(look_offsets, blink_poses)
    ):
        raise AssertionError("Look and Blink timelines unexpectedly overlap")

    durations = (
        exact_prototype_durations(frame_count)
        if config.mode is core.RenderMode.PROTOTYPE
        else [config.frame_duration_ms] * frame_count
    )
    return IdleRender(
        frames=frames,
        breath_frames=breath_frames,
        look_offsets=look_offsets,
        blink_poses=blink_poses,
        breath_assets=breath_assets,
        duration_ms=duration_ms,
        durations=durations,
        blink_closed_seconds=blink_closed_seconds,
    )


def count_changed_pixels(
    frames: list[Image.Image],
    reference: np.ndarray,
    mask: np.ndarray,
) -> int:
    maximum = 0
    for frame in frames:
        changed = np.any(np.asarray(frame.convert("RGBA")) != reference, axis=2)
        maximum = max(maximum, int(np.count_nonzero(changed & mask)))
    return maximum


def validate_composition(
    source: Image.Image,
    render: IdleRender,
) -> tuple[int, int, int, int, int, int]:
    reference = np.asarray(source.convert("RGBA"))
    eye_regions = np.logical_or.reduce(
        [core.bbox_mask(source.size, bbox) for bbox in core.EYE_BBOXES]
    )
    outside_eyes = ~eye_regions
    module_conflicts = 0
    for frame, breath_frame in zip(render.frames, render.breath_frames):
        current = np.asarray(frame.convert("RGBA"))
        expected = np.asarray(breath_frame.convert("RGBA"))
        module_conflicts = max(
            module_conflicts,
            int(np.count_nonzero(np.any(current != expected, axis=2) & outside_eyes)),
        )

    hair = core.bbox_mask(source.size, core.HAIR_BBOX)
    arms = render.breath_assets.arm_region_mask
    lower = render.breath_assets.lower_region_mask
    edge = render.breath_assets.outer_edge_mask
    changed_hair = count_changed_pixels(render.frames, reference, hair)
    changed_arms = count_changed_pixels(render.frames, reference, arms)
    changed_lower = count_changed_pixels(render.frames, reference, lower)
    changed_edge = count_changed_pixels(render.frames, reference, edge)

    source_white = np.all(reference[:, :, :3] >= 250, axis=2)
    new_white = 0
    for frame in render.frames:
        rgba = np.asarray(frame.convert("RGBA"))
        frame_white = np.all(rgba[:, :, :3] >= 250, axis=2) & (rgba[:, :, 3] > 0)
        # Look legitimately reveals white pixels that were covered by a pupil.
        # A white-flash diagnostic is meaningful only outside the owned eyes.
        new_white = max(
            new_white,
            int(np.count_nonzero(frame_white & ~source_white & outside_eyes)),
        )

    if any((module_conflicts, changed_hair, changed_arms, changed_lower, changed_edge, new_white)):
        raise AssertionError(
            "Idle composition changed a protected region: "
            f"conflicts={module_conflicts}, hair={changed_hair}, arms={changed_arms}, "
            f"lower={changed_lower}, edge={changed_edge}, white={new_white}"
        )
    if not np.array_equal(np.asarray(render.frames[0]), reference):
        raise AssertionError("Idle must begin in the neutral master pose")
    if not np.array_equal(np.asarray(render.frames[-1]), reference):
        raise AssertionError("Idle must end in the neutral master pose")
    return module_conflicts, changed_hair, changed_arms, changed_lower, changed_edge, new_white


def smoke_test(
    output: Path,
    source: Image.Image,
    render: IdleRender,
) -> SmokeResult:
    protected = validate_composition(source, render)
    basic = core.validate_webp(output, source.size, render.duration_ms)
    with Image.open(output) as animation:
        animation.seek(0)
        first = core.canonical_rgba(np.asarray(animation.convert("RGBA")))
        animation.seek(animation.n_frames - 1)
        last = core.canonical_rgba(np.asarray(animation.convert("RGBA")))
    first_last = np.array_equal(first, last)
    if not first_last:
        raise AssertionError("Decoded WebP does not close seamlessly")
    return SmokeResult(
        encoded_frames=basic.encoded_frames,
        duration_ms=basic.duration_ms,
        size=basic.size,
        loop=basic.loop,
        has_alpha=basic.has_alpha,
        first_last_identical=first_last,
        module_conflicts=protected[0],
        changed_hair_pixels=protected[1],
        changed_arm_pixels=protected[2],
        changed_lower_pixels=protected[3],
        changed_edge_pixels=protected[4],
        new_white_flash_pixels=protected[5],
    )


def normalize_transparent_pixels(frames: list[Image.Image]) -> list[Image.Image]:
    """Set RGB to zero only where alpha is exactly zero."""
    return [
        Image.fromarray(
            core.canonical_rgba(np.asarray(frame.convert("RGBA"))),
            "RGBA",
        )
        for frame in frames
    ]


def decode_timeline(
    path: Path,
    frame_duration_ms: int,
    expected_frames: int,
) -> list[np.ndarray]:
    timeline: list[np.ndarray] = []
    with Image.open(path) as animation:
        for index in range(animation.n_frames):
            animation.seek(index)
            animation.load()
            duration = int(animation.info.get("duration", 0))
            if duration <= 0 or duration % frame_duration_ms:
                raise AssertionError(f"Invalid frame duration in {path}: {duration}")
            rgba = core.canonical_rgba(np.asarray(animation.convert("RGBA")))
            timeline.extend([rgba] * (duration // frame_duration_ms))
    if len(timeline) != expected_frames:
        raise AssertionError((path, len(timeline), expected_frames))
    return timeline


def region_mae(
    reference: list[np.ndarray],
    candidate: list[np.ndarray],
    mask: np.ndarray,
) -> float:
    total = 0
    count = 0
    for expected, actual in zip(reference, candidate):
        difference = np.abs(
            actual[:, :, :3].astype(np.int16)
            - expected[:, :, :3].astype(np.int16)
        )
        total += int(difference[mask].sum())
        count += int(np.count_nonzero(mask)) * 3
    return total / max(1, count)


def compare_variant(
    name: str,
    path: Path,
    reference: list[np.ndarray],
    source: Image.Image,
    duration_ms: int,
    breath_assets: breath.InternalBreathAssets,
    frame_duration_ms: int,
) -> VariantMetrics:
    basic = core.validate_webp(path, source.size, duration_ms)
    candidate = decode_timeline(path, frame_duration_ms, len(reference))
    height, width = reference[0].shape[:2]
    visible = np.logical_or.reduce([frame[:, :, 3] > 0 for frame in reference])
    edge = breath_assets.outer_edge_mask
    eyes = np.logical_or.reduce(
        [core.bbox_mask(source.size, bbox) for bbox in core.EYE_BBOXES]
    )
    hair = core.bbox_mask(source.size, core.HAIR_BBOX) & visible
    arms = breath_assets.arm_region_mask & visible
    legs = core.bbox_mask(source.size, core.FEET_BBOX) & visible

    alpha_changed = 0
    white_flash = 0
    visible_abs = 0
    visible_count = 0
    composite_squared = 0.0
    composite_count = height * width * 3 * len(reference)
    temporal_abs = 0
    temporal_count = 0
    previous_expected: np.ndarray | None = None
    previous_actual: np.ndarray | None = None

    for expected, actual in zip(reference, candidate):
        alpha_changed += int(np.count_nonzero(actual[:, :, 3] != expected[:, :, 3]))
        difference = np.abs(
            actual[:, :, :3].astype(np.int16)
            - expected[:, :, :3].astype(np.int16)
        )
        visible_abs += int(difference[visible].sum())
        visible_count += int(np.count_nonzero(visible)) * 3

        expected_alpha = expected[:, :, 3:4].astype(np.float32) / 255.0
        actual_alpha = actual[:, :, 3:4].astype(np.float32) / 255.0
        expected_composite = (
            expected[:, :, :3].astype(np.float32) * expected_alpha
            + 127.0 * (1.0 - expected_alpha)
        )
        actual_composite = (
            actual[:, :, :3].astype(np.float32) * actual_alpha
            + 127.0 * (1.0 - actual_alpha)
        )
        composite_delta = actual_composite - expected_composite
        composite_squared += float(np.square(composite_delta).sum())

        expected_white = np.all(expected_composite >= 250.0, axis=2)
        actual_white = np.all(actual_composite >= 250.0, axis=2)
        white_flash = max(
            white_flash,
            int(np.count_nonzero(actual_white & ~expected_white & ~eyes)),
        )

        if previous_expected is not None and previous_actual is not None:
            expected_delta = (
                expected[:, :, :3].astype(np.int16)
                - previous_expected[:, :, :3].astype(np.int16)
            )
            actual_delta = (
                actual[:, :, :3].astype(np.int16)
                - previous_actual[:, :, :3].astype(np.int16)
            )
            temporal_abs += int(np.abs(actual_delta - expected_delta)[visible].sum())
            temporal_count += int(np.count_nonzero(visible)) * 3
        previous_expected = expected
        previous_actual = actual

    mse = composite_squared / max(1, composite_count)
    psnr = float("inf") if mse == 0 else 10.0 * math.log10((255.0**2) / mse)
    first_last = np.array_equal(candidate[0], candidate[-1])
    loop_rgb_mae = float(
        np.abs(
            candidate[0][:, :, :3].astype(np.int16)
            - candidate[-1][:, :, :3].astype(np.int16)
        )[visible].mean()
    )
    return VariantMetrics(
        name=name,
        path=path,
        file_size_bytes=path.stat().st_size,
        encoded_frames=basic.encoded_frames,
        psnr_db=psnr,
        visible_rgb_mae=visible_abs / max(1, visible_count),
        edge_rgb_mae=region_mae(reference, candidate, edge),
        eyes_rgb_mae=region_mae(reference, candidate, eyes & visible),
        hair_rgb_mae=region_mae(reference, candidate, hair),
        arms_rgb_mae=region_mae(reference, candidate, arms),
        legs_rgb_mae=region_mae(reference, candidate, legs),
        temporal_delta_mae=temporal_abs / max(1, temporal_count),
        alpha_changed_pixels=alpha_changed,
        white_flash_pixels=white_flash,
        first_last_identical=first_last,
        loop_rgb_mae=loop_rgb_mae,
    )


def encode_final_variants(
    frames: list[Image.Image],
    render: IdleRender,
    source: Image.Image,
    config: core.RenderConfig,
    selected_only: bool = False,
) -> tuple[Path, list[VariantMetrics]]:
    reference_path = core.DEFAULT_OUTPUT / "jive_idle_v1_reference.webp"
    reference_config = replace(
        config,
        lossless=True,
        quality=100,
        method=0,
    )
    core.save_webp(
        frames,
        reference_path,
        reference_config,
        durations=render.durations,
    )
    reference = decode_timeline(
        reference_path,
        config.frame_duration_ms,
        len(frames),
    )

    variants = (
        (("lossless", True, 100),)
        if selected_only
        else (
            ("lossless", True, 100),
            ("quality 95", False, 95),
            ("quality 90", False, 90),
            ("quality 85", False, 85),
        )
    )
    metrics: list[VariantMetrics] = []
    for label, lossless, quality in variants:
        suffix = "lossless" if lossless else f"q{quality}"
        path = core.DEFAULT_OUTPUT / f".jive_idle_v1_{suffix}.webp"
        variant_config = replace(
            config,
            lossless=lossless,
            quality=quality,
            method=6,
        )
        core.save_webp(
            frames,
            path,
            variant_config,
            durations=render.durations,
            minimize_size=True,
        )
        result = compare_variant(
            label,
            path,
            reference,
            source,
            render.duration_ms,
            render.breath_assets,
            config.frame_duration_ms,
        )
        if result.alpha_changed_pixels:
            raise AssertionError(
                f"Unsafe WebP variant {label}: alpha={result.alpha_changed_pixels}"
            )
        metrics.append(result)
        print(
            f"VARIANT {label}: bytes={result.file_size_bytes}, "
            f"frames={result.encoded_frames}, psnr={result.psnr_db:.2f}dB, "
            f"mae={result.visible_rgb_mae:.3f}, edge={result.edge_rgb_mae:.3f}, "
            f"eyes={result.eyes_rgb_mae:.3f}, hair={result.hair_rgb_mae:.3f}, "
            f"arms={result.arms_rgb_mae:.3f}, legs={result.legs_rgb_mae:.3f}, "
            f"temporal={result.temporal_delta_mae:.3f}, "
            f"alpha_changed={result.alpha_changed_pixels}, "
            f"white_flash={result.white_flash_pixels}, "
            f"loop_mae={result.loop_rgb_mae:.3f}"
        )
    return reference_path, metrics


def encode_from_reference(
    reference_path: Path,
    qualities: list[int],
    source: Image.Image,
    config: core.RenderConfig,
    preserve_timeline: bool = False,
    lossless_only: bool = False,
) -> list[VariantMetrics]:
    reference = decode_timeline(
        reference_path,
        config.frame_duration_ms,
        FINAL_DURATION_MS * config.fps // 1_000,
    )
    frames = [Image.fromarray(frame, "RGBA") for frame in reference]
    breath_assets = breath.build_internal_breath_assets(source)
    results: list[VariantMetrics] = []
    variants = (("lossless", True, 100),) if lossless_only else tuple(
        (f"quality {quality}", False, quality) for quality in qualities
    )
    for label, lossless, quality in variants:
        suffix = "_timing" if preserve_timeline else ""
        filename_quality = "lossless" if lossless else f"q{quality}"
        path = core.DEFAULT_OUTPUT / f".jive_idle_v1_{filename_quality}{suffix}.webp"
        variant_config = replace(
            config,
            lossless=lossless,
            quality=quality,
            method=6,
        )
        core.save_webp(
            frames,
            path,
            variant_config,
            durations=[config.frame_duration_ms] * len(frames),
            minimize_size=not preserve_timeline,
            kmin=9,
            kmax=17,
        )
        result = compare_variant(
            label,
            path,
            reference,
            source,
            FINAL_DURATION_MS,
            breath_assets,
            config.frame_duration_ms,
        )
        if result.alpha_changed_pixels:
            raise AssertionError(
                f"Unsafe WebP variant {label}: alpha={result.alpha_changed_pixels}"
            )
        results.append(result)
        print(
            f"VARIANT {label}: bytes={result.file_size_bytes}, "
            f"frames={result.encoded_frames}, psnr={result.psnr_db:.2f}dB, "
            f"mae={result.visible_rgb_mae:.3f}, edge={result.edge_rgb_mae:.3f}, "
            f"eyes={result.eyes_rgb_mae:.3f}, hair={result.hair_rgb_mae:.3f}, "
            f"arms={result.arms_rgb_mae:.3f}, legs={result.legs_rgb_mae:.3f}, "
            f"temporal={result.temporal_delta_mae:.3f}, "
            f"alpha_changed={result.alpha_changed_pixels}, "
            f"white_flash={result.white_flash_pixels}, "
            f"loop_mae={result.loop_rgb_mae:.3f}"
        )
    return results


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    core.add_common_cli(parser, "idle_v1")
    parser.add_argument(
        "--encode-only-reference",
        type=Path,
        help="Encode lossy candidates from an existing lossless FINAL reference.",
    )
    parser.add_argument(
        "--qualities",
        nargs="+",
        type=int,
        choices=(100, 95, 90, 85),
        default=(95, 90, 85),
    )
    parser.add_argument(
        "--preserve-timeline",
        action="store_true",
        help="Keep conservative keyframes instead of global size minimization.",
    )
    parser.add_argument(
        "--lossless-from-reference",
        action="store_true",
        help="Encode only optimized lossless from an existing FINAL reference.",
    )
    parser.add_argument(
        "--selected-only",
        action="store_true",
        help="Encode only the already selected FINAL quality after a render.",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    started = time.perf_counter()
    config = core.config_for(args.mode)
    source = core.load_master(args.source)
    if args.encode_only_reference is not None:
        if config.mode is not core.RenderMode.FINAL:
            raise ValueError("Reference re-encoding is available only in FINAL mode")
        results = encode_from_reference(
            args.encode_only_reference.resolve(),
            list(args.qualities),
            source,
            config,
            preserve_timeline=args.preserve_timeline,
            lossless_only=args.lossless_from_reference,
        )
        print(
            f"Encoded {len(results)} candidates from the lossless reference "
            f"without rerendering in {time.perf_counter() - started:.3f}s"
        )
        return
    render = render_idle(source, config)

    if config.mode is core.RenderMode.FINAL:
        protected = validate_composition(source, render)
        normalized_frames = normalize_transparent_pixels(render.frames)
        reference_path, metrics = encode_final_variants(
            normalized_frames,
            render,
            source,
            config,
            selected_only=args.selected_only,
        )
        selected = next(
            metric
            for metric in metrics
            if metric.name == SELECTED_FINAL_VARIANT
        )
        if not (
            selected.alpha_changed_pixels == 0
            and selected.white_flash_pixels == 0
            and selected.psnr_db >= 42.0
            and selected.edge_rgb_mae < 3.0
            and selected.temporal_delta_mae < 0.1
            and selected.loop_rgb_mae < 1.0
        ):
            raise AssertionError("Selected FINAL encoder quality failed safety limits")
        output: Path = core.resolve_output(args)
        output.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(selected.path, output)
        elapsed = time.perf_counter() - started
        print(
            f"Rendered FINAL once and encoded {len(metrics)} variants in "
            f"{elapsed:.3f}s: reference={reference_path}, "
            f"duration={render.duration_ms}ms, fps={config.fps}, "
            f"timeline_frames={len(render.frames)}, size={source.size}, "
            f"oversampling={config.oversampling}x, conflicts={protected[0]}, "
            f"hair={protected[1]}px, arms={protected[2]}px, "
            f"lower={protected[3]}px, edge={protected[4]}px, "
            f"white_flash={protected[5]}px, selected={selected.name}, "
            f"output={output}"
        )
        return

    if args.output is None:
        args.output = core.DEFAULT_OUTPUT / "jive_idle_v1_preview.webp"
    output: Path = core.resolve_output(args)
    export_config = replace(config, lossless=True, quality=100, method=0)
    core.save_webp(
        render.frames,
        output,
        export_config,
        durations=render.durations,
    )
    smoke = smoke_test(output, source, render)
    elapsed = time.perf_counter() - started
    print(
        f"Created {output} in {elapsed:.3f}s: mode={config.mode.value}, "
        f"duration={smoke.duration_ms}ms, fps={config.fps}, "
        f"timeline_frames={len(render.frames)}, encoded_frames={smoke.encoded_frames}, "
        f"size={smoke.size}, alpha={smoke.has_alpha}, loop={smoke.loop}, "
        f"oversampling={config.oversampling}x, first_last={smoke.first_last_identical}, "
        f"look=LEFT {look.LEFT_MOVE[0]:.2f}-{look.LEFT_RETURN[1]:.2f}s, "
        f"blink_closed={render.blink_closed_seconds}, conflicts={smoke.module_conflicts}, "
        f"hair={smoke.changed_hair_pixels}px, arms={smoke.changed_arm_pixels}px, "
        f"lower={smoke.changed_lower_pixels}px, edge={smoke.changed_edge_pixels}px, "
        f"white_flash={smoke.new_white_flash_pixels}px, bytes={output.stat().st_size}"
    )


if __name__ == "__main__":
    main()
