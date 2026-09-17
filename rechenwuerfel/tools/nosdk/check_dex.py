#!/usr/bin/env python3
"""
Bricht ab, wenn die Dex-Datei invoke-custom-Aufrufstellen (call_site_ids) enthält.
Solche Stellen entstehen aus invokedynamic-Bytecode (LambdaMetafactory), den dx
unverändert durchreicht; Android kann sie nicht ausführen und stürzt ab.

Aufruf: check_dex.py <classes.dex>
"""
import struct
import sys

TYPE_CALL_SITE_ID_ITEM = 0x0007
TYPE_METHOD_HANDLE_ITEM = 0x0008


def main(path):
    data = open(path, "rb").read()
    map_off = struct.unpack_from("<I", data, 52)[0]
    count = struct.unpack_from("<I", data, map_off)[0]
    bad = {}
    for i in range(count):
        item_type, _, size, _ = struct.unpack_from("<HHII", data, map_off + 4 + i * 12)
        if item_type in (TYPE_CALL_SITE_ID_ITEM, TYPE_METHOD_HANDLE_ITEM) and size:
            bad[hex(item_type)] = size
    if bad:
        raise SystemExit(f"FEHLER: {path} enthält invoke-custom-Aufrufstellen: {bad}")
    print(f"Dex geprüft: keine invoke-custom-Aufrufstellen in {path}")


if __name__ == "__main__":
    main(sys.argv[1])
