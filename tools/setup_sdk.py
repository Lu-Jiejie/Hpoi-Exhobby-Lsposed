"""Fetch the Android SDK pieces the build needs (platform 34 + build-tools 34).

sdkmanager cannot reach Google's repository from some networks, so the two zips are
downloaded directly and unpacked into `sdk/`.
"""

import os
import platform
import shutil
import ssl
import sys
import urllib.request
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SDK = os.path.join(ROOT, "sdk")
CACHE = os.path.join(ROOT, "build", "download")

BASE = "https://dl.google.com/android/repository/"

# The platform zip is plain data, only build-tools carry host binaries.
BUILD_TOOLS_ZIPS = {
    "windows": "build-tools_r34-windows.zip",
    "linux": "build-tools_r34-linux.zip",
    "darwin": "build-tools_r34-macosx.zip",
}


def build_tools_zip():
    system = platform.system().lower()
    for key, name in BUILD_TOOLS_ZIPS.items():
        if key in system:
            return name
    return BUILD_TOOLS_ZIPS["linux"]


PACKAGES = [
    ("platform-34-ext7_r03.zip", "android-34", os.path.join("platforms", "android-34")),
    (build_tools_zip(), "android-14", os.path.join("build-tools", "34.0.0")),
]


def download(url, dest):
    if os.path.exists(dest) and os.path.getsize(dest) > 0:
        print("cached  %s" % os.path.basename(dest))
        return
    print("fetch   %s" % url)
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    tmp = dest + ".part"
    with urllib.request.urlopen(url, context=ctx) as r, open(tmp, "wb") as f:
        shutil.copyfileobj(r, f)
    os.replace(tmp, dest)


def unpack(zip_path, inner, target):
    if os.path.isdir(target) and os.listdir(target):
        print("ready   %s" % target)
        return
    print("unpack  %s" % os.path.basename(zip_path))
    tmp = target + ".tmp"
    shutil.rmtree(tmp, ignore_errors=True)
    with zipfile.ZipFile(zip_path) as z:
        z.extractall(tmp)
    src = os.path.join(tmp, inner)
    if not os.path.isdir(src):
        # Archive layout changes between hosts; fall back to the only top-level folder.
        entries = [e for e in os.listdir(tmp) if os.path.isdir(os.path.join(tmp, e))]
        if len(entries) != 1:
            raise SystemExit("unexpected archive layout in %s: %s" % (zip_path, entries))
        src = os.path.join(tmp, entries[0])
    shutil.rmtree(target, ignore_errors=True)
    os.makedirs(os.path.dirname(target), exist_ok=True)
    os.replace(src, target)
    shutil.rmtree(tmp, ignore_errors=True)


def main():
    for name, inner, rel in PACKAGES:
        archive = os.path.join(CACHE, name)
        download(BASE + name, archive)
        unpack(archive, inner, os.path.join(SDK, rel))
    print("SDK ready at %s" % SDK)
    print("next: bash tools/build.sh")


if __name__ == "__main__":
    sys.exit(main())
