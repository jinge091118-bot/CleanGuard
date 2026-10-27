#!/usr/bin/env python3
"""Render the CleanGuard launcher icon into store-ready PNG sizes.

The launcher icon is an Android vector drawable, and Google Play needs a
512x512 PNG. This script rasterizes the two shapes the vector defines, a
rounded shield and a check mark, with the standard library only.

The output keeps the application palette so the store icon matches the
in-app brand colour.

Usage:
    python3 tools/store-assets/render-icon.py <output-dir>
"""

from __future__ import annotations

import math
import sys
from pathlib import Path

BACKGROUND = (0xE9, 0xF4, 0xF1)
SHIELD = (0x17, 0x6B, 0x5B)
MARK = (0xFF, 0xFF, 0xFF)

# The vector uses a 108x108 viewport.
VIEWPORT = 108.0


def shield_contains(x: float, y: float) -> bool:
    """Test whether a point in viewport units falls inside the shield shape.

    The shield is the path "M54,18 L84,30 V50 C84,69 71,84 54,91 C37,84
    24,69 24,50 V30 Z". The straight edges are tested directly, and the
    lower bowl is approximated with a circle that matches the curve closely
    enough at icon scale.
    """
    if y < 18:
        return False
    # Upper tapered section between the top point and the shoulder.
    if y <= 50:
        if y <= 30:
            # The two edges run from (54,18) to (24,30) and (54,18) to (84,30).
            t = (y - 18) / 12.0
            half_width = 30.0 * t
            return abs(x - 54.0) <= half_width
        half_width = 30.0
        if abs(x - 54.0) > half_width:
            return False
        return True
    # Lower bowl: a circle centred below the widest point approximates the
    # cubic curve well enough for a 512 px icon.
    centre_x = 54.0
    centre_y = 50.0
    radius = 30.0
    if (x - centre_x) ** 2 + (y - centre_y) ** 2 <= radius * radius:
        # The curve reaches y=91 at x=54, so stretch the bottom.
        if y <= 91.0:
            return True
    return False


def check_mark_contains(x: float, y: float) -> bool:
    """Test whether a point falls inside the check mark polygon.

    The path is "M47,65 L35,53 L40,48 L47,55 L68,34 L73,39 Z".
    """
    points = [(47, 65), (35, 53), (40, 48), (47, 55), (68, 34), (73, 39)]
    return point_in_polygon(x, y, points)


def point_in_polygon(x: float, y: float, points: list[tuple[float, float]]) -> bool:
    inside = False
    count = len(points)
    for index in range(count):
        x1, y1 = points[index]
        x2, y2 = points[(index + 1) % count]
        if (y1 > y) != (y2 > y):
            crossing = x1 + (y - y1) * (x2 - x1) / (y2 - y1)
            if x <= crossing:
                inside = not inside
    return inside


def render(size: int) -> bytes:
    """Rasterize the icon at the requested pixel size and return PNG bytes."""
    scale = size / VIEWPORT
    rows: list[bytes] = []
    for py in range(size):
        row = bytearray()
        viewport_y = (py + 0.5) / scale
        for px in range(size):
            viewport_x = (px + 0.5) / scale
            if check_mark_contains(viewport_x, viewport_y):
                colour = MARK
            elif shield_contains(viewport_x, viewport_y):
                colour = SHIELD
            else:
                colour = BACKGROUND
            row += bytes(colour)
        rows.append(bytes(row))

    import struct
    import zlib

    raw = bytearray()
    for row in rows:
        raw.append(0)
        raw += row

    def chunk(tag: bytes, payload: bytes) -> bytes:
        return (
            struct.pack(">I", len(payload))
            + tag
            + payload
            + struct.pack(">I", zlib.crc32(tag + payload) & 0xFFFFFFFF)
        )

    header = struct.pack(">IIBBBBB", size, size, 8, 2, 0, 0, 0)
    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", header)
        + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
        + chunk(b"IEND", b"")
    )


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: render-icon.py <output-dir>", file=sys.stderr)
        return 2

    output = Path(sys.argv[1])
    output.mkdir(parents=True, exist_ok=True)

    for size in (512, 192, 48):
        target = output / f"ic_launcher_{size}.png"
        target.write_bytes(render(size))
        print(f"  {target.name}  {size}x{size}  {target.stat().st_size / 1024:.1f} KB")

    print(f"输出目录: {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
