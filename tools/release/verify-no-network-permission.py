#!/usr/bin/env python3
"""Check that the release manifest carries no network or advertising permission.

The release promise for this version is that the application is free, has no
network access, and contains no advertising or analytics code. That promise is
stated in the privacy policy, in the store listing, and in the release
checklist, so it needs a check that can be run rather than a promise that has
to be remembered.

A dependency can add a permission without anyone editing the application
manifest. An advertising library merges `AD_ID` and the ad services
permissions by itself, and an analytics library does the same. This script
reads the merged manifest of a release build and reports every permission that
contradicts the promise.

Usage:
    python3 tools/release/verify-no-network-permission.py
    python3 tools/release/verify-no-network-permission.py path/to/merged-manifest.xml
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]

# The merged manifest of a release build. The directory the Android Gradle
# plugin uses has changed between versions, so the candidates are searched
# rather than hard coded.
MANIFEST_CANDIDATES = [
    REPO_ROOT / "app" / "build" / "intermediates" / "merged_manifest" / "release" / "processReleaseMainManifest" / "AndroidManifest.xml",
    REPO_ROOT / "app" / "build" / "intermediates" / "merged_manifests" / "release" / "processReleaseManifest" / "AndroidManifest.xml",
    REPO_ROOT / "app" / "build" / "intermediates" / "merged_manifest" / "release" / "AndroidManifest.xml",
    REPO_ROOT / "app" / "build" / "intermediates" / "packaged_manifests" / "release" / "processReleaseManifestForPackage" / "AndroidManifest.xml",
]

# Permissions that would break the promise of a local, networkless, free
# application. The list is taken from the project's own permission red line
# and from the permissions an advertising or analytics library adds on its
# own.
FORBIDDEN = {
    "android.permission.INTERNET": "the application has no network access",
    "android.permission.ACCESS_NETWORK_STATE": "the application has no network access",
    "android.permission.ACCESS_WIFI_STATE": "the application has no network access",
    "android.permission.QUERY_ALL_PACKAGES": "reading the installed application list is out of scope",
    "android.permission.READ_PHONE_STATE": "the application reads no device state",
    "android.permission.ACCESS_COARSE_LOCATION": "the application uses no location",
    "android.permission.ACCESS_FINE_LOCATION": "the application uses no location",
    "com.google.android.gms.permission.AD_ID": "the application reads no advertising identifier",
    "com.google.android.gms.permission.AD_SERVICES_ATTRIBUTION": "the application contains no advertising code",
    "com.google.android.gms.permission.AD_SERVICES_TOPICS": "the application contains no advertising code",
    "android.permission.FOREGROUND_SERVICE": "the application runs no background service",
    "android.permission.WAKE_LOCK": "the application holds no wake lock",
}

# Permissions that are expected and must not be reported.
EXPECTED = {
    "android.permission.READ_MEDIA_IMAGES",
    "android.permission.READ_MEDIA_VIDEO",
    "android.permission.READ_MEDIA_AUDIO",
    "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
    "android.permission.READ_EXTERNAL_STORAGE",
    "android.permission.PACKAGE_USAGE_STATS",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
}


def find_manifest(explicit: str | None) -> Path:
    if explicit:
        return Path(explicit)
    for candidate in MANIFEST_CANDIDATES:
        if candidate.exists():
            return candidate
    raise SystemExit(
        "the merged release manifest was not found.\n"
        "build it first:  ./gradlew.bat :app:processReleaseManifest\n"
        "or pass the path: python3 tools/release/verify-no-network-permission.py <manifest>\n"
        "looked in:\n  " + "\n  ".join(str(path) for path in MANIFEST_CANDIDATES)
    )


def permissions_in(manifest: Path) -> list[str]:
    """Read the permissions the manifest requests.

    Only the names inside a uses-permission element are read. An intent
    filter carries names too, and those are actions and categories rather
    than permissions.
    """
    text = manifest.read_text(encoding="utf-8", errors="replace")
    names = re.findall(
        r"<uses-permission[^>]*?android:name\s*=\s*\"([^\"]+)\"",
        text,
    )
    names += re.findall(
        r"<uses-permission-sdk-23[^>]*?android:name\s*=\s*\"([^\"]+)\"",
        text,
    )
    return sorted(set(names))


def main() -> int:
    manifest = find_manifest(sys.argv[1] if len(sys.argv) > 1 else None)
    names = permissions_in(manifest)
    present = sorted(set(names))

    print(f"checking {manifest}")
    print()

    violations = []
    for name in present:
        if name in FORBIDDEN:
            violations.append(name)

    print("permissions in the merged release manifest:")
    for name in present:
        if name in FORBIDDEN:
            print(f"  FORBIDDEN  {name}  ({FORBIDDEN[name]})")
        elif name in EXPECTED or name.startswith("com.cleanguard.app."):
            print(f"  expected   {name}")
        else:
            print(f"  review     {name}")

    print()
    if violations:
        print(f"{len(violations)} permission(s) break the release promise:")
        for name in violations:
            print(f"  - {name}")
        print()
        print("A library may be merging these in. Check the merged manifest of")
        print("the dependency that brings them, and remove it or exclude the")
        print("permission before the release can ship.")
        return 1

    print("no network, advertising, or analytics permission is present")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
