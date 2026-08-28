#!/usr/bin/env python3
"""Shared rendering infrastructure for the Jive animation generators."""

from __future__ import annotations

import argparse
import hashlib
import json
from dataclasses import dataclass
from enum import Enum
from pathlib import Path
from typing import Sequence

import numpy as np
from PIL import Image, ImageFilter
from scipy import ndimage


ROOT = Path(__file__).resolve().parent
DEFAULT_SOURCE = ROOT / "source" / "jive.png"
DEFAULT_OUTPUT = ROOT / "output"
DEFAULT_CACHE = ROOT / ".cache"
JIVE_SIZE = (365, 626)
EYE_BBOXES = ((94, 171, 191, 291), (178, 171, 279, 289))
PUPIL_SEARCH_BBOXES = ((124, 194, 184, 279), (191, 194, 255, 278))
VALIDATION_EYE_BBOX = (88, 165, 285, 297)
BODY_CENTER_X = 182.5
REFERENCE_HALF_WIDTH_PX = 140.0
BODY_ACTIVE_TOP = 300
BODY_ACTIVE_BOTTOM = 490
BODY_CROP = (35, 285, 330, 500)
FACE_BBOX = (85, 115, 285, 300)
EYES_BBOX = VALIDATION_EYE_BBOX
BROWS_BBOX = (95, 125, 270, 170)
MOUTH_BBOX = (130, 255, 235, 300)
HAIR_BBOX = (100, 0, 265, 100)
FEET_BBOX = (70, 490, 290, 575)
SHADOW_BBOX = (35, 550, 330, 585)


class RenderMode(str, Enum):
    PROTOTYPE = "prototype"
    FINAL = "final"


@dataclass(frozen=True)
class RenderConfig:
    mode: RenderMode
    fps: int
    oversampling: int
    full_validation: bool
    lossless: bool
    quality: int
    method: int

    @property
    def frame_duration_ms(self) -> int:
        return round(1_000 / self.fps)


@dataclass(frozen=True)
class WebPInfo:
    encoded_frames: int
    duration_ms: int
    loop: int
    size: tuple[int, int]
    has_alpha: bool


@dataclass(frozen=True)
class TorsoAssets:
    source_crop: Image.Image
    base_high: Image.Image
    body_high: Image.Image
    motion_mask_low: Image.Image
    arm_mask_full: Image.Image
    crop_bbox: tuple[int, int, int, int]


def config_for(mode: RenderMode | str) -> RenderConfig:
    mode = RenderMode(mode)
    if mode is RenderMode.PROTOTYPE:
        return RenderConfig(mode, 12, 2, False, False, 86, 0)
    # Lossless pixels with the fast encoder. Container-size optimization is a
    # separate release step and must not slow down motion iteration.
    return RenderConfig(mode, 25, 8, True, True, 100, 0)


def add_common_cli(
    parser: argparse.ArgumentParser,
    animation_name: str,
) -> None:
    parser.add_argument(
        "--mode",
        choices=[mode.value for mode in RenderMode],
        default=RenderMode.PROTOTYPE.value,
    )
    parser.add_argument("--source", type=Path, default=DEFAULT_SOURCE)
    parser.add_argument("--output", type=Path)
    parser.set_defaults(animation_name=animation_name)


def resolve_output(args: argparse.Namespace) -> Path:
    if args.output:
        return args.output.resolve()
    suffix = "_preview" if args.mode == RenderMode.PROTOTYPE.value else ""
    return DEFAULT_OUTPUT / f"jive_{args.animation_name}{suffix}.webp"


def load_master(path: Path = DEFAULT_SOURCE) -> Image.Image:
    if not path.is_file():
        raise FileNotFoundError(f"Jive master asset not found: {path}")
    with Image.open(path) as image:
        result = image.convert("RGBA")
    if result.size != JIVE_SIZE:
        raise ValueError(f"Expected Jive {JIVE_SIZE}, got {result.size}")
    return result


def canonical_rgba(array: np.ndarray) -> np.ndarray:
    result = array.copy()
    result[result[:, :, 3] == 0, :3] = 0
    return result


def premultiply(image: Image.Image) -> Image.Image:
    return image.convert("RGBA").convert("RGBa")


def unpremultiply(image: Image.Image) -> Image.Image:
    return image.convert("RGBA")


def resize_rgba(
    image: Image.Image,
    size: tuple[int, int],
    resample: Image.Resampling = Image.Resampling.LANCZOS,
) -> Image.Image:
    return unpremultiply(premultiply(image).resize(size, resample))


def transform_rgba(
    image: Image.Image,
    size: tuple[int, int],
    method: Image.Transform,
    data: object,
    resample: Image.Resampling = Image.Resampling.BICUBIC,
) -> Image.Image:
    transformed = premultiply(image).transform(
        size, method, data, resample=resample, fillcolor=(0, 0, 0, 0)
    )
    return unpremultiply(transformed)


def feathered_mask(binary: np.ndarray, radius: float) -> Image.Image:
    mask = Image.fromarray(binary.astype(np.uint8) * 255, "L")
    return mask.filter(ImageFilter.GaussianBlur(radius))


def largest_component(binary: np.ndarray) -> np.ndarray:
    labels, count = ndimage.label(binary)
    if count == 0:
        raise RuntimeError("No connected component found")
    sizes = ndimage.sum(binary, labels, index=range(1, count + 1))
    return labels == (int(np.argmax(sizes)) + 1)


def extract_with_mask(image: Image.Image, mask: Image.Image) -> Image.Image:
    result = Image.new("RGBA", image.size, (0, 0, 0, 0))
    result.paste(image, (0, 0), mask)
    return result


def bbox_mask(size: tuple[int, int], bbox: tuple[int, int, int, int]) -> np.ndarray:
    x0, y0, x1, y1 = bbox
    mask = np.zeros((size[1], size[0]), dtype=bool)
    mask[y0:y1, x0:x1] = True
    return mask


def torso_vertical_envelope(global_y: float) -> float:
    if global_y <= BODY_ACTIVE_TOP or global_y >= BODY_ACTIVE_BOTTOM:
        return 0.0
    progress = (global_y - BODY_ACTIVE_TOP) / (BODY_ACTIVE_BOTTOM - BODY_ACTIVE_TOP)
    return float(np.sin(np.pi * progress) ** 2)


def torso_motion_mask(source: Image.Image) -> np.ndarray:
    rgba = np.asarray(source.convert("RGBA"))
    rgb = rgba[:, :, :3].astype(np.int16)
    alpha = rgba[:, :, 3]
    orange = (
        (rgb[:, :, 0] > 145)
        & (rgb[:, :, 0] > rgb[:, :, 1] + 35)
        & (rgb[:, :, 1] > 45)
        & (rgb[:, :, 1] < 190)
        & (rgb[:, :, 2] < 165)
        & (alpha > 12)
    )
    orange[:BODY_ACTIVE_TOP] = False
    orange[BODY_ACTIVE_BOTTOM:] = False
    body = ndimage.binary_fill_holes(largest_component(orange))
    body = ndimage.binary_dilation(body, iterations=5)
    body[:BODY_ACTIVE_TOP] = False
    body[BODY_ACTIVE_BOTTOM:] = False
    return body


def static_arm_mask(source: Image.Image) -> np.ndarray:
    rgba = np.asarray(source.convert("RGBA"))
    rgb, alpha = rgba[:, :, :3], rgba[:, :, 3]
    yy, xx = np.indices(alpha.shape)
    candidates = (
        (rgb.max(axis=2) < 110) & (alpha > 12) & (yy >= 270) & (yy < 500)
        & ((xx < 90) | (xx > 275))
    )
    labels, count = ndimage.label(candidates)
    arms = np.zeros_like(candidates)
    for index in range(1, count + 1):
        component = labels == index
        if int(component.sum()) > 1_000:
            arms |= component
    return ndimage.binary_dilation(arms, iterations=2)


def build_torso_assets(source: Image.Image, oversampling: int) -> TorsoAssets:
    motion_full = torso_motion_mask(source)
    arm_full = static_arm_mask(source)
    x0, y0, x1, y1 = BODY_CROP
    source_crop = source.crop(BODY_CROP)
    motion_local = motion_full[y0:y1, x0:x1]
    body_mask = Image.fromarray(motion_local.astype(np.uint8) * 255, "L")
    body_layer = extract_with_mask(source_crop, body_mask)
    base = source_crop.copy()
    base.paste(Image.new("RGBA", source_crop.size), (0, 0), body_mask)
    high_size = (source_crop.width * oversampling, source_crop.height * oversampling)
    affected = ndimage.binary_dilation(motion_local, iterations=5)
    affected_values = np.asarray(feathered_mask(affected, 0.55), dtype=np.float32)
    vertical = np.asarray(
        [torso_vertical_envelope(float(row)) for row in range(y0, y1)],
        dtype=np.float32,
    )[:, None]
    affected_values *= vertical
    return TorsoAssets(
        source_crop,
        resize_rgba(base, high_size),
        resize_rgba(body_layer, high_size),
        Image.fromarray(np.round(affected_values).astype(np.uint8), "L"),
        Image.fromarray(arm_full.astype(np.uint8) * 255, "L"),
        BODY_CROP,
    )


def bbox_identical(
    frames: Sequence[Image.Image], source: Image.Image, bbox: tuple[int, int, int, int]
) -> bool:
    reference = np.asarray(source.crop(bbox).convert("RGBA"))
    return all(np.array_equal(np.asarray(frame.crop(bbox).convert("RGBA")), reference) for frame in frames)


def mask_identical(
    frames: Sequence[Image.Image], source: Image.Image, mask: np.ndarray
) -> bool:
    reference = np.asarray(source.convert("RGBA"))
    return all(np.array_equal(np.asarray(frame.convert("RGBA"))[mask], reference[mask]) for frame in frames)


def alpha_center(image: Image.Image, bbox: tuple[int, int, int, int]) -> tuple[float, float]:
    x0, y0, x1, y1 = bbox
    alpha = np.asarray(image.crop(bbox).getchannel("A"), dtype=np.float64).copy()
    alpha[alpha < 12] = 0
    yy, xx = np.indices(alpha.shape)
    total = float(alpha.sum())
    return x0 + float((xx * alpha).sum() / total), y0 + float((yy * alpha).sum() / total)


def max_center_delta(frames: Sequence[Image.Image], bbox: tuple[int, int, int, int]) -> float:
    centers = np.asarray([alpha_center(frame, bbox) for frame in frames])
    return float(np.abs(centers - centers[0]).max())


def subpixel_edge_position(values: np.ndarray, from_left: bool) -> float:
    values = values.astype(np.float64) / 255.0
    if not from_left:
        values = values[::-1]
    indices = np.flatnonzero(values >= 0.5)
    if indices.size == 0:
        return float("nan")
    index = int(indices[0])
    position = 0.0 if index == 0 else index - 1 + (0.5 - values[index - 1]) / max(1e-9, values[index] - values[index - 1])
    return position if from_left else len(values) - 1 - position


def coalesce_frames(
    frames: Sequence[Image.Image], frame_duration_ms: int
) -> tuple[list[Image.Image], list[int]]:
    if not frames:
        raise ValueError("Cannot encode an empty animation")
    encoded = [frames[0]]
    durations = [frame_duration_ms]
    previous = canonical_rgba(np.asarray(frames[0].convert("RGBA")))
    for frame in frames[1:]:
        current = canonical_rgba(np.asarray(frame.convert("RGBA")))
        if np.array_equal(current, previous):
            durations[-1] += frame_duration_ms
        else:
            encoded.append(frame)
            durations.append(frame_duration_ms)
            previous = current
    return encoded, durations


def save_webp(
    frames: Sequence[Image.Image],
    output: Path,
    config: RenderConfig,
    *,
    durations: Sequence[int] | None = None,
    minimize_size: bool = False,
    kmin: int | None = 1,
    kmax: int | None = 1,
) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    if durations is None:
        encoded, encoded_durations = coalesce_frames(
            frames, config.frame_duration_ms
        )
    else:
        encoded = list(frames)
        encoded_durations = list(durations)
    encoder_options = {
        "format": "WEBP",
        "save_all": True,
        "append_images": encoded[1:],
        "duration": encoded_durations,
        "loop": 0,
        "lossless": config.lossless,
        "quality": config.quality,
        "alpha_quality": 100,
        "method": config.method,
        "minimize_size": minimize_size,
        "exact": True,
    }
    if not minimize_size and kmin is not None and kmax is not None:
        encoder_options.update(kmin=kmin, kmax=kmax)
    encoded[0].save(output, **encoder_options)


def validate_webp(
    output: Path,
    expected_size: tuple[int, int],
    expected_duration_ms: int,
) -> WebPInfo:
    with Image.open(output) as animation:
        if animation.format != "WEBP" or not animation.is_animated:
            raise AssertionError("Output is not an animated WebP")
        if animation.size != expected_size:
            raise AssertionError((animation.size, expected_size))
        if animation.info.get("loop") != 0:
            raise AssertionError("WebP loop must be infinite")
        duration = 0
        has_alpha = True
        for index in range(animation.n_frames):
            animation.seek(index)
            animation.load()
            duration += int(animation.info.get("duration", 0))
            has_alpha &= "A" in animation.convert("RGBA").getbands()
        info = WebPInfo(
            animation.n_frames, duration, 0, animation.size, has_alpha
        )
    if info.duration_ms != expected_duration_ms:
        raise AssertionError((info.duration_ms, expected_duration_ms))
    return info


def source_fingerprint(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_cached_npz(name: str, source: Path, oversampling: int) -> dict[str, np.ndarray] | None:
    cache_path = DEFAULT_CACHE / f"{name}-{oversampling}x.npz"
    metadata_path = cache_path.with_suffix(".json")
    if not cache_path.is_file() or not metadata_path.is_file():
        return None
    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    if metadata.get("source_sha256") != source_fingerprint(source):
        return None
    with np.load(cache_path, allow_pickle=False) as data:
        return {key: data[key] for key in data.files}


def save_cached_npz(
    name: str,
    source: Path,
    oversampling: int,
    **arrays: np.ndarray,
) -> None:
    DEFAULT_CACHE.mkdir(parents=True, exist_ok=True)
    cache_path = DEFAULT_CACHE / f"{name}-{oversampling}x.npz"
    np.savez_compressed(cache_path, **arrays)
    cache_path.with_suffix(".json").write_text(
        json.dumps({"source_sha256": source_fingerprint(source)}),
        encoding="utf-8",
    )
