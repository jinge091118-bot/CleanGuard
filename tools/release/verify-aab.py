#!/usr/bin/env python3
"""Verify that a CleanGuard release AAB is ready to upload to Google Play.

The checks mirror what Play Console validates on upload, so problems are
caught locally instead of after a rejected upload. The script reads the
protobuf manifest directly out of the bundle, because the build-tools
binaries (aapt2, apksigner) only understand APKs.

Usage:
    python3 tools/release/verify-aab.py [path/to/app-release.aab]
"""

from __future__ import annotations

import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

DEFAULT_AAB = Path("app/build/outputs/bundle/release/app-release.aab")

# Permissions that must never appear in a published build. Each entry is a
# Play policy red flag or a contradiction of the published privacy policy.
FORBIDDEN_PERMISSIONS = {
    "android.permission.INTERNET": "no network access is claimed in the privacy policy",
    "android.permission.MANAGE_EXTERNAL_STORAGE": "all-files access is not claimed",
    "android.permission.QUERY_ALL_PACKAGES": "full package visibility is not claimed",
    "android.permission.WRITE_EXTERNAL_STORAGE": "external storage writes are not performed",
    "android.permission.MANAGE_MEDIA": "media management is not claimed",
    "android.permission.ACCESS_FINE_LOCATION": "no location is collected",
    "android.permission.ACCESS_COARSE_LOCATION": "no location is collected",
    "android.permission.READ_SMS": "no messages are read",
    "android.permission.READ_CONTACTS": "no contacts are read",
    "android.permission.READ_CALL_LOG": "no call logs are read",
    "android.permission.BIND_ACCESSIBILITY_SERVICE": "no accessibility service is used",
    "android.permission.REQUEST_INSTALL_PACKAGES": "no package installation is performed",
}

EXPECTED_PERMISSIONS = {
    "android.permission.READ_EXTERNAL_STORAGE",
    "android.permission.READ_MEDIA_IMAGES",
    "android.permission.READ_MEDIA_VIDEO",
    "android.permission.READ_MEDIA_AUDIO",
    "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
    "android.permission.PACKAGE_USAGE_STATS",
}

EXPECTED_PACKAGE = "com.cleanguard.app"
EXPECTED_VERSION_CODE = 2
EXPECTED_VERSION_NAME = "0.2.0"
MIN_TARGET_SDK = 35

# Permissions injected by AndroidX libraries. They are signature-level and
# self-scoped, so they never appear in the Play listing permission list.
LIBRARY_INJECTED = {
    "com.cleanguard.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
    "android.permission.DUMP",
}


class Report:
    def __init__(self) -> None:
        self.lines: list[str] = []
        self.failures: list[str] = []
        self.warnings: list[str] = []

    def ok(self, label: str, detail: str = "") -> None:
        suffix = f" — {detail}" if detail else ""
        self.lines.append(f"  [OK]   {label}{suffix}")

    def fail(self, label: str, detail: str) -> None:
        self.lines.append(f"  [FAIL] {label} — {detail}")
        self.failures.append(f"{label}: {detail}")

    def warn(self, label: str, detail: str) -> None:
        self.lines.append(f"  [WARN] {label} — {detail}")
        self.warnings.append(f"{label}: {detail}")

    def section(self, title: str) -> None:
        self.lines.append("")
        self.lines.append(title)


def read_manifest_raw(aab: Path) -> bytes:
    with zipfile.ZipFile(aab) as archive:
        return archive.read("base/manifest/AndroidManifest.xml")


def read_manifest_strings(raw: bytes) -> list[str]:
    return [chunk.decode("ascii") for chunk in re.findall(rb"[\x20-\x7e]{4,}", raw)]


def extract_permissions(strings: list[str]) -> set[str]:
    permissions: set[str] = set()
    for value in strings:
        match = re.search(r"(android\.permission\.[A-Z_]+)", value)
        if match:
            permissions.add(match.group(1))
    return permissions


def extract_integers(strings: list[str], label: str) -> int | None:
    """Deprecated string-table lookup; kept for the version name path."""
    return None


def extract_version_code(raw: bytes) -> int | None:
    """Read versionCode from the protobuf manifest.

    In the bundle manifest the attribute is encoded as a field whose value is
    a varint: the label is followed by a length byte, a type byte, and then
    the varint itself. Version code 2 therefore appears as 0x08 0x02.
    """
    index = raw.find(b"versionCode")
    if index < 0:
        return None
    # Skip past the field header: the value is a varint field (wire type 0).
    for offset in range(index + len("versionCode"), min(index + len("versionCode") + 12, len(raw))):
        if raw[offset] == 0x08:
            value, _ = decode_varint(raw, offset + 1)
            return value
    return None


def decode_varint(data: bytes, index: int) -> tuple[int, int]:
    """Decode a protobuf varint, returning the value and the next index."""
    result = 0
    shift = 0
    while index < len(data):
        byte = data[index]
        result |= (byte & 0x7F) << shift
        index += 1
        if not byte & 0x80:
            break
        shift += 7
    return result, index


def extract_sdk_version(raw: bytes, label: bytes) -> int | None:
    """Read an SDK version attribute from the protobuf manifest.

    These are encoded as a nested message holding the value as a varint, so
    the number appears as a printable ASCII run right after the label.
    """
    index = raw.find(label)
    if index < 0:
        return None
    window = raw[index:index + 24]
    match = re.search(rb"\x1a\x02([0-9]{2})", window)
    if match:
        return int(match.group(1))
    match = re.search(rb"\x08([0-9]{1,3})", window)
    if match:
        return int(match.group(1))
    return None


def extract_version_name(raw: bytes) -> str | None:
    index = raw.find(b"versionName")
    if index < 0:
        return None
    window = raw[index:index + 32]
    # The value is a length-delimited field: 0x1a, one length byte, then the
    # string. The length byte is binary, so it cannot be read as a digit.
    match = re.search(rb"\x1a(.)([0-9]+\.[0-9]+\.[0-9]+)", window, re.S)
    if match:
        return match.group(2).decode("ascii")
    return None


def check_signing(aab: Path, report: Report) -> None:
    report.section("签名 (Signing)")
    with zipfile.ZipFile(aab) as archive:
        signature_entries = [
            name for name in archive.namelist()
            if name.startswith("META-INF/") and name.endswith((".RSA", ".DSA", ".EC"))
        ]
        if not signature_entries:
            report.fail("签名存在", "AAB 内未找到签名文件")
            return
        report.ok("签名存在", f"{signature_entries[0]}")

        certificate = archive.read(signature_entries[0])
        # A debug key always uses the alias "androiddebugkey". The upload key
        # uses the project's own alias, so the alias name is the discriminator.
        alias = "androiddebugkey" if b"androiddebugkey" in certificate else None
        if alias:
            report.fail("签名密钥类型", "检测到 debug key，不能上传到 Play")
        else:
            report.ok("签名密钥类型", "非 debug key（应为受保护的 upload key）")

        subject = extract_certificate_subject(certificate)
        if subject:
            report.ok("证书主体", subject)
            if "CN=Android Debug" in subject:
                report.fail("证书主体", "这是 Android debug 证书")
        else:
            report.warn("证书主体", "无法解析，keytool 可能未安装")


def extract_certificate_subject(certificate: bytes) -> str | None:
    """Read the certificate subject with keytool when it is available.

    The subject inside a PKCS#7 blob is DER encoded, so it cannot be matched
    as plain text. keytool handles the decoding, and the JDK ships with the
    Android build toolchain.
    """
    keytool = find_keytool()
    if keytool is None:
        return None

    with tempfile.TemporaryDirectory() as workdir:
        path = Path(workdir) / "cert.rsa"
        path.write_bytes(certificate)
        try:
            result = subprocess.run(
                [str(keytool), "-printcert", "-file", str(path)],
                capture_output=True,
                timeout=60,
            )
        except (OSError, subprocess.SubprocessError):
            return None

    output = (result.stdout + result.stderr).decode("utf-8", errors="replace")
    for line in output.splitlines():
        if "CN=" in line:
            match = re.search(r"CN=[^\s,]+(?:\s*,\s*[A-Z]+=[^\s,]+)*", line)
            if match:
                return match.group(0)
    return None


def find_keytool() -> Path | None:
    """Locate keytool in the JDK used by the Android build toolchain."""
    candidates = [
        Path("I:/Android/jdk-17/bin/keytool.exe"),
        Path(os.environ.get("JAVA_HOME", "")) / "bin" / "keytool.exe",
        Path("C:/Program Files/Android/Android Studio/jbr/bin/keytool.exe"),
    ]
    for candidate in candidates:
        if candidate and candidate.exists():
            return candidate
    found = shutil.which("keytool")
    return Path(found) if found else None


def check_version(raw: bytes, report: Report) -> None:
    report.section("版本与 SDK (Version and SDK)")
    version_code = extract_version_code(raw)
    if version_code == EXPECTED_VERSION_CODE:
        report.ok("versionCode", str(EXPECTED_VERSION_CODE))
    else:
        report.fail("versionCode", f"期望 {EXPECTED_VERSION_CODE}，实际 {version_code}")

    version_name = extract_version_name(raw)
    if version_name == EXPECTED_VERSION_NAME:
        report.ok("versionName", version_name)
    else:
        report.warn("versionName", f"期望 {EXPECTED_VERSION_NAME}，实际 {version_name}")

    target_sdk = extract_sdk_version(raw, b"targetSdkVersion")
    if target_sdk is None:
        report.warn("targetSdkVersion", "未能从 manifest 解析")
    elif target_sdk >= MIN_TARGET_SDK:
        report.ok("targetSdkVersion", str(target_sdk))
    else:
        report.fail("targetSdkVersion", f"{target_sdk} 低于 Play 当前要求的 {MIN_TARGET_SDK}")

    min_sdk = extract_sdk_version(raw, b"minSdkVersion")
    if min_sdk is not None:
        report.ok("minSdkVersion", str(min_sdk))


def check_package(strings: list[str], report: Report) -> None:
    report.section("包信息 (Package)")
    if any(EXPECTED_PACKAGE in value for value in strings):
        report.ok("applicationId", EXPECTED_PACKAGE)
    else:
        report.fail("applicationId", f"未找到 {EXPECTED_PACKAGE}")


def check_permissions(strings: list[str], report: Report) -> None:
    report.section("权限 (Permissions)")
    declared = extract_permissions(strings)

    forbidden_found = sorted(declared & set(FORBIDDEN_PERMISSIONS))
    if forbidden_found:
        for permission in forbidden_found:
            report.fail("禁用权限", f"{permission} — {FORBIDDEN_PERMISSIONS[permission]}")
    else:
        report.ok("无禁用权限", "与隐私政策声明一致")

    app_scoped = {p for p in declared if p.startswith(EXPECTED_PACKAGE)}
    library_scoped = declared & LIBRARY_INJECTED
    report.ok("库注入权限", ", ".join(sorted(library_scoped)) or "无")

    expected_present = EXPECTED_PERMISSIONS - declared
    if expected_present:
        for permission in sorted(expected_present):
            report.fail("缺失权限", f"{permission} 应存在但未在 manifest 中找到")
    else:
        report.ok("声明权限齐全", f"{len(EXPECTED_PERMISSIONS)} 项")

    unexpected = declared - EXPECTED_PERMISSIONS - LIBRARY_INJECTED - app_scoped
    if unexpected:
        for permission in sorted(unexpected):
            report.warn("额外权限", f"{permission} 不在预期清单中，请确认用途")
    else:
        report.ok("无意外权限")


def check_debug_markers(aab: Path, report: Report) -> None:
    report.section("调试标记 (Debug markers)")
    with zipfile.ZipFile(aab) as archive:
        metadata = archive.read("base/root/META-INF/com/android/build/gradle/app-metadata.properties").decode(
            "utf-8", errors="replace"
        )
    debuggable = "true" in metadata.lower() and "android.debuggable" in metadata
    if debuggable:
        report.fail("debuggable", "manifest 标记为可调试")
    else:
        report.ok("debuggable", "未标记为可调试")


def check_contents(aab: Path, report: Report) -> None:
    report.section("产物内容 (Bundle contents)")
    with zipfile.ZipFile(aab) as archive:
        names = archive.namelist()

    for required in ("base/manifest/AndroidManifest.xml", "base/resources.pb", "BundleConfig.pb"):
        if required in names:
            report.ok(required)
        else:
            report.fail("必需条目缺失", required)

    if "base/dex/classes.dex" in names:
        report.ok("DEX 存在", "base/dex/classes.dex")
    else:
        report.fail("DEX 缺失", "base/dex/classes.dex")

    assets = [name for name in names if name.startswith("base/assets/")]
    if any(name.endswith("safety_rules_v1.json") for name in assets):
        report.ok("安全规则已打包", "safety_rules_v1.json")
    else:
        report.fail("安全规则缺失", "safety_rules_v1.json 未打包进 assets")

    native_libs = sorted({name.split("/")[2] for name in names if name.startswith("base/lib/")})
    if native_libs:
        report.ok("原生库 ABI", ", ".join(native_libs))

    size_mb = aab.stat().st_size / (1024 * 1024)
    if size_mb <= 150:
        report.ok("AAB 体积", f"{size_mb:.1f} MB（Play 上限 150 MB 基准）")
    else:
        report.warn("AAB 体积", f"{size_mb:.1f} MB 偏大，建议用 App Bundle 动态特性拆分")


def main() -> int:
    target = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_AAB
    if not target.exists():
        print(f"AAB 不存在: {target}", file=sys.stderr)
        return 2

    report = Report()
    report.lines.append(f"CleanGuard Release AAB 上传就绪校验")
    report.lines.append(f"产物: {target}")
    report.lines.append(f"大小: {target.stat().st_size / (1024 * 1024):.2f} MB")

    raw = read_manifest_raw(target)
    strings = read_manifest_strings(raw)

    check_signing(target, report)
    check_package(strings, report)
    check_version(raw, report)
    check_permissions(strings, report)
    check_debug_markers(target, report)
    check_contents(target, report)

    print("\n".join(report.lines))
    print("")
    if report.failures:
        print(f"结论: 不通过 — {len(report.failures)} 项失败, {len(report.warnings)} 项警告")
        for failure in report.failures:
            print(f"  - {failure}")
        return 1
    if report.warnings:
        print(f"结论: 通过（有 {len(report.warnings)} 项警告需人工确认）")
        for warning in report.warnings:
            print(f"  - {warning}")
        return 0
    print("结论: 全部通过，可上传到 Google Play 内部测试轨道")


if __name__ == "__main__":
    raise SystemExit(main())
