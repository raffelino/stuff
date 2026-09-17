#!/usr/bin/env python3
"""
Packt classes.dex in die von aapt2 erzeugte Ressourcen-APK und schreibt eine
neue, unsignierte APK, in der alle unkomprimierten Einträge (resources.arsc,
WAV-Dateien, ...) auf 4 Byte ausgerichtet sind – Ersatz für zipalign.

Aufruf: package_apk.py <base.apk> <classes.dex> <out.apk>
"""
import struct
import sys
import zipfile

ALIGN = 4


def write_aligned(out, name, data, compress):
    zi = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
    zi.compress_type = zipfile.ZIP_DEFLATED if compress else zipfile.ZIP_STORED
    zi.create_system = 0
    if not compress:
        header_offset = out.fp.tell()
        data_offset = header_offset + 30 + len(name.encode("utf-8"))
        pad = (-data_offset) % ALIGN
        if pad:
            # Leeres Extra-Feld als Füllung (Header-ID 0xD935 wie bei zipalign)
            zi.extra = struct.pack("<HH", 0xD935, pad - 4) + b"\0" * (pad - 4) if pad >= 4 else b"\0" * pad
            if pad < 4:
                # Extra-Felder müssen mind. 4 Byte haben: auf pad+4 auffüllen
                zi.extra = struct.pack("<HH", 0xD935, pad) + b"\0" * pad
    out.writestr(zi, data)


def main(base, dex, target):
    with zipfile.ZipFile(base) as src, zipfile.ZipFile(target, "w") as out:
        for info in src.infolist():
            data = src.read(info.filename)
            compress = info.compress_type != zipfile.ZIP_STORED
            write_aligned(out, info.filename, data, compress)
        with open(dex, "rb") as f:
            write_aligned(out, "classes.dex", f.read(), compress=False)
    check(target)


def check(path):
    """Prüft, dass alle unkomprimierten Einträge ausgerichtet sind."""
    with zipfile.ZipFile(path) as z, open(path, "rb") as f:
        for info in z.infolist():
            if info.compress_type != zipfile.ZIP_STORED:
                continue
            f.seek(info.header_offset)
            hdr = f.read(30)
            n, m = struct.unpack("<HH", hdr[26:30])
            data_offset = info.header_offset + 30 + n + m
            if data_offset % ALIGN:
                raise SystemExit(f"nicht ausgerichtet: {info.filename} @ {data_offset}")
    print("Alignment geprüft: alle unkomprimierten Einträge sind 4-Byte-ausgerichtet")


if __name__ == "__main__":
    if len(sys.argv) == 3 and sys.argv[1] == "--check":
        check(sys.argv[2])
    else:
        main(*sys.argv[1:4])
