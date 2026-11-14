# -*- coding: utf-8 -*-
"""List the method and field names a dex actually holds for one class.

Decompilers rename obfuscated members; hook targets must be matched against the names
written in the dex itself. An APK is accepted too, in which case every classes*.dex
inside it is searched.

    python tools/dex_names.py hpoi/hpoi_2.43.apk net.hpoi.ui.hobby.HobbyDetailActivity
    python tools/dex_names.py work/dexcheck/classes6.dex 'Lnet/hpoi/ui/hobby/X;'
"""
import glob
import struct
import sys
import zipfile


def uleb(data, off):
    result = 0
    shift = 0
    while True:
        b = data[off]
        off += 1
        result |= (b & 0x7F) << shift
        if not b & 0x80:
            return result, off
        shift += 7


class Dex:
    def __init__(self, path, data=None):
        self.path = path
        self.data = data if data is not None else open(path, "rb").read()
        d = self.data
        self.n_str, self.off_str = struct.unpack_from("<II", d, 0x38)
        self.n_type, self.off_type = struct.unpack_from("<II", d, 0x40)
        self.n_field, self.off_field = struct.unpack_from("<II", d, 0x50)
        self.n_method, self.off_method = struct.unpack_from("<II", d, 0x58)
        self.n_class, self.off_class = struct.unpack_from("<II", d, 0x60)

    def string(self, i):
        off = struct.unpack_from("<I", self.data, self.off_str + 4 * i)[0]
        _, p = uleb(self.data, off)
        end = self.data.index(b"\x00", p)
        return self.data[p:end].decode("utf-8", "replace")

    def type_name(self, i):
        return self.string(struct.unpack_from("<I", self.data, self.off_type + 4 * i)[0])

    def members(self, want):
        methods, fields = [], []
        for i in range(self.n_method):
            cls, proto, name = struct.unpack_from("<HHI", self.data, self.off_method + 8 * i)
            if self.type_name(cls) == want:
                methods.append((self.string(name), self.proto(proto)))
        for i in range(self.n_field):
            cls, typ, name = struct.unpack_from("<HHI", self.data, self.off_field + 8 * i)
            if self.type_name(cls) == want:
                fields.append((self.type_name(typ), self.string(name)))
        return methods, fields

    def proto(self, i):
        # proto_ids: 0x48 size, 0x4C offset; entry = shorty, return_type, params_off (12 bytes)
        off = struct.unpack_from("<I", self.data, 0x4C)[0] + 12 * i
        _shorty, ret, params_off = struct.unpack_from("<III", self.data, off)
        args = []
        if params_off:
            n = struct.unpack_from("<I", self.data, params_off)[0]
            ids = struct.unpack_from("<%dH" % n, self.data, params_off + 4)
            args = [self.type_name(t) for t in ids]
        return "(%s)%s" % (",".join(args), self.type_name(ret))


def dexs(path):
    """Yield (label, Dex) for a .dex file or every classes*.dex in an .apk."""
    if not path.lower().endswith(".apk"):
        yield path, Dex(path)
        return
    with zipfile.ZipFile(path) as z:
        names = sorted(n for n in z.namelist() if n.endswith(".dex") and "/" not in n)
        for name in names:
            yield "%s!%s" % (path, name), Dex("%s!%s" % (path, name), z.read(name))


def main(argv):
    if len(argv) < 3:
        raise SystemExit(__doc__.strip().splitlines()[-1].strip())

    want = argv[-1]
    if not want.startswith("L"):
        want = "L" + want.replace(".", "/") + ";"

    paths = []
    for pattern in argv[1:-1]:
        paths.extend(sorted(glob.glob(pattern)) or [pattern])

    found = False
    for path in paths:
        try:
            entries = list(dexs(path))
        except (OSError, zipfile.BadZipFile, struct.error) as e:
            print("!! %s: %s" % (path, e))
            continue
        for label, dex in entries:
            try:
                methods, fields = dex.members(want)
            except struct.error as e:
                print("!! %s: %s" % (label, e))
                continue
            if not methods and not fields:
                continue
            found = True
            print("== %s  (%s)" % (want, label))
            print("methods(%d):" % len(methods))
            for name, proto in methods:
                print("  %s %s" % (proto, name))
            print("fields(%d):" % len(fields))
            for typ, name in fields:
                print("  %s %s" % (typ, name))

    if not found:
        raise SystemExit("class not found in: %s" % ", ".join(paths))


if __name__ == "__main__":
    main(sys.argv)
