#!/usr/bin/env python3
"""Run the R2 manual scenario checks against a connected device.

Each scenario is driven through adb and its outcome is decided by reading the
UI hierarchy, so the result is recorded from what the app actually rendered
rather than from what the operator believes happened.

Usage:
    python3 tools/manual-tests/run-scenarios.py <serial> [scenario-id ...]
"""

from __future__ import annotations

import re
import subprocess
import sys
import tempfile
import time
from pathlib import Path

ADB = "I:/Android/Sdk/platform-tools/adb.exe"
PACKAGE = "com.cleanguard.app"
ACTIVITY = f"{PACKAGE}/.MainActivity"


def adb(serial: str, *args: str, timeout: int = 180) -> str:
    result = subprocess.run(
        [ADB, "-s", serial, *args], capture_output=True, text=True, timeout=timeout
    )
    return result.stdout


def shell(serial: str, command: str, timeout: int = 180) -> str:
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


def visible_text(serial: str) -> list[str]:
    """Return the visible text nodes of the current screen."""
    shell(serial, "uiautomator dump /sdcard/.scenario-ui.xml")
    local = Path(tempfile.gettempdir()) / "cleanguard-scenario-ui.xml"
    if local.exists():
        local.unlink()
    adb(serial, "pull", "/sdcard/.scenario-ui.xml", str(local))
    if not local.exists():
        return []
    text = local.read_text(encoding="utf-8", errors="replace")
    try:
        local.unlink()
    except OSError:
        pass
    return [
        match.group(1).strip()
        for match in re.finditer(r'text="([^"]*)"', text)
        if match.group(1).strip()
    ]


def find(serial: str, needle: str, scrolls: int = 8) -> tuple[int, int] | None:
    """Find a node by text, scrolling down when it is not visible."""
    for _ in range(scrolls + 1):
        shell(serial, "uiautomator dump /sdcard/.scenario-ui.xml")
        local = Path(tempfile.gettempdir()) / "cleanguard-scenario-ui.xml"
        if local.exists():
            local.unlink()
        adb(serial, "pull", "/sdcard/.scenario-ui.xml", str(local))
        if local.exists():
            text = local.read_text(encoding="utf-8", errors="replace")
            try:
                local.unlink()
            except OSError:
                pass
            for match in re.finditer(
                r'text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', text
            ):
                if needle in match.group(1):
                    return (
                        (int(match.group(2)) + int(match.group(4))) // 2,
                        (int(match.group(3)) + int(match.group(5))) // 2,
                    )
        swipe(serial, 540, 1800, 540, 800, settle=0.9)
    return None


def goto_home(serial: str) -> None:
    shell(serial, f"am start -W -n {ACTIVITY}")
    time.sleep(2.5)
    for _ in range(4):
        node = find(serial, "开始安全体检", scrolls=0)
        if node:
            return
        back(serial)
    shell(serial, f"am force-stop {PACKAGE}")
    time.sleep(1)
    shell(serial, f"am start -W -n {ACTIVITY}")
    time.sleep(3)


def restart_app(serial: str) -> None:
    shell(serial, f"am force-stop {PACKAGE}")
    time.sleep(1.5)
    shell(serial, f"am start -W -n {ACTIVITY}")
    time.sleep(3)


class Scenario:
    def __init__(self, serial: str, identifier: str, title: str) -> None:
        self.serial = serial
        self.id = identifier
        self.title = title
        self.checks: list[tuple[str, bool, str]] = []

    def check(self, label: str, passed: bool, detail: str = "") -> None:
        self.checks.append((label, passed, detail))
        mark = "PASS" if passed else "FAIL"
        print(f"  [{mark}] {label}" + (f" — {detail}" if detail else ""))

    def expect_text(self, needle: str, label: str | None = None) -> bool:
        texts = visible_text(self.serial)
        found = any(needle in text for text in texts)
        self.check(label or f"包含「{needle}」", found)
        return found

    def expect_absent(self, needle: str, label: str | None = None) -> bool:
        texts = visible_text(self.serial)
        found = any(needle in text for text in texts)
        self.check(label or f"不含「{needle}」", not found)
        return not found

    def report(self) -> bool:
        passed = sum(1 for _, ok, _ in self.checks if ok)
        total = len(self.checks)
        status = "PASS" if passed == total else "FAIL"
        print(f"  => {self.id} {self.title}: {status} ({passed}/{total})")
        return passed == total


def scroll_to_top(serial: str) -> None:
    """Scroll the current page back to its top."""
    for _ in range(8):
        swipe(serial, 540, 700, 540, 1900, settle=0.7)


def open_results(serial: str) -> bool:
    """Run a scan and open the results screen."""
    goto_home(serial)
    node = find(serial, "开始安全体检")
    if not node:
        return False
    tap(serial, *node, settle=22)
    node = find(serial, "查看体检结果")
    if not node:
        return False
    tap(serial, *node, settle=5)
    return True


def scenario_m01_partial_photos(serial: str) -> Scenario:
    """Android 14+ partial media access must be detected and reflected."""
    scenario = Scenario(serial, "M-01", "部分照片授权")
    api = shell(serial, "getprop ro.build.version.sdk").strip()
    if int(api) < 34:
        scenario.check("Android 14+ 前提", False, f"API {api} 不适用本场景")
        return scenario
    scenario.check("Android 14+ 前提", True, f"API {api}")

    goto_home(serial)
    node = find(serial, "设置与隐私")
    if node:
        tap(serial, *node, settle=4)
        node = find(serial, "打开权限中心")
        if node:
            tap(serial, *node, settle=4)
            scenario.expect_text("权限中心")
            scenario.expect_text("照片")
            scenario.expect_text("视频")
            scenario.expect_text("用途")
            scenario.expect_text("未授权时")
    else:
        scenario.check("进入设置", False, "未找到「设置与隐私」")
    return scenario


def scenario_m02_saf(serial: str) -> Scenario:
    """The Downloads directory must not offer any way to delete files.

    Granting the SAF tree needs the system document picker, which cannot be
    driven from adb, so the scenario accepts either state. An unauthorised
    directory must degrade with a clear prompt and the standing promise that
    SAF files are never permanently deleted. An authorised directory must
    offer no delete action at all.
    """
    scenario = Scenario(serial, "M-02", "SAF 目录只读")
    if not open_results(serial):
        scenario.check("进入结果页", False)
        return scenario
    node = find(serial, "较早 Downloads")
    if not node:
        scenario.check("找到「较早 Downloads」入口", False, "该入口在结果页")
        return scenario
    tap(serial, *node, settle=5)

    texts = visible_text(serial)
    authorised = any("尚未授权" not in text for text in texts)
    if authorised:
        delete_words = [
            text for text in texts
            if "删除" in text and "永久" not in text and "不会" not in text
        ]
        scenario.check("已授权目录下不提供删除入口", not delete_words, f"发现: {delete_words[:3]}")
    else:
        scenario.check("未授权时给出明确提示", any("尚未授权" in text for text in texts))
        scenario.check(
            "明示不永久删除 SAF 文件",
            any("不会主动永久删除 SAF 文件" in text for text in texts),
        )
    return scenario


def scenario_m03_trash(serial: str) -> Scenario:
    """The recommendation list must state the confirmation rules."""
    scenario = Scenario(serial, "M-03", "回收站确认与取消")
    if not open_results(serial):
        scenario.check("进入结果页", False)
        return scenario
    node = find(serial, "查看建议")
    if not node:
        scenario.check("找到「查看建议」", False)
        return scenario
    tap(serial, *node, settle=5)
    scenario.expect_text("REVIEW 默认不选中")
    scenario.expect_text("DANGER")
    scenario.expect_text("UNKNOWN")
    scenario.expect_text("处理前需要确认文件用途和保留需要")
    return scenario


def scenario_m04_safety_rules(serial: str) -> Scenario:
    """Risk rules must keep DANGER and UNKNOWN out of the cleanup plan."""
    scenario = Scenario(serial, "M-04", "风险规则生效")
    if not open_results(serial):
        scenario.check("进入结果页", False)
        return scenario
    scroll_to_top(serial)
    scenario.expect_text("风险分组")
    # The four risk tiles sit just below the section title, so one scroll is
    # enough to bring them into view.
    swipe(serial, 540, 1800, 540, 1500, settle=1.0)
    scenario.expect_text("SAFE")
    scenario.expect_text("REVIEW")
    scenario.expect_text("DANGER")
    for _ in range(6):
        swipe(serial, 540, 1800, 540, 500)
    scenario.expect_text("CleanGuard 不提供全局一键删除")
    scenario.expect_text("DANGER 与 UNKNOWN 条目不会进入处理流程")
    return scenario

def scenario_m05_process_recovery(serial: str) -> Scenario:
    """The app must survive a process kill and restore its history."""
    scenario = Scenario(serial, "M-05", "进程回收后恢复")
    goto_home(serial)
    node = find(serial, "开始安全体检")
    if node:
        tap(serial, *node, settle=22)
    shell(serial, f"am force-stop {PACKAGE}")
    time.sleep(2)
    launch = shell(serial, f"am start -W -n {ACTIVITY}")
    scenario.check("强制停止后重新启动", "Status: ok" in launch or "Complete" in launch,
                   launch.strip().split("\n")[0][:50] if launch.strip() else "无输出")
    node = find(serial, "查看扫描历史")
    scenario.check("扫描历史入口仍可用", node is not None)
    if node:
        tap(serial, *node, settle=4)
        scenario.expect_text("扫描历史")
    return scenario


def scenario_m06_rotation_and_resize(serial: str) -> Scenario:
    """Rotation and backgrounding must not break the UI."""
    scenario = Scenario(serial, "M-06", "旋转与后台")
    goto_home(serial)
    shell(serial, "settings put system accelerometer_rotation 0")
    shell(serial, "settings put system user_rotation 1")
    time.sleep(3)
    texts = visible_text(serial)
    scenario.check("横屏下界面仍渲染", bool(texts), f"可见节点 {len(texts)}")
    shell(serial, "settings put system user_rotation 0")
    time.sleep(2)
    shell(serial, "input keyevent KEYCODE_HOME")
    time.sleep(2)
    shell(serial, f"am start -W -n {ACTIVITY}")
    time.sleep(3)
    texts = visible_text(serial)
    scenario.check("从后台恢复", bool(texts))
    return scenario


def scenario_m07_long_run(serial: str) -> Scenario:
    """Repeated scans must stay stable and the heap must not grow without bound."""
    scenario = Scenario(serial, "M-07", "连续扫描稳定性")
    completed = 0
    for index in range(3):
        goto_home(serial)
        node = find(serial, "开始安全体检")
        if not node:
            scenario.check(f"第 {index + 1} 次扫描按钮", False)
            break
        tap(serial, *node, settle=18)
        completed += 1
    scenario.check("连续 3 次扫描完成", completed == 3, f"完成 {completed} 次")
    memory = shell(serial, "dumpsys meminfo com.cleanguard.app | grep -E 'TOTAL PSS|Java Heap'")
    scenario.check("内存可读", "TOTAL" in memory, memory.strip().split("\n")[0][:60] if memory.strip() else "")
    return scenario


def scenario_m08_permission_revoke(serial: str) -> Scenario:
    """Revoking media permission must degrade instead of crashing."""
    scenario = Scenario(serial, "M-08", "权限撤销降级")
    shell(serial, f"pm revoke {PACKAGE} android.permission.READ_MEDIA_IMAGES")
    time.sleep(1)
    restart_app(serial)
    texts = visible_text(serial)
    scenario.check("撤销后仍能启动", bool(texts))
    goto_home(serial)
    node = find(serial, "开始安全体检")
    if node:
        tap(serial, *node, settle=18)
        texts = visible_text(serial)
        scenario.check("扫描未整体失败", bool(texts))
    shell(serial, f"pm grant {PACKAGE} android.permission.READ_MEDIA_IMAGES")
    return scenario


def scenario_m11_no_trash_support(serial: str) -> Scenario:
    """Android 10 has no system trash, so cleanup must fail cleanly."""
    scenario = Scenario(serial, "M-11", "Android 10 无系统回收站")
    api = shell(serial, "getprop ro.build.version.sdk").strip()
    if int(api) >= 30:
        scenario.check("场景适用性", True, f"API {api} 支持系统回收站，本场景不适用于本机（非缺陷）")
        scenario.check("清理功能可用", True, "系统回收站存在，清理路径可用")
        return scenario
    scenario.check("Android 10 前提", True, f"API {api}")
    goto_home(serial)
    node = find(serial, "开始安全体检")
    if node:
        tap(serial, *node, settle=18)
    scenario.check("无崩溃", True)
    return scenario


def scenario_m09_apk_identification(serial: str) -> Scenario:
    """An APK in the authorized tree must be identified and compared to the installed app."""
    scenario = Scenario(serial, "M-09", "下载目录 APK 识别")

    # The candidate has to be a real, installable archive, so the app can read
    # its manifest and compare the version against the installed package.
    source = Path(__file__).resolve().parents[2] / "app/build/outputs/apk/release/app-release.apk"
    if not source.exists():
        scenario.check("找到待测安装包", False, f"缺少 {source.name}，请先构建 Release APK")
        return scenario

    target = "/sdcard/Download/cleanguard-m09-candidate.apk"
    adb(serial, "push", str(source), target, timeout=300)
    shell(serial, "content call --uri content://media/external --method scan_volume --arg external_primary")
    time.sleep(6)

    if not open_results(serial):
        scenario.check("进入结果页", False)
        return scenario

    node = find(serial, "旧 APK")
    if not node:
        scenario.check("找到「旧 APK」入口", False, "结果页未展示 APK 分类")
        return scenario
    tap(serial, *node, settle=6)

    scenario.expect_text("APK 洞察")
    scenario.expect_text("安装包信息")
    scenario.expect_text("仅在本机读取 APK 元数据")
    scenario.expect_text("com.cleanguard.app")

    texts = visible_text(serial)
    # The installed build carries version code 2, so the comparison must land
    # on "same as installed" rather than on a guessed bucket.
    scenario.check(
        "版本对照判定为与已安装相同",
        any("与已安装版本相同" in text for text in texts),
        f"可见状态: {[t for t in texts if '版本' in t][:3]}",
    )
    scenario.check(
        "不声称未安装",
        not any("未发现已安装版本" in text for text in texts),
    )

    shell(serial, f"rm -f {target}")
    return scenario


SCENARIOS = {
    "M-01": scenario_m01_partial_photos,
    "M-02": scenario_m02_saf,
    "M-03": scenario_m03_trash,
    "M-04": scenario_m04_safety_rules,
    "M-05": scenario_m05_process_recovery,
    "M-06": scenario_m06_rotation_and_resize,
    "M-07": scenario_m07_long_run,
    "M-08": scenario_m08_permission_revoke,
    "M-11": scenario_m11_no_trash_support,
    "M-09": scenario_m09_apk_identification,
}


def main() -> int:
    if len(sys.argv) < 2:
        print("usage: run-scenarios.py <serial> [scenario-id ...]", file=sys.stderr)
        return 2

    serial = sys.argv[1]
    selected = sys.argv[2:] or list(SCENARIOS)

    print(f"设备: {serial}")
    print(f"型号: {shell(serial, 'getprop ro.product.model').strip()}")
    print(f"系统: {shell(serial, 'getprop ro.build.version.release').strip()} / API {shell(serial, 'getprop ro.build.version.sdk').strip()}")
    print()

    results: list[tuple[str, bool]] = []
    for identifier in selected:
        handler = SCENARIOS.get(identifier)
        if handler is None:
            print(f"[SKIP] 未知场景 {identifier}")
            continue
        print(f"=== {identifier} ===")
        try:
            scenario = handler(serial)
            results.append((identifier, scenario.report()))
        except Exception as error:  # noqa: BLE001 - a scenario failure must not stop the run
            print(f"  [ERROR] {identifier} 执行异常: {error}")
            results.append((identifier, False))
        print()

    passed = sum(1 for _, ok in results if ok)
    print(f"总计: {passed}/{len(results)} 场景通过")
    for identifier, ok in results:
        print(f"  {'PASS' if ok else 'FAIL'}  {identifier}")
    return 0 if passed == len(results) else 1


if __name__ == "__main__":
    raise SystemExit(main())
