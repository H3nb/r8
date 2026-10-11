#!/usr/bin/env python3
# SPDX-License-Identifier: BSD-3-Clause
"""Verify the deployable in-memory D8 contract independently from the CLI build."""

import os
from pathlib import Path
import subprocess
import sys
import tempfile
from zipfile import ZipFile

from verify_d8_j2me import make_fixture


def run(*args):
    subprocess.run(args, check=True)


def main():
    if len(sys.argv) != 2:
        raise SystemExit("Expected d8-j2me-api.jar")
    jar = Path(sys.argv[1]).resolve()
    with ZipFile(jar) as archive:
        classes = set(archive.namelist())
    required = (
        "com/android/tools/r8/D8.class",
        "com/android/tools/r8/ClassFileResourceProvider.class",
        "com/android/tools/r8/ProgramResource.class",
    )
    for name in required:
        if name not in classes:
            raise AssertionError("Missing in-memory API: " + name)
    for name in ("com/android/tools/r8/R8.class",):
        if name in classes:
            raise AssertionError("R8 launcher retained in API artifact")
    with tempfile.TemporaryDirectory(prefix="d8-j2me-api-") as location:
        root = Path(location)
        make_fixture(root)
        compiled = root / "smoke"
        compiled.mkdir()
        source = Path(__file__).with_name("D8MemoryLibrarySmoke.java")
        run("javac", "-cp", str(jar), "-d", str(compiled), str(source))
        run(
            "java", "-cp", os.pathsep.join((str(jar), str(compiled))),
            "D8MemoryLibrarySmoke",
            str(root / "classes" / "sample" / "Legacy.class"),
            str(root / "classes" / "sample" / "Modern.class"),
        )
    print(f"Validated in-memory D8 artifact: {jar.stat().st_size} bytes")


if __name__ == "__main__":
    main()
