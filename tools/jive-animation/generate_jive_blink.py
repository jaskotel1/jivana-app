#!/usr/bin/env python3
"""Generate the accepted Jive Blink v5 motion through the shared pipeline."""

from __future__ import annotations

import argparse
import time
from dataclasses import dataclass
from enum import Enum

import numpy as np
from PIL import Image
from scipy import ndimage

import jive_animation_core as core


DURATION_SECONDS = 3
EYE_BBOXES = core.EYE_BBOXES


class EyePose(Enum):
    OPEN = "open"
    LID_50 = "top_lid_50"
    LID_94 = "top_lid_94"
    CLOSED = "closed"


@dataclass(frozen=True)
class EyeAssets:
    bbox: tuple[int, int, int, int]
    cover_mask_high: Image.Image
    fur_high: Image.Image
    outline_high: Image.Image


def eye_mask_from_bbox(rgba: np.ndarray, bbox: tuple[int, int, int, int]) -> np.ndarray:
    x0, y0, x1, y1 = bbox
    crop = rgba[y0:y1, x0:x1]
    white = (crop[:, :, :3].min(axis=2) > 205) & (crop[:, :, 3] > 12)
    white = ndimage.binary_closing(white, iterations=2)
    white = ndimage.binary_fill_holes(white)
    white = ndimage.binary_dilation(white, iterations=6)
    result = np.zeros(rgba.shape[:2], dtype=bool)
    result[y0:y1, x0:x1] = white
    return result


def remove_eyes(source: Image.Image, eye_mask: np.ndarray) -> Image.Image:
    rgba = np.asarray(source, dtype=np.float32).copy()
    expanded = ndimage.binary_dilation(eye_mask, iterations=3)
    boundary = ndimage.binary_dilation(expanded, iterations=2) & ~expanded
    filled = rgba.copy()
    filled[expanded] = rgba[boundary].mean(axis=0)
    for _ in range(420):
        neighbors = (
            np.roll(filled, 1, 0) + np.roll(filled, -1, 0)
            + np.roll(filled, 1, 1) + np.roll(filled, -1, 1)
        ) * 0.25
        filled[expanded] = neighbors[expanded]
    replacement = Image.fromarray(np.clip(filled, 0, 255).astype(np.uint8), "RGBA")
    result = source.copy()
    result.paste(replacement, (0, 0), core.feathered_mask(expanded, 1.0))
    return result


def upper_outline(rgba: np.ndarray, eye: np.ndarray, bbox: tuple[int, int, int, int]) -> np.ndarray:
    x0, y0, x1, y1 = bbox
    perimeter = eye & ~ndimage.binary_erosion(eye, iterations=8)
    dark = (rgba[:, :, :3].max(axis=2) < 105) & (rgba[:, :, 3] > 12)
    rows = np.arange(rgba.shape[0])[:, None]
    candidates = perimeter & dark & (rows <= y0 + int((y1 - y0) * 0.48))
    selected = np.zeros_like(candidates)
    for x in range(x0, x1):
        ys = np.flatnonzero(candidates[y0:y1, x])
        if ys.size:
            selected[y0 + int(ys.min()), x] = True
    if bbox == EYE_BBOXES[0]:
        selected[:, x1 - 14:x1] = False
    else:
        selected[:, x0:x0 + 14] = False
    return ndimage.binary_dilation(selected, iterations=1)


def build_assets(source: Image.Image, oversampling: int) -> list[EyeAssets]:
    rgba = np.asarray(source)
    eyes = [eye_mask_from_bbox(rgba, bbox) for bbox in EYE_BBOXES]
    no_eyes = remove_eyes(source, np.logical_or.reduce(eyes))
    result = []
    for bbox, eye in zip(EYE_BBOXES, eyes):
        x0, y0, x1, y1 = bbox
        size = ((x1 - x0) * oversampling, (y1 - y0) * oversampling)
        cover = core.feathered_mask(ndimage.binary_dilation(eye, iterations=4), 0.75).crop(bbox)
        outline_mask = core.feathered_mask(upper_outline(rgba, eye, bbox), 0.35)
        outline = core.extract_with_mask(source, outline_mask).crop(bbox)
        result.append(EyeAssets(
            bbox,
            cover.resize(size, Image.Resampling.LANCZOS),
            core.resize_rgba(no_eyes.crop(bbox), size),
            core.resize_rgba(outline, size),
        ))
    return result


def top_cover(mask: Image.Image, coverage: float) -> Image.Image:
    values = np.asarray(mask, dtype=np.float32) / 255.0
    height, width = values.shape
    rows = np.arange(height, dtype=np.float32)[:, None]
    columns = np.arange(width, dtype=np.float32)[None, :]
    x = (columns - (width - 1) * 0.5) / max(1.0, width * 0.5)
    curve = np.clip(1.0 - x**2, 0.0, 1.0)
    depth = 0.07 if coverage == 0.50 else 0.03
    boundary = height * (coverage - depth + depth * curve)
    feather = max(1.0, height * 0.0055)
    result = np.clip((boundary - rows) / feather + 0.5, 0.0, 1.0)
    return Image.fromarray(np.round(result * values * 255).astype(np.uint8), "L")


def closed_arc(asset: EyeAssets) -> Image.Image:
    bbox = asset.outline_high.getchannel("A").getbbox()
    if bbox is None:
        raise RuntimeError("Cannot locate eye outline")
    crop = asset.outline_high.crop(bbox)
    height = max(2, round(crop.height * 0.38))
    transformed = core.resize_rgba(crop, (crop.width, height))
    result = Image.new("RGBA", asset.outline_high.size, (0, 0, 0, 0))
    result.alpha_composite(transformed, (bbox[0], round(result.height * 0.53 - height * 0.5)))
    return result


def render_eye(source_patch: Image.Image, asset: EyeAssets, pose: EyePose) -> Image.Image:
    if pose is EyePose.OPEN:
        return source_patch.copy()
    cover = top_cover(asset.cover_mask_high, 0.50) if pose is EyePose.LID_50 else (
        top_cover(asset.cover_mask_high, 0.94) if pose is EyePose.LID_94 else asset.cover_mask_high
    )
    result = source_patch.copy()
    result.paste(core.resize_rgba(asset.fur_high, source_patch.size), (0, 0), cover.resize(source_patch.size, Image.Resampling.LANCZOS))
    if pose is EyePose.CLOSED:
        result = Image.alpha_composite(result, core.resize_rgba(closed_arc(asset), source_patch.size))
    return result


def render_pose(source: Image.Image, assets: list[EyeAssets], pose: EyePose) -> Image.Image:
    if pose is EyePose.OPEN:
        return source.copy()
    frame = source.copy()
    split_x = round(((94 + 191) * 0.5 + (178 + 279) * 0.5) * 0.5)
    for index, asset in enumerate(assets):
        x0, y0, x1, y1 = asset.bbox
        patch = render_eye(source.crop(asset.bbox), asset, pose)
        if index == 0:
            frame.paste(patch.crop((0, 0, split_x - x0, y1 - y0)), (x0, y0))
        else:
            frame.paste(patch.crop((split_x - x0, 0, x1 - x0, y1 - y0)), (split_x, y0))
    return frame


def pose_at(time_seconds: float) -> EyePose:
    # Accepted v5: OPEN -> 50% -> 94% -> CLOSED -> 94% -> 50% -> OPEN.
    step = 1 / 25
    start = 37 * step
    relative = round((time_seconds - start) / step)
    return {
        0: EyePose.LID_50,
        1: EyePose.LID_94,
        2: EyePose.CLOSED,
        3: EyePose.LID_94,
        4: EyePose.LID_50,
    }.get(relative, EyePose.OPEN)


def render(source: Image.Image, config: core.RenderConfig) -> list[Image.Image]:
    assets = build_assets(source, config.oversampling)
    poses = {pose: render_pose(source, assets, pose) for pose in EyePose}
    count = config.fps * DURATION_SECONDS
    if config.mode is core.RenderMode.PROTOTYPE:
        sequence = (
            EyePose.LID_50,
            EyePose.LID_94,
            EyePose.CLOSED,
            EyePose.LID_94,
            EyePose.LID_50,
        )
        pose_by_frame = {
            count // 2 - 2 + offset: pose
            for offset, pose in enumerate(sequence)
        }
        return [
            poses[pose_by_frame.get(index, EyePose.OPEN)].copy()
            for index in range(count)
        ]
    return [poses[pose_at(index / config.fps)].copy() for index in range(count)]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    core.add_common_cli(parser, "blink")
    args = parser.parse_args()
    started = time.perf_counter()
    config = core.config_for(args.mode)
    source = core.load_master(args.source)
    frames = render(source, config)
    output = core.resolve_output(args)
    core.save_webp(frames, output, config)
    info = core.validate_webp(output, source.size, len(frames) * config.frame_duration_ms)
    if config.full_validation:
        outside = ~core.bbox_mask(source.size, core.VALIDATION_EYE_BBOX)
        reference = np.asarray(source)
        assert all(np.array_equal(np.asarray(frame)[outside], reference[outside]) for frame in frames)
        assert np.array_equal(np.asarray(frames[0]), reference)
        assert np.array_equal(np.asarray(frames[-1]), reference)
    print(f"Created {output} in {time.perf_counter() - started:.3f}s: mode={config.mode.value}, timeline_frames={len(frames)}, encoded_frames={info.encoded_frames}, oversampling={config.oversampling}x")


if __name__ == "__main__":
    main()
