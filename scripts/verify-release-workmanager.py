#!/usr/bin/env python3
"""Reject a minified APK whose default WorkManager input merger cannot be constructed."""
import argparse
import os
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile


def retains_merger_constructor(dump):
    for block in re.split(r"(?m)^Class #", dump):
        if not re.search(r"Class descriptor\s*:\s*'Landroidx/work/OverwritingInputMerger;'", block):
            continue
        direct = block.split("Direct methods", 1)[-1].split("Virtual methods", 1)[0]
        for method in re.split(r"(?m)^\s+#\d+\s+:", direct):
            if (re.search(r"name\s*:\s*'<init>'", method)
                    and re.search(r"type\s*:\s*'\(\)V'", method)
                    and (flags := re.search(r"access\s*:\s*0x([0-9a-fA-F]+)", method))
                    and int(flags.group(1), 16) & 1):
                return True
    return False


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--dexdump", type=Path)
    args = parser.parse_args()
    dexdump = args.dexdump
    if dexdump is None:
        sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
        if not sdk:
            parser.error("ANDROID_HOME or --dexdump is required")
        candidates = list((Path(sdk) / "build-tools").glob("*/dexdump*"))
        if not candidates:
            parser.error("No Android SDK dexdump found")
        dexdump = sorted(candidates, key=lambda p: tuple(int(n) for n in re.findall(r"\d+", p.parent.name)))[-1]
    with zipfile.ZipFile(args.apk) as apk, tempfile.TemporaryDirectory(prefix="codexbar-dex-") as folder:
        for name in apk.namelist():
            if not re.fullmatch(r"classes\d*\.dex", name):
                continue
            dex = Path(folder) / name
            dex.write_bytes(apk.read(name))
            result = subprocess.run([str(dexdump), "-l", "plain", str(dex)], check=True,
                                    stdout=subprocess.PIPE, text=True, encoding="utf-8", errors="replace")
            if retains_merger_constructor(result.stdout):
                print("PASS minified APK retains WorkManager's public no-argument InputMerger constructor")
                return
    raise SystemExit("ERROR: minified APK removed OverwritingInputMerger.<init>()V; in-app updates cannot start")


if __name__ == "__main__":
    main()
