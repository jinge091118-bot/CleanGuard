#!/usr/bin/env python3
"""Render the CleanGuard privacy policy as a standalone public HTML page.

The script is intentionally dependency-free: it only uses the Python standard
library, because the build environment has no package index access. The
Markdown subset it supports is the one the privacy policy actually uses:
ATX headings, paragraphs, unordered and ordered lists, pipe tables, fenced
code blocks, block quotes, and horizontal rules.

Usage:
    python3 tools/privacy-page/render.py <input.md> <output.html>
"""

from __future__ import annotations

import html
import re
import sys
from pathlib import Path

INLINE_CODE = re.compile(r"`([^`]+)`")
BOLD = re.compile(r"\*\*([^*]+)\*\*")
ITALIC = re.compile(r"(?<!\*)\*([^*\n]+)\*(?!\*)")
LINK = re.compile(r"\[([^\]]+)\]\(([^)]+)\)")
BARE_URL = re.compile(r"(?<![<\w])(https?://[^\s<]+)")
EMAIL = re.compile(r"(?<![\w.+-])([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,})(?![\w.-])")


def escape(text: str) -> str:
    return html.escape(text, quote=False)


def render_inline(text: str) -> str:
    """Apply inline emphasis after escaping, so markup is never injected."""
    out = escape(text)
    out = INLINE_CODE.sub(r"<code>\1</code>", out)
    out = BOLD.sub(r"<strong>\1</strong>", out)
    out = ITALIC.sub(r"<em>\1</em>", out)
    out = LINK.sub(r'<a href="\2">\1</a>', out)

    # Auto-linking runs last, so already-linked text is protected with
    # placeholders instead of being wrapped a second time.
    placeholders: list[str] = []

    def protect(fragment: str) -> str:
        placeholders.append(fragment)
        return f"\x00{len(placeholders) - 1}\x00"

    # Angle-bracket URLs first: they read as deliberate references.
    out = re.sub(
        r"&lt;(https?://[^\s&]+)&gt;",
        lambda m: protect(f'<a href="{m.group(1)}">{m.group(1)}</a>'),
        out,
    )
    # Then plain URLs, but not ones already inside an anchor.
    out = re.sub(
        r'(?<!href=")(?<!["\w])(https?://[^\s<>"]+)',
        lambda m: protect(f'<a href="{m.group(1)}">{m.group(1)}</a>'),
        out,
    )
    # Then email addresses.
    out = EMAIL.sub(
        lambda m: protect(f'<a href="mailto:{m.group(1)}">{m.group(1)}</a>'),
        out,
    )

    for index, fragment in enumerate(placeholders):
        out = out.replace(f"\x00{index}\x00", fragment)
    return out


def split_table_row(line: str) -> list[str]:
    stripped = line.strip()
    if stripped.startswith("|"):
        stripped = stripped[1:]
    if stripped.endswith("|"):
        stripped = stripped[:-1]
    return [cell.strip() for cell in stripped.split("|")]


def is_separator_row(line: str) -> bool:
    cells = split_table_row(line)
    return bool(cells) and all(re.fullmatch(r":?-{2,}:?", cell) for cell in cells)


def convert(markdown_text: str) -> str:
    lines = markdown_text.split("\n")
    html_parts: list[str] = []
    index = 0
    total = len(lines)

    while index < total:
        line = lines[index]
        stripped = line.strip()

        if not stripped:
            index += 1
            continue

        # Fenced code block
        if stripped.startswith("```"):
            language = stripped[3:].strip()
            index += 1
            code_lines: list[str] = []
            while index < total and not lines[index].strip().startswith("```"):
                code_lines.append(lines[index])
                index += 1
            index += 1
            class_attr = f' class="language-{escape(language)}"' if language else ""
            html_parts.append(f"<pre><code{class_attr}>{escape(chr(10).join(code_lines))}</code></pre>")
            continue

        # Horizontal rule
        if re.fullmatch(r"-{3,}", stripped):
            html_parts.append("<hr>")
            index += 1
            continue

        # Heading
        heading = re.match(r"^(#{1,6})\s+(.*)$", stripped)
        if heading:
            level = len(heading.group(1))
            content = heading.group(2).strip()
            anchor = re.sub(r"[^\w\u4e00-\u9fff-]+", "-", content.lower()).strip("-")
            html_parts.append(f'<h{level} id="{anchor}">{render_inline(content)}</h{level}>')
            index += 1
            continue

        # Table
        if "|" in stripped and index + 1 < total and is_separator_row(lines[index + 1]):
            header_cells = split_table_row(stripped)
            index += 2
            body_rows: list[list[str]] = []
            while index < total and "|" in lines[index] and lines[index].strip():
                body_rows.append(split_table_row(lines[index]))
                index += 1
            html_parts.append("<div class=\"table-wrap\"><table><thead><tr>")
            for cell in header_cells:
                html_parts.append(f"<th>{render_inline(cell)}</th>")
            html_parts.append("</tr></thead><tbody>")
            for row in body_rows:
                html_parts.append("<tr>")
                for cell in row:
                    html_parts.append(f"<td>{render_inline(cell)}</td>")
                html_parts.append("</tr>")
            html_parts.append("</tbody></table></div>")
            continue

        # Block quote
        if stripped.startswith(">"):
            quote_lines: list[str] = []
            while index < total and lines[index].strip().startswith(">"):
                quote_lines.append(lines[index].strip().lstrip(">").strip())
                index += 1
            html_parts.append(f"<blockquote>{render_inline(' '.join(quote_lines))}</blockquote>")
            continue

        # Unordered list
        if re.match(r"^[-*]\s+", stripped):
            items: list[str] = []
            while index < total and re.match(r"^[-*]\s+", lines[index].strip()):
                items.append(re.sub(r"^[-*]\s+", "", lines[index].strip()))
                index += 1
            html_parts.append("<ul>")
            for item in items:
                html_parts.append(f"<li>{render_inline(item)}</li>")
            html_parts.append("</ul>")
            continue

        # Ordered list
        if re.match(r"^\d+\.\s+", stripped):
            items = []
            while index < total and re.match(r"^\d+\.\s+", lines[index].strip()):
                items.append(re.sub(r"^\d+\.\s+", "", lines[index].strip()))
                index += 1
            html_parts.append("<ol>")
            for item in items:
                html_parts.append(f"<li>{render_inline(item)}</li>")
            html_parts.append("</ol>")
            continue

        # Paragraph
        paragraph: list[str] = []
        while index < total and lines[index].strip():
            candidate = lines[index].strip()
            if re.match(r"^(#{1,6})\s+", candidate) or candidate.startswith("```"):
                break
            if re.match(r"^[-*]\s+", candidate) or re.match(r"^\d+\.\s+", candidate):
                break
            if candidate.startswith(">"):
                break
            paragraph.append(candidate)
            index += 1
        if paragraph:
            # A run of "key: value" lines is a metadata block, not prose. Keep
            # each entry on its own line so it stays readable.
            if all(re.match(r"^\*\*[^*]+\*\*：", line) for line in paragraph):
                for line in paragraph:
                    html_parts.append(f'<p class="meta">{render_inline(line)}</p>')
            else:
                html_parts.append(f"<p>{render_inline(' '.join(paragraph))}</p>")

    return "\n".join(html_parts)


PAGE_TEMPLATE = """<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{title}</title>
<meta name="description" content="{description}">
<meta name="robots" content="index, follow">
<style>
:root {{
  --bg: #ffffff;
  --fg: #1b1b1f;
  --muted: #5b5b66;
  --line: #e3e3e8;
  --accent: #2f5fd0;
  --code-bg: #f4f4f7;
  --quote-bg: #f7f8fb;
}}
@media (prefers-color-scheme: dark) {{
  :root {{
    --bg: #14141a;
    --fg: #e8e8ee;
    --muted: #a5a5b2;
    --line: #2c2c36;
    --accent: #8fb0ff;
    --code-bg: #1e1e26;
    --quote-bg: #1a1a22;
  }}
}}
* {{ box-sizing: border-box; }}
html {{ -webkit-text-size-adjust: 100%; }}
body {{
  margin: 0;
  padding: 2rem 1.25rem 4rem;
  background: var(--bg);
  color: var(--fg);
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC",
    "Hiragino Sans GB", "Microsoft YaHei", "Noto Sans CJK SC", sans-serif;
  font-size: 16px;
  line-height: 1.75;
}}
main {{ max-width: 46rem; margin: 0 auto; }}
header {{ border-bottom: 1px solid var(--line); padding-bottom: 1.25rem; margin-bottom: 2rem; }}
.app-name {{ font-size: 1.5rem; font-weight: 700; margin: 0 0 .35rem; letter-spacing: -.01em; }}
.meta {{ color: var(--muted); font-size: .875rem; margin: 0; }}
h1 {{ font-size: 1.75rem; margin: 0 0 .5rem; letter-spacing: -.015em; }}
h2 {{ font-size: 1.2rem; margin: 2.25rem 0 .75rem; padding-top: .25rem; }}
h3 {{ font-size: 1.02rem; margin: 1.6rem 0 .5rem; }}
p {{ margin: .85rem 0; }}
ul, ol {{ margin: .85rem 0; padding-left: 1.4rem; }}
li {{ margin: .4rem 0; }}
strong {{ font-weight: 650; }}
a {{ color: var(--accent); }}
code {{
  background: var(--code-bg);
  padding: .12em .38em;
  border-radius: 4px;
  font-size: .87em;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}}
pre {{
  background: var(--code-bg);
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 1rem;
  overflow-x: auto;
  line-height: 1.55;
}}
pre code {{ background: none; padding: 0; font-size: .85rem; }}
blockquote {{
  margin: 1rem 0;
  padding: .75rem 1rem;
  background: var(--quote-bg);
  border-left: 3px solid var(--accent);
  border-radius: 0 6px 6px 0;
  color: var(--muted);
}}
hr {{ border: none; border-top: 1px solid var(--line); margin: 2.25rem 0; }}
.table-wrap {{ overflow-x: auto; margin: 1.1rem 0; }}
table {{ border-collapse: collapse; width: 100%; font-size: .93rem; }}
th, td {{
  border: 1px solid var(--line);
  padding: .55rem .7rem;
  text-align: left;
  vertical-align: top;
}}
th {{ background: var(--code-bg); font-weight: 650; }}
footer {{
  margin-top: 3rem;
  padding-top: 1.25rem;
  border-top: 1px solid var(--line);
  color: var(--muted);
  font-size: .875rem;
}}
footer p {{ margin: .4rem 0; }}
@media (max-width: 30rem) {{
  body {{ padding: 1.5rem 1rem 3rem; font-size: 15.5px; }}
  h1 {{ font-size: 1.45rem; }}
  h2 {{ font-size: 1.1rem; margin-top: 1.9rem; }}
}}
</style>
</head>
<body>
<main>
{body}
<footer>
<p>本页面为 CleanGuard V0.2 的公开隐私政策，与应用内「设置 → 隐私说明」及 Google Play 商店页所列内容一致。</p>
<p>政策正文源文件随应用源码一同发布，可在应用仓库的 <code>docs/release/v0.2/PRIVACY-POLICY.md</code> 查看。仓库采用专有许可证，仅供审阅与审计，不授予开源许可。</p>
</footer>
</main>
</body>
</html>
"""


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: render.py <input.md> <output.html>", file=sys.stderr)
        return 2

    source = Path(sys.argv[1])
    target = Path(sys.argv[2])

    markdown_text = source.read_text(encoding="utf-8")
    body = convert(markdown_text)

    title = "CleanGuard 隐私政策"
    description = "CleanGuard 是一款在本机分析存储占用并协助清理的 Android 应用，无网络权限，不上传任何数据。"

    # The header block in the policy source carries the version, effective date,
    # approval state, public URL, and support address. Show those as a distinct
    # block so a reader can confirm the version at a glance.
    body = body.replace(
        "<h1 id=\"cleanguard-隐私政策\">CleanGuard 隐私政策</h1>",
        "<header><p class=\"app-name\">CleanGuard</p>"
        "<h1 id=\"cleanguard-隐私政策\">隐私政策</h1></header>",
        1,
    )
    body = body.replace(
        "<p><strong>公开 URL</strong>：<a href=\"https://jinge091118-bot.github.io/CleanGuard/privacy-policy.html\">",
        "<p class=\"meta\"><strong>公开 URL</strong>：<a href=\"https://jinge091118-bot.github.io/CleanGuard/privacy-policy.html\">",
        1,
    )

    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(
        PAGE_TEMPLATE.format(title=html.escape(title), description=html.escape(description), body=body),
        encoding="utf-8",
    )
    print(f"rendered {source} -> {target} ({len(body)} bytes of body)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
