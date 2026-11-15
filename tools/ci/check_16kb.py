#!/usr/bin/env python3
"""Checks that every 64-bit native library in an APK works on phones with 16 KB memory pages.

Android 15 and later can run with 16 KB pages. A library whose loadable segments are aligned to less
than 16 KB then fails the ELF alignment check, and Android shows a compatibility warning when the app
starts. Libraries stored uncompressed in the APK must also start on a 16 KB boundary in the zip.

Usage: check_16kb.py APK [APK ...]   (exits 1 and lists each bad library if any fail)
"""
import struct
import sys
import zipfile

PAGE = 16384
ABIS = ("arm64-v8a", "x86_64")  # 32-bit ABIs never run with 16 KB pages
PT_LOAD = 1


def load_alignments(data: bytes) -> list[int]:
    if data[:4] != b"\x7fELF":
        raise ValueError("not an ELF file")
    if data[4] != 2:
        raise ValueError("not a 64-bit ELF file")
    end = "<" if data[5] == 1 else ">"
    phoff, = struct.unpack_from(end + "Q", data, 0x20)
    phentsize, phnum = struct.unpack_from(end + "HH", data, 0x36)
    aligns = []
    for i in range(phnum):
        off = phoff + i * phentsize
        p_type, = struct.unpack_from(end + "I", data, off)
        if p_type == PT_LOAD:
            p_align, = struct.unpack_from(end + "Q", data, off + 0x30)
            aligns.append(p_align)
    return aligns


def data_offset(apk: str, info: zipfile.ZipInfo) -> int:
    """Where an entry's bytes start: after its local header (30 bytes, then the name and extra field)."""
    with open(apk, "rb") as f:
        f.seek(info.header_offset)
        header = f.read(30)
    name_len, extra_len = struct.unpack_from("<HH", header, 26)
    return info.header_offset + 30 + name_len + extra_len


def check(apk: str) -> list[str]:
    problems = []
    checked = 0
    with zipfile.ZipFile(apk) as z:
        for info in z.infolist():
            parts = info.filename.split("/")
            if len(parts) != 3 or parts[0] != "lib" or parts[1] not in ABIS or not parts[2].endswith(".so"):
                continue
            checked += 1
            try:
                aligns = load_alignments(z.read(info))
            except ValueError as e:
                problems.append(f"{info.filename}: {e}")
                continue
            bad = [a for a in aligns if a < PAGE or a % PAGE]
            if bad:
                problems.append(f"{info.filename}: LOAD segments aligned to {', '.join(hex(a) for a in bad)}, need 0x4000")
            if info.compress_type == zipfile.ZIP_STORED:
                start = data_offset(apk, info)
                if start % PAGE:
                    problems.append(f"{info.filename}: stored uncompressed at offset {start}, not on a 16 KB boundary")
    print(f"{apk}: checked {checked} 64-bit native libraries")
    return problems


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    problems = [p for apk in sys.argv[1:] for p in check(apk)]
    for p in problems:
        print("NOT 16 KB ALIGNED:", p)
    if problems:
        return 1
    print("All 64-bit native libraries are 16 KB aligned.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
