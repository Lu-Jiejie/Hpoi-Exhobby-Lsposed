# -*- coding: utf-8 -*-
"""Add classes.dex into the resource-only apk produced by aapt2 link."""
import shutil
import sys
import zipfile


def main():
    if len(sys.argv) != 4:
        raise SystemExit("usage: pack_apk.py <base.apk> <classes.dex> <out.apk>")
    base, dex, out = sys.argv[1], sys.argv[2], sys.argv[3]
    shutil.copyfile(base, out)
    with zipfile.ZipFile(out, "a", zipfile.ZIP_DEFLATED) as z:
        z.write(dex, "classes.dex")
    with zipfile.ZipFile(out) as z:
        names = z.namelist()
    if "classes.dex" not in names:
        raise SystemExit("packaging failed: classes.dex missing")
    print("entries:", len(names), "has dex:", True)


if __name__ == "__main__":
    main()
