#!/usr/bin/env python3
"""Normalize an ASCII CardDemo fixture into a fixed length record file.

The fixtures in app/data/ASCII are newline delimited and have their trailing
FILLER bytes stripped (and, for tcatbal.txt, a stray CR). GnuCOBOL
ORGANIZATION SEQUENTIAL expects fixed length records with no delimiters, so
each line is right padded with spaces to the record length and concatenated.
"""
import sys


def main() -> int:
    if len(sys.argv) != 4:
        print("usage: normalize_fixture.py <in> <out> <reclen>", file=sys.stderr)
        return 2
    src, dst, reclen = sys.argv[1], sys.argv[2], int(sys.argv[3])
    count = 0
    with open(src, "r", newline="") as fin, open(dst, "w", newline="") as fout:
        for raw in fin.read().split("\n"):
            line = raw.rstrip("\r")
            if not line:
                continue
            if len(line) > reclen:
                raise SystemExit(f"{src}: record longer than {reclen}: {len(line)}")
            fout.write(line.ljust(reclen))
            count += 1
    print(f"{src} -> {dst}: {count} records of {reclen} bytes")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
