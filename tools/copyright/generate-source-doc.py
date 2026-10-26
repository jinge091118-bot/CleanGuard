#!/usr/bin/env python3
"""Generate the source-code document required for a Chinese software copyright filing.

The China Copyright Protection Center requires a source code document that
contains the first 30 and the last 30 pages of the program, with no fewer
than 50 lines of code per page. Page headers must carry the software name and
version, and page numbers belong in the top right corner.

The output is a PDF with a fixed page layout, so the exact page and line counts
can be verified before filing. The document deliberately excludes test code,
build scripts, and generated sources.

Usage:
    python3 tools/copyright/generate-source-doc.py [--name "CleanGuard"] [--version "V0.2.0"]
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
SOURCE_ROOT = PROJECT_ROOT / "app" / "src" / "main" / "java"

# Lines matching these patterns are treated as blank for the 50-per-page count,
# because the filing guidance asks for blank lines and redundant comments to be
# removed.
BLANK_OR_COMMENT = re.compile(r"^\s*(//.*)?$")

LINES_PER_PAGE = 50
PAGES_EACH_END = 30
HEADER_TEXT = "CleanGuard V0.2.0"

# Files are collected in a stable order so the document is reproducible.
SKIP_PATTERNS = (
    "/fake/",
    "/BuildConfig",
)


def collect_source_files(root: Path) -> list[Path]:
    files = sorted(
        path for path in root.rglob("*.kt")
        if not any(skip in path.as_posix() for skip in SKIP_PATTERNS)
    )
    return files


def extract_lines(files: list[Path]) -> list[tuple[str, int, str]]:
    """Return (relative path, line number, text) for every non-blank source line."""
    entries: list[tuple[str, int, str]] = []
    for path in files:
        relative = path.relative_to(PROJECT_ROOT).as_posix()
        try:
            content = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        for number, line in enumerate(content.split("\n"), start=1):
            if BLANK_OR_COMMENT.match(line):
                continue
            entries.append((relative, number, line.rstrip()))
    return entries


def build_pages(entries: list[tuple[str, int, str]]) -> list[list[tuple[str, int, str]]]:
    """Split the extracted lines into pages of at most LINES_PER_PAGE lines."""
    lines_per_page = []
    for start in range(0, len(entries), LINES_PER_PAGE):
        lines_per_page.append(entries[start:start + LINES_PER_PAGE])
    return lines_per_page


def select_pages(pages: list[list[tuple[str, int, str]]]) -> list[list[tuple[str, int, str]]]:
    """Take the first 30 and last 30 pages, as the filing guidance requires."""
    if len(pages) <= PAGES_EACH_END * 2:
        return pages
    return pages[:PAGES_EACH_END] + pages[-PAGES_EACH_END:]


def render_pdf(pages: list[list[tuple[str, int, str]]], header: str, output: Path) -> None:
    """Write the pages as a minimal PDF with an embedded monospace font.

    The PDF is produced without third-party libraries because the build
    environment has no package index access. Each page holds 50 lines of
    source with the header on top and the page number in the top right corner.
    """
    page_width = 595  # A4 at 72 dpi
    page_height = 842
    margin_left = 45
    margin_top = 62
    line_height = 13.2

    # Assemble the PDF structure. Every object goes through add_object so the
    # cross-reference offsets stay correct; writing an object by hand and
    # patching the index afterwards shifts the first entries and produces an
    # unreadable file.
    parts: list[bytes] = [b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n"]
    offsets: dict[int, int] = {}

    def add_object(object_id: int, body: bytes) -> int:
        offsets[object_id] = len(b"".join(parts))
        parts.append(f"{object_id} 0 obj\n".encode("ascii"))
        parts.append(body)
        parts.append(b"\nendobj\n")
        return object_id

    # Object 1: catalog. Object 2: the page tree, written after the pages
    # exist but registered in the offset table before the content streams so
    # the numbering stays stable.
    catalog_id = 1
    pages_id = 2

    content_bodies: list[bytes] = []
    for page in pages:
        content_lines = [
            "BT",
            "/F1 7.2 Tf",
            f"{line_height} TL",
            f"{margin_left} {page_height - margin_top} Td",
        ]
        for index, (path, number, text) in enumerate(page):
            if index:
                content_lines.append("T*")
            escaped = (
                text.replace("\\", r"\\")
                .replace("(", r"\(")
                .replace(")", r"\)")
            )
            content_lines.append(f"({escaped}) Tj")
        content_lines.append("ET")
        code = "\n".join(content_lines).encode("latin-1", errors="replace")

        header_escaped = header.replace("(", r"\(").replace(")", r"\)")
        page_number_escaped = str(len(content_bodies) + 1).replace("(", r"\(")
        header_content = (
            "BT /F1 8 Tf "
            f"{margin_left} {page_height - 40} Td ({header_escaped}) Tj ET\n"
            "BT /F1 8 Tf "
            f"{page_width - margin_left - 20} {page_height - 40} Td ({page_number_escaped}) Tj ET\n"
        ).encode("latin-1", errors="replace")

        content_bodies.append(header_content + code)

    # Reserve the content stream ids, then write everything in order.
    next_id = 3
    content_ids: list[int] = []
    for body in content_bodies:
        content_ids.append(next_id)
        next_id += 1
    font_id = next_id
    next_id += 1
    page_ids: list[int] = []
    for _ in pages:
        page_ids.append(next_id)
        next_id += 1

    add_object(catalog_id, b"<< /Type /Catalog /Pages 2 0 R >>")

    for content_id, body in zip(content_ids, content_bodies):
        add_object(
            content_id,
            b"<< /Length " + str(len(body)).encode("ascii") + b" >>\nstream\n" + body + b"\nendstream",
        )

    add_object(font_id, b"<< /Type /Font /Subtype /Type1 /BaseFont /Courier /Encoding /WinAnsiEncoding >>")

    for page_id, content_id in zip(page_ids, content_ids):
        add_object(
            page_id,
            f"<< /Type /Page /Parent {pages_id} 0 R "
            f"/MediaBox [0 0 {page_width} {page_height}] "
            f"/Resources << /Font << /F1 {font_id} 0 R >> >> "
            f"/Contents {content_id} 0 R >>".encode("ascii"),
        )

    kids = " ".join(f"{page_id} 0 R" for page_id in page_ids)
    add_object(
        pages_id,
        f"<< /Type /Pages /Count {len(page_ids)} /Kids [{kids}] >>".encode("ascii"),
    )

    body = b"".join(parts)
    xref_position = len(body)
    object_count = next_id

    xref_lines = [f"xref\n0 {object_count}\n".encode("ascii"), b"0000000000 65535 f \n"]
    for object_id in range(1, object_count):
        xref_lines.append(f"{offsets[object_id]:010d} 00000 n \n".encode("ascii"))
    xref_lines.append(
        f"trailer\n<< /Size {object_count} /Root {catalog_id} 0 R >>\n"
        f"startxref\n{xref_position}\n%%EOF\n".encode("ascii")
    )

    output.write_bytes(body + b"".join(xref_lines))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", default="CleanGuard", help="Software name printed in the page header")
    parser.add_argument("--version", default="V0.2.0", help="Version printed in the page header")
    parser.add_argument(
        "--output",
        default="docs/release/v0.2/copyright/CleanGuard-source-code.pdf",
        help="Output PDF path",
    )
    args = parser.parse_args()

    if not SOURCE_ROOT.exists():
        print(f"源码目录不存在: {SOURCE_ROOT}", file=sys.stderr)
        return 2

    files = collect_source_files(SOURCE_ROOT)
    entries = extract_lines(files)
    if not entries:
        print("未提取到任何源码行", file=sys.stderr)
        return 1

    pages = build_pages(entries)
    selected = select_pages(pages)

    output = PROJECT_ROOT / args.output
    output.parent.mkdir(parents=True, exist_ok=True)
    render_pdf(selected, f"{args.name} {args.version}", output)

    print(f"源文件数: {len(files)}")
    print(f"有效代码行: {len(entries)}")
    print(f"总页数: {len(pages)}（每页 {LINES_PER_PAGE} 行）")
    print(f"提交页数: {len(selected)}（前 {PAGES_EACH_END} + 后 {PAGES_EACH_END}）")
    print(f"输出: {output}")
    print(f"文件大小: {output.stat().st_size / 1024:.1f} KB")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
