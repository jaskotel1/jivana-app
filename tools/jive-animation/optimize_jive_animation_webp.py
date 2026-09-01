#!/usr/bin/env python3
"""Create Android-size animated WebP candidates from lossless master PNG frames."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import sys
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Sequence

import cv2
import numpy as np
from PIL import Image

import video_to_jive_animation as converter


@dataclass(frozen=True)
class VariantConfig:
    label: str
    filename_suffix: str
    size: int
    fps: int
    quality: int


@dataclass(frozen=True)
class VariantResult:
    variant: str
    path: str
    preview: str
    resolution: str
    fps: int
    frames: int
    duration_ms: int
    quality: int
    file_size_bytes: int
    file_size_kb: float
    file_size_mb: float
    composite_psnr_db: float
    visible_rgb_mae: float
    edge_rgb_mae: float
    alpha_max_error: int


VARIANTS = (
    VariantConfig("A", "512-20", 512, 20, 88),
    VariantConfig("B", "512-15", 512, 15, 86),
    VariantConfig("C", "512-15-compressed", 512, 15, 72),
    VariantConfig("D", "640-15", 640, 15, 86),
)


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "master_dir",
        type=Path,
        help="Animation directory containing frames/ and metadata.json.",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        help="Candidate directory (default: <master-dir>/optimized).",
    )
    parser.add_argument(
        "--name",
        help="Output filename prefix (default: animation name from metadata).",
    )
    parser.add_argument(
        "--skip-640",
        action="store_true",
        help="Skip optional 640x640 variant D.",
    )
    return parser.parse_args(argv)


def load_master_timeline(
    master_dir: Path,
) -> tuple[list[Path], list[int], tuple[int, int], dict[str, object]]:
    metadata_path = master_dir / "metadata.json"
    frames_dir = master_dir / "frames"
    if not metadata_path.is_file():
        raise FileNotFoundError(f"Metadata not found: {metadata_path}")
    if not frames_dir.is_dir():
        raise FileNotFoundError(f"Master frame directory not found: {frames_dir}")
    try:
        metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise ValueError(f"Invalid metadata JSON: {error}") from error

    frame_entries = metadata.get("frames")
    if not isinstance(frame_entries, list) or not frame_entries:
        raise ValueError("metadata.json does not contain a non-empty frames array")
    durations: list[int] = []
    for index, entry in enumerate(frame_entries):
        if not isinstance(entry, dict) or not isinstance(entry.get("durationMs"), int):
            raise ValueError(f"Metadata frame {index} has no integer durationMs")
        duration = int(entry["durationMs"])
        if duration <= 0:
            raise ValueError(f"Metadata frame {index} has an invalid duration")
        durations.append(duration)

    frame_paths = sorted(frames_dir.glob("frame_*.png"))
    if len(frame_paths) != len(durations):
        raise ValueError(
            f"Found {len(frame_paths)} master PNGs but {len(durations)} metadata frames"
        )
    width = int(metadata.get("width", 0))
    height = int(metadata.get("height", 0))
    expected_size = (width, height)
    if width <= 0 or height <= 0:
        raise ValueError("metadata.json contains an invalid resolution")
    for index, path in enumerate(frame_paths):
        with Image.open(path) as image:
            if image.mode != "RGBA" or image.size != expected_size:
                raise ValueError(
                    f"Master frame {index} must be RGBA {width}x{height}, got "
                    f"{image.mode} {image.size}"
                )
    if int(metadata.get("durationMs", sum(durations))) != sum(durations):
        raise ValueError("Metadata duration does not equal the master frame timeline")
    return frame_paths, durations, expected_size, metadata


def master_frames_fingerprint(frame_paths: Sequence[Path]) -> str:
    digest = hashlib.sha256()
    for path in frame_paths:
        digest.update(path.name.encode("utf-8"))
        with path.open("rb") as handle:
            for chunk in iter(lambda: handle.read(1 << 20), b""):
                digest.update(chunk)
    return digest.hexdigest()


def target_timeline(duration_ms: int, fps: int) -> tuple[list[int], list[int]]:
    starts: list[int] = []
    index = 0
    while True:
        timestamp = round(index * 1000.0 / fps)
        if timestamp >= duration_ms:
            break
        starts.append(timestamp)
        index += 1
    boundaries = starts + [duration_ms]
    durations = [
        boundaries[index + 1] - boundaries[index]
        for index in range(len(starts))
    ]
    if not starts or any(duration <= 0 for duration in durations):
        raise ValueError(f"Could not create a valid {fps} FPS target timeline")
    return starts, durations


def select_master_indices(
    master_durations: Sequence[int],
    target_starts: Sequence[int],
    target_durations: Sequence[int],
) -> list[int]:
    master_ends = np.cumsum(np.asarray(master_durations, dtype=np.int64))
    selected: list[int] = []
    for start, duration in zip(target_starts, target_durations):
        sample_time = start + duration / 2.0
        index = int(np.searchsorted(master_ends, sample_time, side="right"))
        selected.append(min(index, len(master_durations) - 1))
    return selected


def resize_rgba(image: Image.Image, size: int) -> Image.Image:
    # Resize premultiplied pixels so transparent white/gray cannot bleed into
    # the furry silhouette or the three thin hairs.
    premultiplied = image.convert("RGBA").convert("RGBa")
    resized = premultiplied.resize((size, size), Image.Resampling.LANCZOS)
    result = resized.convert("RGBA")
    array = np.asarray(result).copy()
    array[array[:, :, 3] == 0, :3] = 0
    return Image.fromarray(array, "RGBA")


def load_resized_frames(
    frame_paths: Sequence[Path], selected_indices: Sequence[int], size: int
) -> list[Image.Image]:
    frames: list[Image.Image] = []
    for index in selected_indices:
        with Image.open(frame_paths[index]) as image:
            frames.append(resize_rgba(image, size))
    return frames


def save_webp(
    frames: Sequence[Image.Image],
    durations: Sequence[int],
    path: Path,
    quality: int,
) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    frames[0].save(
        path,
        format="WEBP",
        save_all=True,
        append_images=list(frames[1:]),
        duration=list(durations),
        loop=0,
        lossless=False,
        quality=quality,
        alpha_quality=100,
        method=4,
        minimize_size=True,
        exact=True,
    )


def decode_and_validate(
    path: Path,
    expected_frames: int,
    expected_size: tuple[int, int],
    expected_duration_ms: int,
) -> tuple[list[Image.Image], int]:
    decoded: list[Image.Image] = []
    duration = 0
    with Image.open(path) as animation:
        if animation.format != "WEBP" or not animation.is_animated:
            raise AssertionError(f"{path.name} is not an animated WebP")
        if animation.n_frames != expected_frames:
            raise AssertionError(
                f"{path.name} has {animation.n_frames} frames, expected {expected_frames}"
            )
        if animation.size != expected_size:
            raise AssertionError(f"{path.name} has an unexpected resolution")
        if animation.info.get("loop") != 0:
            raise AssertionError(f"{path.name} does not loop infinitely")
        for index in range(animation.n_frames):
            animation.seek(index)
            animation.load()
            frame = animation.convert("RGBA").copy()
            if not np.any(np.asarray(frame.getchannel("A")) < 255):
                raise AssertionError(f"{path.name} frame {index} lost transparency")
            decoded.append(frame)
            duration += int(animation.info.get("duration", 0))
    if duration != expected_duration_ms:
        raise AssertionError(
            f"{path.name} duration is {duration}ms, expected {expected_duration_ms}ms"
        )
    return decoded, duration


def visual_metrics(
    references: Sequence[Image.Image], decoded: Sequence[Image.Image]
) -> tuple[float, float, float, int]:
    squared_error = 0.0
    composite_values = 0
    visible_error = 0.0
    visible_values = 0
    edge_error = 0.0
    edge_values = 0
    alpha_max_error = 0
    kernel = np.ones((3, 3), dtype=np.uint8)
    for expected_image, actual_image in zip(references, decoded):
        expected = np.asarray(expected_image.convert("RGBA"), dtype=np.float32)
        actual = np.asarray(actual_image.convert("RGBA"), dtype=np.float32)
        expected_alpha = expected[:, :, 3] / 255.0
        actual_alpha = actual[:, :, 3] / 255.0
        alpha_max_error = max(
            alpha_max_error,
            int(np.abs(expected[:, :, 3] - actual[:, :, 3]).max()),
        )

        expected_composite = (
            expected[:, :, :3] * expected_alpha[:, :, None]
            + 127.0 * (1.0 - expected_alpha[:, :, None])
        )
        actual_composite = (
            actual[:, :, :3] * actual_alpha[:, :, None]
            + 127.0 * (1.0 - actual_alpha[:, :, None])
        )
        delta = actual_composite - expected_composite
        squared_error += float(np.square(delta).sum())
        composite_values += delta.size

        visible = expected_alpha >= 0.10
        rgb_delta = np.abs(actual[:, :, :3] - expected[:, :, :3])
        visible_error += float(rgb_delta[visible].sum())
        visible_values += int(np.count_nonzero(visible)) * 3

        solid = (expected_alpha >= 0.50).astype(np.uint8)
        edge = cv2.morphologyEx(solid, cv2.MORPH_GRADIENT, kernel).astype(bool)
        edge |= (expected_alpha > 0.0) & (expected_alpha < 1.0)
        edge_error += float(rgb_delta[edge].sum())
        edge_values += int(np.count_nonzero(edge)) * 3

    mse = squared_error / max(1, composite_values)
    psnr = float("inf") if mse == 0 else 10.0 * math.log10((255.0**2) / mse)
    return (
        psnr,
        visible_error / max(1, visible_values),
        edge_error / max(1, edge_values),
        alpha_max_error,
    )


def representative_indices(durations: Sequence[int]) -> list[int]:
    ends = np.cumsum(np.asarray(durations, dtype=np.int64))
    duration = int(ends[-1])
    times = (0, min(1600, duration - 1), min(3500, duration - 1), min(4800, duration - 1))
    return [min(int(np.searchsorted(ends, time, side="right")), len(durations) - 1) for time in times]


def save_preview(frames: Sequence[Image.Image], durations: Sequence[int], path: Path) -> None:
    indices = representative_indices(durations)
    tile_width, tile_height = frames[0].size
    canvas = Image.new("RGB", (tile_width * 2, tile_height * 2), (255, 255, 255))
    for position, index in enumerate(indices):
        preview = converter.checkerboard_preview(frames[index])
        canvas.paste(preview, ((position % 2) * tile_width, (position // 2) * tile_height))
    canvas.save(path, format="PNG")


def encode_variant(
    config: VariantConfig,
    name: str,
    output_dir: Path,
    frame_paths: Sequence[Path],
    master_durations: Sequence[int],
) -> VariantResult:
    duration_ms = sum(master_durations)
    starts, durations = target_timeline(duration_ms, config.fps)
    selected = select_master_indices(master_durations, starts, durations)
    references = load_resized_frames(frame_paths, selected, config.size)
    path = output_dir / f"{name}-{config.filename_suffix}.webp"
    save_webp(references, durations, path, config.quality)
    decoded, decoded_duration = decode_and_validate(
        path,
        len(references),
        (config.size, config.size),
        duration_ms,
    )
    psnr, visible_mae, edge_mae, alpha_error = visual_metrics(references, decoded)
    preview_path = output_dir / f"{name}-{config.filename_suffix}-preview.png"
    save_preview(decoded, durations, preview_path)
    size_bytes = path.stat().st_size
    return VariantResult(
        variant=config.label,
        path=str(path),
        preview=str(preview_path),
        resolution=f"{config.size}x{config.size}",
        fps=config.fps,
        frames=len(references),
        duration_ms=decoded_duration,
        quality=config.quality,
        file_size_bytes=size_bytes,
        file_size_kb=size_bytes / 1024.0,
        file_size_mb=size_bytes / (1024.0 * 1024.0),
        composite_psnr_db=psnr,
        visible_rgb_mae=visible_mae,
        edge_rgb_mae=edge_mae,
        alpha_max_error=alpha_error,
    )


def print_report(results: Sequence[VariantResult]) -> None:
    print(
        "Variant | Resolution | FPS | Frames | Duration | Quality | "
        "Size KB | Size MB | PSNR dB | Edge MAE | Alpha max"
    )
    print("-" * 112)
    for result in sorted(results, key=lambda item: item.file_size_bytes):
        print(
            f"{result.variant:>7} | {result.resolution:>10} | {result.fps:>3} | "
            f"{result.frames:>6} | {result.duration_ms / 1000:>7.3f}s | "
            f"{result.quality:>7} | {result.file_size_kb:>7.1f} | "
            f"{result.file_size_mb:>7.3f} | {result.composite_psnr_db:>7.2f} | "
            f"{result.edge_rgb_mae:>8.3f} | {result.alpha_max_error:>9}"
        )


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(argv)
    master_dir = args.master_dir.expanduser().resolve()
    frame_paths, master_durations, _, metadata = load_master_timeline(master_dir)
    master_fingerprint = master_frames_fingerprint(frame_paths)
    output_dir = (
        args.output_dir.expanduser().resolve()
        if args.output_dir
        else master_dir / "optimized"
    )
    output_dir.mkdir(parents=True, exist_ok=True)
    name = args.name or str(metadata.get("animation", master_dir.name))
    configs = VARIANTS[:-1] if args.skip_640 else VARIANTS
    results: list[VariantResult] = []
    for config in configs:
        print(
            f"Encoding {config.label}: {config.size}x{config.size}, "
            f"{config.fps} FPS, quality {config.quality}...",
            flush=True,
        )
        results.append(
            encode_variant(
                config, name, output_dir, frame_paths, master_durations
            )
        )
    if master_frames_fingerprint(frame_paths) != master_fingerprint:
        raise AssertionError("Master PNG frames changed during WebP optimization")
    report_path = output_dir / "optimization-report.json"
    report_path.write_text(
        json.dumps(
            {
                "sourceMaster": str(master_dir),
                "masterFramesUnmodified": True,
                "masterFramesSha256": master_fingerprint,
                "durationMs": sum(master_durations),
                "variants": [asdict(result) for result in results],
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    print_report(results)
    print(f"Report: {report_path}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, FileNotFoundError, ValueError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(1)
