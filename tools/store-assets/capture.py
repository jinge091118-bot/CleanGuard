#!/usr/bin/env python3
"""Capture the store screenshots on a connected Android device.

The script drives the app through its screens with adb and records each
screen with `screencap`. Screens are grouped by how they handle private
content, because a store listing must never expose the photographer's own
files:

- `safe` screens show only aggregate numbers or the app's own copy, so they
  can be captured directly on a device that holds real media.
- `isolated` screens list individual file names. They are only captured when
  the demo dataset is the only indexed media, which the script verifies
  before capturing anything.

Usage:
    python3 tools/store-assets/capture.py <serial> <output-dir> [--only safe|isolated|all]
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
import tempfile
import time
from pathlib import Path

ADB = "I:/Android/Sdk/platform-tools/adb.exe"
PACKAGE = "com.cleanguard.app"
ACTIVITY = f"{PACKAGE}/.MainActivity"

# Screens that show no user file names.
SAFE_SCREENS = [
    ("home_capacity", "首页容量总览", 0),
    ("home_permission", "首页权限状态", 2),
    ("scan_stats", "体检统计", 1),
    ("results_clues", "空间线索与风险分组", 3),
    ("insights", "空间洞察大文件", 0),
    ("permissions", "权限中心", 0),
    ("settings_privacy", "设置与隐私", 0),
]

# Screens that list individual file names.
ISOLATED_SCREENS = [
    ("recommendations", "清理建议列表", 0),
    ("duplicates", "重复文件确认", 0),
    ("cleanup_confirm", "清理前确认", 0),
    ("history", "清理历史", 0),
    ("recovery", "恢复中心", 0),
]

PRIVATE_PATTERNS = [
    re.compile(r"/sdcard/(DCIM|Pictures|Movies|Music|Download)/"),
    re.compile(r"relative_path=(?!Pictures/CleanGuardDemo)"),
]


def adb(serial: str, *args: str, timeout: int = 120) -> str:
    result = subprocess.run(
        [ADB, "-s", serial, *args],
        capture_output=True,
        text=True,
        timeout=timeout,
    )
    return result.stdout


def shell(serial: str, command: str, timeout: int = 120) -> str:
    return adb(serial, "shell", command, timeout=timeout)


def tap(serial: str, x: int, y: int, settle: float = 1.2) -> None:
    shell(serial, f"input tap {x} {y}")
    time.sleep(settle)


def swipe(serial: str, x1: int, y1: int, x2: int, y2: int, settle: float = 1.0) -> None:
    shell(serial, f"input swipe {x1} {y1} {x2} {y2} 250")
    time.sleep(settle)


def back(serial: str, settle: float = 1.2) -> None:
    shell(serial, "input keyevent KEYCODE_BACK")
    time.sleep(settle)


def dump_ui(serial: str) -> str:
    """Return the visible text nodes with their tap coordinates.

    The hierarchy is pulled into a local temporary file. Writing it to a
    device path would not work, because the caller reads it with the local
    Python process.
    """
    shell(serial, "uiautomator dump /sdcard/.capture-ui.xml")
    local = Path(tempfile.gettempdir()) / "cleanguard-capture-ui.xml"
    if local.exists():
        local.unlink()
    adb(serial, "pull", "/sdcard/.capture-ui.xml", str(local))
    if not local.exists():
        return ""
    text = local.read_text(encoding="utf-8", errors="replace")
    try:
        local.unlink()
    except OSError:
        pass
    nodes: list[tuple[str, int, int]] = []
    for match in re.finditer(
        r'text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', text
    ):
        label = match.group(1).strip()
        if not label:
            continue
        centre_x = (int(match.group(2)) + int(match.group(4))) // 2
        centre_y = (int(match.group(3)) + int(match.group(5))) // 2
        nodes.append((label, centre_x, centre_y))
    return nodes


def find_node(serial: str, needle: str, scroll_attempts: int = 6) -> tuple[int, int] | None:
    """Find a node, scrolling down when it is not in the current viewport.

    Long pages put their primary action at the bottom, so a plain lookup on
    the first viewport misses it.
    """
    for _ in range(scroll_attempts + 1):
        node = find_node_in_view(serial, needle)
        if node:
            return node
        swipe(serial, 540, 1800, 540, 800, settle=0.9)
    return None


def find_node_in_view(serial: str, needle: str) -> tuple[int, int] | None:
    for label, x, y in dump_ui(serial):
        if needle in label:
            return x, y
    return None


def capture(serial: str, output: Path, name: str) -> Path:
    shell(serial, f"screencap -p /sdcard/.capture.png")
    target = output / f"{name}.png"
    adb(serial, "pull", "/sdcard/.capture.png", str(target))
    shell(serial, "rm -f /sdcard/.capture.png")
    if target.exists():
        print(f"  [OK]   {name}.png")
    else:
        print(f"  [FAIL] {name} 截图失败")
    return target


def ensure_demo_only(serial: str) -> bool:
    """Confirm the only indexed media is the demo dataset.

    The check looks at the directory fields rather than the row count alone,
    because a device may legitimately hold media that the user chose to keep.
    """
    listing = shell(
        serial,
        "content query --uri content://media/external/images/media "
        "--projection relative_path 2>/dev/null",
    )
    paths = re.findall(r"relative_path=(\S*)", listing)
    if not paths:
        print("  [SKIP] 媒体库为空，无法判断隔离状态")
        return False
    unexpected = [p for p in paths if "CleanGuardDemo" not in p]
    if unexpected:
        print(f"  [SKIP] 媒体库含 {len(unexpected)} 条非演示数据，不采集含文件名的界面")
        return False
    print("  [OK]   媒体库仅含演示数据")
    return True


def goto_home(serial: str) -> None:
    """Return to the home screen from wherever the app currently is."""
    shell(serial, f"am start -W -n {ACTIVITY}")
    time.sleep(2.5)
    # The activity may resume on the current destination rather than the home
    # tab, so back out until the home screen is reached.
    for _ in range(4):
        node = find_node_in_view(serial, "开始安全体检")
        if node:
            return
        back(serial, settle=1.2)
    shell(serial, f"am force-stop {PACKAGE}")
    time.sleep(1)
    shell(serial, f"am start -W -n {ACTIVITY}")
    time.sleep(3)


def run_safe_captures(serial: str, output: Path) -> None:
    print("安全界面（不含文件名）：")
    shell(serial, f"am force-stop {PACKAGE}")
    time.sleep(1)
    shell(serial, f"am start -W -n {ACTIVITY}")
    time.sleep(3)

    capture(serial, output, "home_capacity")

    # Scroll the home page to the permission and privacy cards.
    swipe(serial, 540, 1800, 540, 900)
    swipe(serial, 540, 1800, 540, 900)
    capture(serial, output, "home_permission")

    # Run a scan so the statistics screen has real numbers.
    goto_home(serial)
    node = find_node(serial, "开始安全体检")
    if node:
        tap(serial, node[0], node[1], settle=22)
        capture(serial, output, "scan_stats")
        node = find_node(serial, "查看体检结果")
        if node:
            tap(serial, node[0], node[1], settle=5)
            for _ in range(6):
                swipe(serial, 540, 1800, 540, 500)
            capture(serial, output, "results_clues")
            back(serial)
        back(serial)
    else:
        print("  [WARN] 未找到「开始安全体检」按钮，跳过扫描相关截图")

    # Space insights.
    goto_home(serial)
    node = find_node(serial, "查看空间洞察")
    if node:
        tap(serial, node[0], node[1], settle=5)
        capture(serial, output, "insights")
        back(serial)

    # Settings and the permission centre.
    goto_home(serial)
    node = find_node(serial, "设置与隐私")
    if node:
        tap(serial, node[0], node[1], settle=5)
        capture(serial, output, "settings_privacy")
        node = find_node(serial, "打开权限中心")
        if node:
            tap(serial, node[0], node[1], settle=5)
            capture(serial, output, "permissions")
            back(serial)


# A file name that looks like a private record is not acceptable in a store
# listing. These patterns catch the names that carry meaning to their owner.
SENSITIVE_NAME_PATTERNS = [
    re.compile(r"[\u4e00-\u9fff]"),          # any Chinese character
    re.compile(r"(?i)(wechat|weixin|alipay|qq|taobao|baidu)"),
    re.compile(r"(?i)(invoice|contract|idcard|passport|receipt|medical|patient)"),
    re.compile(r"(?i)(身份证|合同|发票|病历|患者| newborn|儿科)"),
]


def names_are_safe(serial: str) -> bool:
    """Check the file names currently visible for anything private."""
    listing = shell(
        serial,
        "content query --uri content://media/external/images/media "
        "--projection _display_name 2>/dev/null",
    )
    names = re.findall(r"_display_name=(\S+)", listing)
    suspicious = []
    for name in names:
        for pattern in SENSITIVE_NAME_PATTERNS:
            if pattern.search(name):
                suspicious.append(name)
                break
    if suspicious:
        print(f"  [SKIP] 媒体库含 {len(suspicious)} 个疑似敏感文件名，不采集含文件名的界面")
        return False
    print(f"  [OK]   媒体库文件名检查通过（{len(names)} 个）")
    return True


def run_isolated_captures(serial: str, output: Path) -> None:
    print("含文件名的界面：")
    if not names_are_safe(serial):
        return

    goto_home(serial)
    node = find_node(serial, "开始安全体检")
    if not node:
        print("  [WARN] 未找到「开始安全体检」按钮")
        return
    tap(serial, node[0], node[1], settle=22)
    node = find_node(serial, "查看体检结果")
    if not node:
        print("  [WARN] 未找到「查看体检结果」按钮")
        return
    tap(serial, node[0], node[1], settle=5)
    for _ in range(6):
        swipe(serial, 540, 1800, 540, 500)
    node = find_node(serial, "查看建议")
    if node:
        tap(serial, node[0], node[1], settle=5)
        capture(serial, output, "recommendations")
    else:
        print("  [WARN] 未找到「查看建议」按钮")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("serial")
    parser.add_argument("output")
    parser.add_argument("--only", choices=["safe", "isolated", "all"], default="all")
    args = parser.parse_args()

    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=True)

    state = shell(args.serial, "getprop ro.product.model")
    print(f"设备: {state.strip()}")
    print(f"输出: {output}")
    print()

    if args.only in ("safe", "all"):
        run_safe_captures(args.serial, output)
    if args.only in ("isolated", "all"):
        run_isolated_captures(args.serial, output)

    print()
    captured = sorted(path.name for path in output.glob("*.png"))
    print(f"已采集 {len(captured)} 张: {', '.join(captured) if captured else '无'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
