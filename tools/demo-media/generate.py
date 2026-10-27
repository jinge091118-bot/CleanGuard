#!/usr/bin/env python3
"""Generate the synthetic demo media used for store screenshots and manual checks.

The images are flat geometric compositions with no photographic content, so a
screenshot taken on a real device cannot reveal anything private. Sizes vary
deliberately so the app classifies them across the large-file threshold, and
timestamps vary so the "older file" rules have something to work with.

The output is plain PNG and JPEG-free, written with the standard library only,
because the build environment has no imaging packages available.

Usage:
    python3 tools/demo-media/generate.py <output-dir>
"""

from __future__ import annotations

import struct
import sys
import zlib
from pathlib import Path


def write_png(path: Path, width: int, height: int, rows: list[list[tuple[int, int, int]]]) -> None:
    """Write an RGB PNG. Rows are given as top-to-bottom pixel rows."""
    raw = bytearray()
    for row in rows:
        raw.append(0)  # filter type 0
        for red, green, blue in row:
            raw += bytes((red, green, blue))

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


def build_rows(width: int, height: int, base: tuple[int, int, int], accent: tuple[int, int, int], band: int, seed: int) -> list[list[tuple[int, int, int]]]:
    """Compose diagonal bands with a lighter accent stripe.

    A small deterministic noise term keeps the pixels from compressing to
    nothing, so the file sizes land where the plan intends them to.
    """
    rows: list[list[tuple[int, int, int]]] = []
    state = seed * 2654435761 & 0xFFFFFFFF
    for y in range(height):
        row: list[tuple[int, int, int]] = []
        for x in range(width):
            stripe = ((x + y) // band) % 3
            if stripe == 1:
                row.append(accent)
            else:
                state = (state * 1103515245 + 12345) & 0x7FFFFFFF
                noise = ((state >> 16) % 17) - 8
                shade = 1.0 - 0.18 * ((x + y) % 7) / 7
                row.append(
                    tuple(
                        max(0, min(255, int(channel * shade) + noise))
                        for channel in base
                    )
                )
        rows.append(row)
    return rows


def make_image(path: Path, width: int, height: int, base: tuple[int, int, int], accent: tuple[int, int, int], band: int, seed: int) -> None:
    write_png(path, width, height, build_rows(width, height, base, accent, band, seed))


PALETTE = [
    ((74, 144, 217), (196, 222, 244)),
    ((123, 160, 91), (222, 236, 202)),
    ((217, 119, 87), (247, 216, 205)),
    ((139, 111, 181), (223, 214, 241)),
    ((217, 164, 65), (246, 233, 197)),
    ((90, 158, 158), (203, 233, 233)),
    ((122, 122, 138), (222, 222, 230)),
    ((168, 118, 92), (240, 219, 207)),
]


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: generate.py <output-dir>", file=sys.stderr)
        return 2

    out = Path(sys.argv[1])
    images = out / "images"
    duplicates = out / "duplicates"
    images.mkdir(parents=True, exist_ok=True)
    duplicates.mkdir(parents=True, exist_ok=True)

    # Sizes in kilobytes span the app's large-file threshold so the results
    # screen shows a realistic mix.
    size_plan = [
        (240, 180), (240, 180), (240, 180),
        (900, 640), (900, 640), (900, 640),
        (2100, 1400), (2100, 1400),
        (3400, 2200), (3400, 2200),
        (180, 140), (180, 140),
        (620, 480), (620, 480),
        (1500, 1000), (1500, 1000),
        (1100, 760), (1100, 760),
        (760, 540), (760, 540),
        (2800, 1900), (2800, 1900),
        (430, 320), (430, 320),
    ]

    for index, (width, height) in enumerate(size_plan, start=1):
        base, accent = PALETTE[(index - 1) % len(PALETTE)]
        band = 24 + (index % 5) * 16
        make_image(images / f"demo_{index:02d}.png", width, height, base, accent, band, index)

    # Three byte-identical copies form one duplicate group, which is what the
    # duplicate screen needs in order to show a confirmed group.
    duplicate_source = images / "demo_01.png"
    if duplicate_source.exists():
        payload = duplicate_source.read_bytes()
        for copy_index in (1, 2, 3):
            (duplicates / f"demo_copy_{copy_index:02d}.png").write_bytes(payload)

    total = sum(1 for _ in out.rglob("*.png"))
    print(f"输出目录: {out}")
    print(f"生成 PNG: {total} 个")
    print(f"演示图片: {len(list(images.glob('*.png')))} 个")
    print(f"重复文件: {len(list(duplicates.glob('*.png')))} 个（同内容，用于重复文件页）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
