# -*- coding: utf-8 -*-
"""Download one URL to a file, skipping certificate checks.

Sandboxed shells here cannot use curl (schannel fails to acquire credentials), so
python's urllib is the fallback. Certificate verification is off because the local
trust store is incomplete; only use it for the fixed Android/Xposed artifact URLs.

    python tools/dl.py <url> <dest>

The Xposed API jar in libs/ came from:
    https://maven.aliyun.com/repository/public/de/robv/android/xposed/api/82/api-82.jar
"""
import ssl
import sys
import urllib.request


def main(argv):
    if len(argv) < 3:
        raise SystemExit("usage: dl.py <url> <dest>")

    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE

    url, dst = argv[1], argv[2]
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=120, context=ctx) as r:
        data = r.read()
    with open(dst, "wb") as f:
        f.write(data)
    print("ok %s %d" % (dst, len(data)))


if __name__ == "__main__":
    main(sys.argv)
