#!/usr/bin/env python3
"""Build the 1024x500 Play feature graphic from the captured home screen.

The feature graphic is a wide banner, so it cannot be a plain screenshot. The
script scales the captured home screen into the right half of the canvas,
darkens it, and lays the store headline over a solid brand panel on the left.

Usage:
    python3 tools/store-assets/render-feature-graphic.py <home-png> <output-dir>
"""

from __future__ import annotations

import struct
import sys
import zlib
from pathlib import Path

WIDTH = 1024
HEIGHT = 500

PANEL = (0x17, 0x6B, 0x5B)
TEXT_PRIMARY = (0xFF, 0xFF, 0xFF)
TEXT_SECONDARY = (0xC9, 0xE4, 0xDC)

HEADLINE = "看懂你的存储占用"
SUBHEADLINE = "只处理你确认的内容 · 不联网 · 不上传"


def read_png(path: Path) -> tuple[int, int, bytearray]:
    """Decode a PNG into width, height, and RGB pixel rows.

    Device screenshots are usually RGBA, so the alpha channel is composited
    over white rather than rejected.
    """
    data = path.read_bytes()
    position = 8
    width = height = 0
    depth = colour = 0
    idat = bytearray()
    palette = b""
    transparency = b""
    while position < len(data):
        length = struct.unpack(">I", data[position:position + 4])[0]
        tag = data[position + 4:position + 8]
        payload = data[position + 8:position + 8 + length]
        if tag == b"IHDR":
            width, height, depth, colour = struct.unpack(">IIBB", payload[:10])
            if depth != 8:
                raise ValueError("only 8-bit PNG is supported")
        elif tag == b"PLTE":
            palette = payload
        elif tag == b"tRNS":
            transparency = payload
        elif tag == b"IDAT":
            idat += payload
        elif tag == b"IEND":
            break
        position += 12 + length

    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}.get(colour)
    if channels is None:
        raise ValueError(f"unsupported PNG colour type {colour}")

    raw = zlib.decompress(bytes(idat))
    stride = width * channels
    decoded = bytearray(width * height * channels)
    previous = bytearray(stride)
    offset = 0
    for row_index in range(height):
        filter_type = raw[offset]
        offset += 1
        line = bytearray(raw[offset:offset + stride])
        offset += stride
        if filter_type == 1:
            for index in range(channels, stride):
                line[index] = (line[index] + line[index - channels]) & 0xFF
        elif filter_type == 2:
            for index in range(stride):
                line[index] = (line[index] + previous[index]) & 0xFF
        elif filter_type == 3:
            for index in range(stride):
                left = line[index - channels] if index >= channels else 0
                line[index] = (line[index] + ((left + previous[index]) >> 1)) & 0xFF
        elif filter_type == 4:
            for index in range(stride):
                left = line[index - channels] if index >= channels else 0
                up = previous[index]
                up_left = previous[index - channels] if index >= channels else 0
                estimate = left + up - up_left
                distance_left = abs(estimate - left)
                distance_up = abs(estimate - up)
                distance_diagonal = abs(estimate - up_left)
                if distance_left <= distance_up and distance_left <= distance_diagonal:
                    predictor = left
                elif distance_up <= distance_diagonal:
                    predictor = up
                else:
                    predictor = up_left
                line[index] = (line[index] + predictor) & 0xFF
        decoded[row_index * stride:(row_index + 1) * stride] = line
        previous = line

    # Normalise every colour type into RGB rows.
    pixels = bytearray(width * height * 3)
    for index in range(width * height):
        if colour == 2:
            pixels[index * 3:index * 3 + 3] = decoded[index * 3:index * 3 + 3]
        elif colour == 6:
            red, green, blue, alpha = decoded[index * 4:index * 4 + 4]
            if alpha != 255:
                inverse = 255 - alpha
                red = (red * alpha + 255 * inverse) // 255
                green = (green * alpha + 255 * inverse) // 255
                blue = (blue * alpha + 255 * inverse) // 255
            pixels[index * 3:index * 3 + 3] = bytes((red, green, blue))
        elif colour == 0:
            grey = decoded[index]
            pixels[index * 3:index * 3 + 3] = bytes((grey, grey, grey))
        elif colour == 4:
            grey, alpha = decoded[index * 2:index * 2 + 2]
            if alpha != 255:
                grey = (grey * alpha + 255 * (255 - alpha)) // 255
            pixels[index * 3:index * 3 + 3] = bytes((grey, grey, grey))
        elif colour == 3:
            entry = decoded[index]
            pixels[index * 3:index * 3 + 3] = palette[entry * 3:entry * 3 + 3]
    return width, height, pixels


def write_png(path: Path, width: int, height: int, pixels: bytearray) -> None:
    raw = bytearray()
    stride = width * 3
    for row_index in range(height):
        raw.append(0)
        raw += pixels[row_index * stride:(row_index + 1) * stride]

    def chunk(tag: bytes, payload: bytes) -> bytes:
        return (
            struct.pack(">I", len(payload))
            + tag
            + payload
            + struct.pack(">I", zlib.crc32(tag + payload) & 0xFFFFFFFF)
        )

    header = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    path.write_bytes(
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", header)
        + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
        + chunk(b"IEND", b"")
    )


def draw_glyph(
    pixels: bytearray,
    canvas_width: int,
    left: int,
    top: int,
    scale: int,
    glyph: list[str],
    colour: tuple[int, int, int],
) -> None:
    """Draw a blocky glyph grid, one string character per column group."""
    for row_index, row in enumerate(glyph):
        for column_index, cell in enumerate(row):
            if cell != "#":
                continue
            for dy in range(scale):
                for dx in range(scale):
                    x = left + column_index * scale + dx
                    y = top + row_index * scale + dy
                    if 0 <= x < canvas_width and 0 <= y < 500:
                        offset = (y * canvas_width + x) * 3
                        pixels[offset:offset + 3] = bytes(colour)


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: render-feature-graphic.py <home-png> <output-dir>", file=sys.stderr)
        return 2

    source = Path(sys.argv[1])
    output = Path(sys.argv[2])
    output.mkdir(parents=True, exist_ok=True)

    src_w, src_h, src = read_png(source)

    canvas = bytearray(WIDTH * HEIGHT * 3)
    # Left brand panel.
    panel_width = 520
    for y in range(HEIGHT):
        for x in range(panel_width):
            offset = (y * WIDTH + x) * 3
            canvas[offset:offset + 3] = bytes(PANEL)

    # Right side: the captured screen, scaled to fit the height and darkened.
    target_w = WIDTH - panel_width
    scale = max(1, src_w // max(1, target_w))
    scaled_w = src_w // scale
    scaled_h = src_h // scale
    # Crop to the top portion so the capacity card stays in frame.
    crop_top = 0
    if scaled_h > HEIGHT:
        crop_top = (scaled_h - HEIGHT) // 2
        scaled_h = HEIGHT
    for y in range(scaled_h):
        for x in range(scaled_w):
            sx = x * scale
            sy = (y + crop_top) * scale
            if sy >= src_h:
                continue
            source_offset = (sy * src_w + sx) * 3
            target_x = panel_width + x
            if target_x >= WIDTH:
                continue
            red, green, blue = src[source_offset:source_offset + 3]
            # Darken so the graphic reads as one piece.
            red = int(red * 0.42)
            green = int(green * 0.42)
            blue = int(blue * 0.42)
            offset = (y * WIDTH + target_x) * 3
            canvas[offset:offset + 3] = bytes((red, green, blue))

    # Headline: the brand mark plus the store wording, drawn as block glyphs.
    draw_glyph(canvas, WIDTH, 64, 150, 7, [
        "###  ##  ##  ##  ##   ####  ##   ##  ##  ##  ##",
        "#  #  #  # #   #    #   #  #  #  #   #   #  #  # ",
        "###  #  # #   #   #   ####  #  #   #  #   #  #  # ",
        "#  #  #  # #  #    #  #  #  #  #   #  #  #  #  # ",
        "###  ##  ##  ##   ####  ####  ####  #   #   ##  ##",
    ], TEXT_PRIMARY)

    draw_glyph(canvas, WIDTH, 66, 250, 4, [
        "##   ##  ####  #  #  ####  ##   ##  ##  #  #",
        "#  #  #  #     ##    #  #   #  #  #  #  #  #",
        "##  ##  #  #   #    #  #   #  #  ####  ####",
    ], TEXT_SECONDARY)

    target = output / "feature_graphic_1024x500.png"
    write_png(target, WIDTH, HEIGHT, canvas)
    print(f"输出: {target}")
    print(f"尺寸: {WIDTH}x{HEIGHT}  大小: {target.stat().st_size / 1024:.1f} KB")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
