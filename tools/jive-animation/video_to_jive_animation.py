#!/usr/bin/env python3
"""Convert an AI-generated Jive MP4 into transparent PNG frames and WebP."""

from __future__ import annotations

import argparse
import json
import math
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence

import cv2
import numpy as np
from PIL import Image


ROOT = Path(__file__).resolve().parent
DEFAULT_OUTPUT = ROOT / "output"
ANCHOR_NAMES = (
    "head",
    "eyes",
    "body",
    "left_hand",
    "right_hand",
    "left_foot",
    "right_foot",
)


@dataclass(frozen=True)
class VideoInfo:
    width: int
    height: int
    fps: float
    original_frames: int

    @property
    def duration_seconds(self) -> float:
        return self.original_frames / self.fps


@dataclass(frozen=True)
class ProcessedFrame:
    image: Image.Image
    background_mask: np.ndarray
    shadow_mask: np.ndarray
    body_mask: np.ndarray


def parse_bool(value: str) -> bool:
    normalized = value.strip().lower()
    if normalized in {"true", "1", "yes", "on"}:
        return True
    if normalized in {"false", "0", "no", "off"}:
        return False
    raise argparse.ArgumentTypeError("expected true or false")


def parse_hex_color(value: str) -> tuple[int, int, int]:
    if not re.fullmatch(r"#[0-9a-fA-F]{6}", value):
        raise argparse.ArgumentTypeError("expected a color in #RRGGBB format")
    return tuple(int(value[index : index + 2], 16) for index in (1, 3, 5))


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Extract an AI-generated Jive MP4, remove only edge-connected white "
            "background plus conservative lower-body holes and shadow, and write "
            "transparent PNG frames, body masks, metadata, and an animated WebP."
        )
    )
    parser.add_argument("input", type=Path, help="Source MP4 file.")
    parser.add_argument(
        "--every-nth-frame",
        type=int,
        default=1,
        metavar="N",
        help="Export every Nth frame and preserve the original duration (default: 1).",
    )
    parser.add_argument(
        "--background-threshold",
        type=int,
        default=245,
        metavar="0-255",
        help="Minimum channel value treated as near-white background (default: 245).",
    )
    parser.add_argument(
        "--remove-shadow",
        type=parse_bool,
        default=True,
        metavar="true|false",
        help="Remove a confidently detected pale-purple floor shadow (default: true).",
    )
    parser.add_argument(
        "--debug",
        action="store_true",
        help="Write per-frame background, shadow, and body masks to debug/.",
    )
    preview_group = parser.add_mutually_exclusive_group()
    preview_group.add_argument(
        "--preview",
        dest="preview",
        action="store_true",
        help="Write preview.png (enabled by default).",
    )
    preview_group.add_argument(
        "--no-preview",
        dest="preview",
        action="store_false",
        help="Do not write the checkerboard preview.",
    )
    parser.set_defaults(preview=True)
    parser.add_argument(
        "--body-color",
        type=parse_hex_color,
        metavar="#RRGGBB",
        help="Recolor only the detected lavender body while preserving luminance.",
    )
    parser.add_argument(
        "--anchors",
        type=Path,
        help="Optional JSON file with manual anchor points reused for every frame.",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        help="Override the default output/<animation-name>/ directory.",
    )
    args = parser.parse_args(argv)
    if args.every_nth_frame < 1:
        parser.error("--every-nth-frame must be at least 1")
    if not 1 <= args.background_threshold <= 255:
        parser.error("--background-threshold must be between 1 and 255")
    return args


def connected_to_edge(binary: np.ndarray) -> np.ndarray:
    count, labels = cv2.connectedComponents(binary.astype(np.uint8), connectivity=8)
    if count <= 1:
        return np.zeros_like(binary, dtype=bool)
    edge_labels = np.unique(
        np.concatenate((labels[0], labels[-1], labels[:, 0], labels[:, -1]))
    )
    edge_labels = edge_labels[edge_labels != 0]
    return np.isin(labels, edge_labels)


def white_candidates(rgb: np.ndarray, threshold: int) -> tuple[np.ndarray, np.ndarray]:
    minimum = rgb.min(axis=2).astype(np.int16)
    maximum = rgb.max(axis=2).astype(np.int16)
    chroma = maximum - minimum
    hard = (minimum >= threshold) & (chroma <= 18)

    # The soft band is used only immediately beside confirmed background. It
    # retains anti-aliased hairs and outlines without opening a global chroma key.
    soft_floor = max(80, threshold - 125)
    allowed_chroma = 12.0 + (255.0 - minimum) * 0.24
    soft = (minimum >= soft_floor) & (chroma <= allowed_chroma)
    return hard, soft


def expand_into_fringe(core: np.ndarray, soft: np.ndarray, pixels: int = 2) -> np.ndarray:
    kernel = np.ones((3, 3), dtype=np.uint8)
    nearby = cv2.dilate(core.astype(np.uint8), kernel, iterations=pixels).astype(bool)
    return core | (soft & nearby)


def lower_enclosed_background(
    hard_white: np.ndarray,
    soft_white: np.ndarray,
    exterior: np.ndarray,
    provisional_alpha: np.ndarray,
) -> np.ndarray:
    height, width = hard_white.shape
    visible_y, visible_x = np.nonzero(provisional_alpha >= 128)
    if visible_y.size == 0:
        return np.zeros_like(hard_white, dtype=bool)

    top, bottom = int(visible_y.min()), int(visible_y.max())
    lower_start = max(int(height * 0.52), top + int((bottom - top + 1) * 0.56))
    interior = hard_white & ~exterior
    count, labels, stats, centroids = cv2.connectedComponentsWithStats(
        interior.astype(np.uint8), connectivity=8
    )
    accepted = np.zeros_like(interior, dtype=bool)
    minimum_area = max(12, int(height * width * 0.00002))
    for label in range(1, count):
        x, y, component_width, component_height, area = stats[label]
        center_y = centroids[label][1]
        if (
            area >= minimum_area
            and center_y >= lower_start
            and y + component_height - 1 >= top + int((bottom - top + 1) * 0.68)
            and x > 0
            and x + component_width < width
        ):
            accepted |= labels == label
    return expand_into_fringe(accepted, soft_white, pixels=2)


def opacity_for_white_matte(rgb: np.ndarray, selected: np.ndarray, threshold: int) -> np.ndarray:
    minimum = rgb.min(axis=2).astype(np.float32)
    maximum = rgb.max(axis=2).astype(np.float32)
    chroma = maximum - minimum
    soft_floor = float(max(80, threshold - 125))
    darkness_opacity = np.clip((255.0 - minimum) / (255.0 - soft_floor), 0.0, 1.0)
    color_opacity = np.clip(chroma / 48.0, 0.0, 1.0)
    opacity = np.maximum(darkness_opacity, color_opacity)
    opacity[minimum >= threshold] = 0.0
    result = np.ones(selected.shape, dtype=np.float32)
    result[selected] = opacity[selected]
    return result


def detect_shadow(rgb: np.ndarray, alpha: np.ndarray) -> np.ndarray:
    height, width = alpha.shape
    hsv = cv2.cvtColor(rgb, cv2.COLOR_RGB2HSV)
    hue = hsv[:, :, 0]
    saturation = hsv[:, :, 1]
    value = hsv[:, :, 2]
    yy = np.indices(alpha.shape)[0]

    # Lavender in OpenCV HSV is normally around 125-165. Nearly neutral pale
    # pixels are allowed too, but only within a low, wide component.
    purple_or_neutral = ((hue >= 118) & (hue <= 172)) | (saturation <= 18)
    candidates = (
        (yy >= int(height * 0.68))
        & (alpha >= 20)
        & (value >= 125)
        & (saturation <= 105)
        & purple_or_neutral
    )
    count, labels, stats, centroids = cv2.connectedComponentsWithStats(
        candidates.astype(np.uint8), connectivity=8
    )
    shadow = np.zeros_like(candidates, dtype=bool)
    minimum_area = max(24, int(height * width * 0.00012))
    for label in range(1, count):
        x, y, component_width, component_height, area = stats[label]
        center_y = centroids[label][1]
        aspect = component_width / max(1, component_height)
        component = labels == label
        mean_saturation = float(saturation[component].mean())
        if (
            area >= minimum_area
            and component_width >= int(width * 0.12)
            # A breathing/bobbing pose can make the oval touch a few more rows.
            # Keep a small tolerance while the width/aspect/location gates still
            # prevent a leg or foot component from being classified as shadow.
            and component_height <= int(height * 0.18)
            and aspect >= 2.2
            and center_y >= height * 0.77
            and y > height * 0.68
            and mean_saturation <= 85.0
        ):
            shadow |= component
    return shadow


def decontaminate_white_edges(
    rgb: np.ndarray, alpha_float: np.ndarray, matte_pixels: np.ndarray
) -> np.ndarray:
    result = rgb.astype(np.float32).copy()
    partial = matte_pixels & (alpha_float > 0.06) & (alpha_float < 0.98)
    if np.any(partial):
        alpha = alpha_float[partial, None]
        corrected = (result[partial] - 255.0 * (1.0 - alpha)) / alpha
        corrected = np.clip(corrected, 0.0, 255.0)
        # A restrained blend avoids a white fringe while preserving the intended
        # irregular, furry edge instead of smoothing or reshaping it.
        result[partial] = result[partial] * 0.25 + corrected * 0.75
    result[alpha_float <= 0.0] = 0.0
    return np.rint(result).astype(np.uint8)


def eye_exclusion_mask(rgb: np.ndarray, alpha: np.ndarray) -> np.ndarray:
    """Locate large enclosed eye whites without treating every white detail as an eye."""
    height, width = alpha.shape
    hsv = cv2.cvtColor(rgb, cv2.COLOR_RGB2HSV)
    white = (
        (alpha >= 128)
        & (hsv[:, :, 1] <= 65)
        & (hsv[:, :, 2] >= 185)
    )
    count, labels, stats, centroids = cv2.connectedComponentsWithStats(
        white.astype(np.uint8), connectivity=8
    )
    candidates: list[tuple[int, int]] = []
    minimum_area = max(80, int(height * width * 0.001))
    for label in range(1, count):
        x, y, component_width, component_height, area = stats[label]
        center_y = centroids[label][1]
        if (
            area >= minimum_area
            and center_y < height * 0.62
            and component_width <= width * 0.28
            and component_height <= height * 0.28
        ):
            candidates.append((area, label))
    eye_whites = np.zeros_like(white, dtype=bool)
    for _, label in sorted(candidates, reverse=True)[:2]:
        eye_whites |= labels == label
    if not np.any(eye_whites):
        return eye_whites
    contours, _ = cv2.findContours(
        eye_whites.astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE
    )
    filled_eyes = np.zeros_like(eye_whites, dtype=np.uint8)
    cv2.drawContours(filled_eyes, contours, -1, 1, thickness=cv2.FILLED)
    radius = max(3, round(min(width, height) * 0.020))
    kernel = cv2.getStructuringElement(
        cv2.MORPH_ELLIPSE, (radius * 2 + 1, radius * 2 + 1)
    )
    return cv2.dilate(filled_eyes, kernel).astype(bool)


def create_body_mask(
    rgb: np.ndarray,
    alpha: np.ndarray,
    reference_eye_exclusion: np.ndarray | None = None,
) -> np.ndarray:
    """Return a conservative mask of lavender fur, excluding facial details."""
    height, width = alpha.shape
    hsv = cv2.cvtColor(rgb, cv2.COLOR_RGB2HSV)
    hue, saturation, value = (hsv[:, :, index] for index in range(3))
    lavender = (
        (alpha >= 24)
        & (hue >= 120)
        & (hue <= 169)
        & (saturation >= 18)
        & (value >= 90)
    )
    eye_exclusion = (
        eye_exclusion_mask(rgb, alpha)
        if reference_eye_exclusion is None
        else reference_eye_exclusion
    )
    if eye_exclusion.shape != alpha.shape:
        raise ValueError("Reference eye exclusion mask has an unexpected size")
    lavender &= ~eye_exclusion

    # Reject tiny compression specks while retaining separately outlined hands,
    # feet, and furry tufts as independent legitimate body components.
    count, labels, stats, _ = cv2.connectedComponentsWithStats(
        lavender.astype(np.uint8), connectivity=8
    )
    body = np.zeros_like(lavender, dtype=bool)
    minimum_area = max(16, int(height * width * 0.00004))
    for label in range(1, count):
        if stats[label, cv2.CC_STAT_AREA] >= minimum_area:
            body |= labels == label
    return body


def recolor_body(
    rgb: np.ndarray,
    body_mask: np.ndarray,
    target_rgb: tuple[int, int, int] | None,
) -> np.ndarray:
    if target_rgb is None or not np.any(body_mask):
        return rgb

    lab = cv2.cvtColor(rgb, cv2.COLOR_RGB2LAB).astype(np.float32)
    target = np.asarray(target_rgb, dtype=np.uint8).reshape(1, 1, 3)
    target_lab = cv2.cvtColor(target, cv2.COLOR_RGB2LAB)[0, 0].astype(np.float32)
    source_ab = lab[:, :, 1:3][body_mask]
    source_center = np.median(source_ab, axis=0)
    source_delta = source_ab - 128.0
    source_chroma = np.linalg.norm(source_delta, axis=1)
    median_chroma = max(1.0, float(np.median(source_chroma)))
    local_scale = np.clip(source_chroma / median_chroma, 0.62, 1.38)[:, None]
    target_delta = target_lab[1:3] - 128.0

    # L stays untouched. The target chroma direction changes the color, while
    # local chroma and a restrained source variation preserve texture/shading.
    recolored_ab = (
        128.0
        + target_delta[None, :] * local_scale
        + (source_ab - source_center[None, :]) * 0.18
    )
    lab[:, :, 1:3][body_mask] = np.clip(recolored_ab, 0.0, 255.0)
    converted = cv2.cvtColor(np.rint(lab).astype(np.uint8), cv2.COLOR_LAB2RGB)
    result = rgb.copy()
    result[body_mask] = converted[body_mask]
    return result


def process_frame(
    rgb: np.ndarray,
    background_threshold: int,
    remove_shadow: bool,
    body_color: tuple[int, int, int] | None = None,
    reference_eye_exclusion: np.ndarray | None = None,
) -> ProcessedFrame:
    hard_white, soft_white = white_candidates(rgb, background_threshold)
    exterior_core = connected_to_edge(hard_white)
    exterior = expand_into_fringe(exterior_core, soft_white)
    exterior_opacity = opacity_for_white_matte(rgb, exterior, background_threshold)
    provisional_alpha = np.rint(exterior_opacity * 255.0).astype(np.uint8)

    lower_holes = lower_enclosed_background(
        hard_white, soft_white, exterior, provisional_alpha
    )
    background_mask = exterior | lower_holes
    alpha_float = opacity_for_white_matte(rgb, background_mask, background_threshold)
    alpha = np.rint(alpha_float * 255.0).astype(np.uint8)

    shadow_mask = detect_shadow(rgb, alpha) if remove_shadow else np.zeros_like(alpha, dtype=bool)
    if np.any(shadow_mask):
        alpha_float[shadow_mask] = 0.0
        alpha[shadow_mask] = 0

    cleaned_rgb = decontaminate_white_edges(rgb, alpha_float, background_mask)
    body_mask = create_body_mask(cleaned_rgb, alpha, reference_eye_exclusion)
    final_rgb = recolor_body(cleaned_rgb, body_mask, body_color)
    final_rgb[alpha == 0] = 0
    rgba = np.dstack((final_rgb, alpha))
    return ProcessedFrame(
        Image.fromarray(rgba, "RGBA"), background_mask, shadow_mask, body_mask
    )


def frame_durations_ms(original_frames: int, fps: float, step: int) -> list[int]:
    starts = list(range(0, original_frames, step))
    boundaries = starts + [original_frames]
    timestamps = [round(frame / fps * 1000.0) for frame in boundaries]
    durations = [timestamps[index + 1] - timestamps[index] for index in range(len(starts))]
    if any(duration <= 0 for duration in durations):
        raise ValueError("Frame duration rounded below 1 ms; source FPS is unsupported")
    return durations


def checkerboard_preview(image: Image.Image, tile: int = 16) -> Image.Image:
    width, height = image.size
    yy, xx = np.indices((height, width))
    pattern = ((xx // tile + yy // tile) % 2).astype(np.uint8)
    light = np.full((height, width, 3), 232, dtype=np.uint8)
    dark = np.full((height, width, 3), 190, dtype=np.uint8)
    background = np.where(pattern[:, :, None] == 0, light, dark)
    rgba = np.asarray(image.convert("RGBA"), dtype=np.float32)
    alpha = rgba[:, :, 3:4] / 255.0
    composite = rgba[:, :, :3] * alpha + background.astype(np.float32) * (1.0 - alpha)
    return Image.fromarray(np.rint(composite).astype(np.uint8), "RGB")


def save_mask(mask: np.ndarray, path: Path) -> None:
    Image.fromarray(mask.astype(np.uint8) * 255, "L").save(path)


def animation_name_from_stem(stem: str) -> str:
    for suffix in ("-source", "_source"):
        if stem.lower().endswith(suffix):
            return stem[: -len(suffix)]
    return stem


def load_anchors(
    path: Path | None, width: int, height: int
) -> dict[str, list[int | float]]:
    if path is None:
        return {}
    resolved = path.expanduser().resolve()
    if not resolved.is_file():
        raise FileNotFoundError(f"Anchor file not found: {resolved}")
    try:
        document = json.loads(resolved.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise ValueError(f"Invalid anchor JSON: {error}") from error
    raw = document.get("anchors", document) if isinstance(document, dict) else None
    if not isinstance(raw, dict):
        raise ValueError("Anchor JSON must be an object or contain an 'anchors' object")
    missing = [name for name in ANCHOR_NAMES if name not in raw]
    unknown = [name for name in raw if name not in ANCHOR_NAMES]
    if missing:
        raise ValueError(f"Anchor JSON is missing: {', '.join(missing)}")
    if unknown:
        raise ValueError(f"Anchor JSON contains unknown anchors: {', '.join(unknown)}")

    anchors: dict[str, list[int | float]] = {}
    for name in ANCHOR_NAMES:
        point = raw[name]
        if (
            not isinstance(point, list)
            or len(point) != 2
            or any(isinstance(value, bool) or not isinstance(value, (int, float)) for value in point)
        ):
            raise ValueError(f"Anchor '{name}' must be a two-number JSON array")
        x, y = float(point[0]), float(point[1])
        if not (math.isfinite(x) and math.isfinite(y)):
            raise ValueError(f"Anchor '{name}' must contain finite coordinates")
        if not (0 <= x < width and 0 <= y < height):
            raise ValueError(
                f"Anchor '{name}' ({x:g}, {y:g}) is outside {width}x{height}"
            )
        anchors[name] = [int(x) if x.is_integer() else x, int(y) if y.is_integer() else y]
    return anchors


def write_metadata(
    output_dir: Path,
    animation_name: str,
    input_path: Path,
    info: VideoInfo,
    step: int,
    durations: Sequence[int],
    anchors: dict[str, list[int | float]],
    body_color: tuple[int, int, int] | None,
) -> Path:
    source_indices = list(range(0, info.original_frames, step))
    frames = [
        {
            "index": index,
            "sourceFrame": source_index,
            "durationMs": durations[index],
            "anchors": anchors.copy(),
        }
        for index, source_index in enumerate(source_indices)
    ]
    metadata = {
        "schemaVersion": 1,
        "animation": animation_name,
        "source": input_path.name,
        "width": info.width,
        "height": info.height,
        "originalFps": info.fps,
        "fps": info.fps / step,
        "durationMs": sum(durations),
        "bodyColor": (
            None
            if body_color is None
            else "#{:02X}{:02X}{:02X}".format(*body_color)
        ),
        "anchorMode": "manual-reused" if anchors else "none",
        "frames": frames,
    }
    path = output_dir / "metadata.json"
    path.write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    return path


def read_and_process(
    input_path: Path,
    step: int,
    threshold: int,
    remove_shadow: bool,
    body_color: tuple[int, int, int] | None,
) -> tuple[VideoInfo, list[ProcessedFrame]]:
    capture = cv2.VideoCapture(str(input_path))
    if not capture.isOpened():
        raise ValueError(f"Could not open input video: {input_path}")
    fps = float(capture.get(cv2.CAP_PROP_FPS))
    if not math.isfinite(fps) or fps <= 0:
        capture.release()
        raise ValueError("Input video does not report a valid FPS")

    # Establish one stable eye region for the current mostly-static Idle. This
    # keeps closed eyelids out of the body mask during blink frames. Future
    # animations can replace this fixed reference with explicit tracking.
    reference_eye_exclusion: np.ndarray | None = None
    scan_index = 0
    while True:
        ok, bgr = capture.read()
        if not ok:
            break
        if scan_index % step == 0:
            scan_rgb = cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB)
            opaque = np.full(scan_rgb.shape[:2], 255, dtype=np.uint8)
            detected = eye_exclusion_mask(scan_rgb, opaque)
            if np.any(detected):
                reference_eye_exclusion = detected
                break
        scan_index += 1
    if not capture.set(cv2.CAP_PROP_POS_FRAMES, 0):
        capture.release()
        raise ValueError("Could not rewind input video after eye-region scan")
    if reference_eye_exclusion is None:
        print(
            "WARNING: no stable eye region was detected; body masks will use "
            "per-frame color segmentation",
            file=sys.stderr,
        )

    processed: list[ProcessedFrame] = []
    original_frames = 0
    width = height = 0
    while True:
        ok, bgr = capture.read()
        if not ok:
            break
        if original_frames == 0:
            height, width = bgr.shape[:2]
        elif bgr.shape[:2] != (height, width):
            capture.release()
            raise ValueError("Input video changes resolution between frames")
        if original_frames % step == 0:
            rgb = cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB)
            frame = process_frame(
                rgb,
                threshold,
                remove_shadow,
                body_color,
                reference_eye_exclusion,
            )
            processed.append(frame)
        original_frames += 1
    capture.release()
    if original_frames == 0 or not processed:
        raise ValueError("Input video contains no decodable frames")
    return VideoInfo(width, height, fps, original_frames), processed


def warn_about_jitter(frames: Sequence[ProcessedFrame], width: int, height: int) -> None:
    centers: list[tuple[float, float]] = []
    for frame in frames:
        alpha = np.asarray(frame.image.getchannel("A"))
        yy, xx = np.nonzero(alpha >= 128)
        if yy.size:
            weights = alpha[yy, xx].astype(np.float64)
            centers.append((float(np.average(xx, weights=weights)), float(np.average(yy, weights=weights))))
    warning_threshold = max(8.0, min(width, height) * 0.02)
    for index, (previous, current) in enumerate(zip(centers, centers[1:]), start=1):
        delta = math.hypot(current[0] - previous[0], current[1] - previous[1])
        if delta > warning_threshold:
            print(f"WARNING: silhouette moved {delta:.1f}px between exported frames {index - 1} and {index}", file=sys.stderr)


def save_outputs(
    output_dir: Path,
    animation_name: str,
    frames: Sequence[ProcessedFrame],
    durations: Sequence[int],
    debug: bool,
    preview: bool,
) -> Path:
    frames_dir = output_dir / "frames"
    body_masks_dir = output_dir / "masks" / "body"
    debug_dir = output_dir / "debug"
    frames_dir.mkdir(parents=True, exist_ok=True)
    body_masks_dir.mkdir(parents=True, exist_ok=True)
    if debug:
        debug_dir.mkdir(parents=True, exist_ok=True)

    # A repeated run with a larger sampling step must not leave stale frames or
    # masks from an earlier, denser export in the same deterministic directory.
    for stale in frames_dir.glob("frame_*.png"):
        stale.unlink()
    for stale in body_masks_dir.glob("frame_*.png"):
        stale.unlink()
    if debug_dir.is_dir():
        for stale in debug_dir.glob("frame_*_background_mask.png"):
            stale.unlink()
        for stale in debug_dir.glob("frame_*_shadow_mask.png"):
            stale.unlink()
        for stale in debug_dir.glob("frame_*_body_mask.png"):
            stale.unlink()
    preview_path = output_dir / "preview.png"
    if preview_path.is_file() and not preview:
        preview_path.unlink()

    for index, frame in enumerate(frames):
        name = f"frame_{index:04d}"
        frame.image.save(frames_dir / f"{name}.png", format="PNG")
        save_mask(frame.body_mask, body_masks_dir / f"{name}.png")
        if debug:
            save_mask(frame.background_mask, debug_dir / f"{name}_background_mask.png")
            save_mask(frame.shadow_mask, debug_dir / f"{name}_shadow_mask.png")
            save_mask(frame.body_mask, debug_dir / f"{name}_body_mask.png")
    if preview:
        checkerboard_preview(frames[0].image).save(preview_path)

    webp_path = output_dir / f"{animation_name}.webp"
    frames[0].image.save(
        webp_path,
        format="WEBP",
        save_all=True,
        append_images=[frame.image for frame in frames[1:]],
        duration=list(durations),
        loop=0,
        lossless=False,
        quality=92,
        alpha_quality=100,
        method=4,
        minimize_size=True,
        exact=True,
    )
    return webp_path


def validate_outputs(
    frames: Sequence[ProcessedFrame],
    expected_size: tuple[int, int],
    webp_path: Path,
) -> tuple[int, int, int]:
    transparent_frames = 0
    for index, frame in enumerate(frames):
        if frame.image.mode != "RGBA" or "A" not in frame.image.getbands():
            raise AssertionError(f"Frame {index} does not have an alpha channel")
        if frame.image.size != expected_size:
            raise AssertionError(f"Frame {index} has size {frame.image.size}, expected {expected_size}")
        if np.any(np.asarray(frame.image.getchannel("A")) < 255):
            transparent_frames += 1
        if frame.body_mask.shape != (expected_size[1], expected_size[0]):
            raise AssertionError(f"Body mask {index} has an unexpected size")
        if frame.body_mask.dtype != np.bool_:
            raise AssertionError(f"Body mask {index} is not binary")
        if not np.any(frame.body_mask):
            raise AssertionError(f"Body mask {index} is empty")

    body_mask_files = sorted((webp_path.parent / "masks" / "body").glob("frame_*.png"))
    if len(body_mask_files) != len(frames):
        raise AssertionError(
            f"Found {len(body_mask_files)} body masks; expected {len(frames)}"
        )
    for index, path in enumerate(body_mask_files):
        with Image.open(path) as mask:
            if mask.mode != "L" or mask.size != expected_size:
                raise AssertionError(f"Saved body mask {index} is not a full-size L image")
            values = np.unique(np.asarray(mask))
            if not set(values.tolist()).issubset({0, 255}):
                raise AssertionError(f"Saved body mask {index} is not black and white")

    with Image.open(webp_path) as animation:
        if animation.format != "WEBP" or not animation.is_animated:
            raise AssertionError("Output is not an animated WebP")
        if animation.size != expected_size:
            raise AssertionError("Animated WebP has an unexpected size")
        if animation.n_frames != len(frames):
            raise AssertionError(
                f"Animated WebP contains {animation.n_frames} frames; expected {len(frames)}"
            )
        if animation.info.get("loop") != 0:
            raise AssertionError("Animated WebP is not configured to loop infinitely")
        decoded_duration = 0
        for index in range(animation.n_frames):
            animation.seek(index)
            animation.load()
            decoded_alpha = np.asarray(animation.convert("RGBA").getchannel("A"))
            if not np.any(decoded_alpha < 255):
                raise AssertionError(
                    f"Animated WebP frame {index} does not retain transparency"
                )
            decoded_duration += int(animation.info.get("duration", 0))
    return transparent_frames, decoded_duration, len(body_mask_files)


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(argv)
    input_path = args.input.expanduser().resolve()
    if not input_path.is_file():
        raise FileNotFoundError(f"Input video not found: {input_path}")
    if input_path.suffix.lower() != ".mp4":
        raise ValueError("Input must be an MP4 file")

    animation_name = animation_name_from_stem(input_path.stem)
    output_dir = (
        args.output_dir.expanduser().resolve()
        if args.output_dir
        else DEFAULT_OUTPUT / animation_name
    )
    info, frames = read_and_process(
        input_path,
        args.every_nth_frame,
        args.background_threshold,
        args.remove_shadow,
        args.body_color,
    )
    anchors = load_anchors(args.anchors, info.width, info.height)
    durations = frame_durations_ms(info.original_frames, info.fps, args.every_nth_frame)
    if len(durations) != len(frames):
        raise AssertionError("Extracted frame count does not match the timing model")
    warn_about_jitter(frames, info.width, info.height)
    webp_path = save_outputs(
        output_dir, animation_name, frames, durations, args.debug, args.preview
    )
    metadata_path = write_metadata(
        output_dir,
        animation_name,
        input_path,
        info,
        args.every_nth_frame,
        durations,
        anchors,
        args.body_color,
    )
    transparent_frames, decoded_duration, body_masks = validate_outputs(
        frames, (info.width, info.height), webp_path
    )
    expected_duration = round(info.duration_seconds * 1000.0)
    if decoded_duration != expected_duration:
        raise AssertionError(
            f"WebP duration is {decoded_duration}ms; expected {expected_duration}ms"
        )

    print(f"Input: {input_path}")
    print(f"Resolution: {info.width}x{info.height}")
    print(f"Original FPS: {info.fps:.6g}")
    print(f"Output FPS: {info.fps / args.every_nth_frame:.6g}")
    print(f"Original frames: {info.original_frames}")
    print(f"Exported frames: {len(frames)}")
    print(f"Duration: {decoded_duration / 1000.0:.3f}s")
    print(f"Transparent frames: {transparent_frames}/{len(frames)}")
    print(f"Body masks: {body_masks}/{len(frames)}")
    print(f"Metadata: {metadata_path}")
    print(f"Output WebP: {webp_path}")
    print(f"WebP file size: {webp_path.stat().st_size} bytes")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, FileNotFoundError, ValueError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(1)
