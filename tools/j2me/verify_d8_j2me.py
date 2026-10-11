#!/usr/bin/env python3
# SPDX-License-Identifier: BSD-3-Clause
"""Validate the source-built D8-only bootstrap artifact against legacy classfiles."""

import argparse
import os
from pathlib import Path
import re
import subprocess
import tempfile
from zipfile import ZIP_DEFLATED, ZipFile


def run(*args):
    subprocess.run(args, check=True)


def verify_archive(jar):
    with ZipFile(jar) as archive:
        files = set(archive.namelist())
        required = "com/android/tools/r8/D8.class"
        if required not in files:
            raise AssertionError(f"Missing D8 entry point: {required}")
        for excluded in (
            "com/android/tools/r8/R8.class",
            "com/android/tools/r8/compatproguard/CompatProguard.class",
        ):
            if excluded in files:
                raise AssertionError(f"R8-only entry point remains: {excluded}")
        if "LICENSE" not in files:
            raise AssertionError("Bundled dependency license is missing")


def make_fixture(root):
    source_dir = root / "source" / "sample"
    source_dir.mkdir(parents=True)
    (source_dir / "Legacy.java").write_text(
        "package sample; public final class Legacy {"
        " public static int coins = 7;"
        " public static int getCoins() { return coins; }"
        "}",
        encoding="utf-8",
    )
    (source_dir / "Modern.java").write_text(
        "package sample; public final class Modern {"
        " public static int[][] grid() { return new int[2][3]; }"
        " public static int locked(Object lock) {"
        " synchronized (lock) { return Legacy.getCoins(); } }"
        "}",
        encoding="utf-8",
    )
    classes = root / "classes"
    classes.mkdir()
    run(
        "javac", "--release", "8", "-d", str(classes),
        str(source_dir / "Legacy.java"), str(source_dir / "Modern.java"),
    )
    legacy_path = classes / "sample" / "Legacy.class"
    legacy = bytearray(legacy_path.read_bytes())
    if legacy[:4] != bytes.fromhex("CAFEBABE"):
        raise AssertionError("Unexpected legacy fixture header")
    legacy[4:8] = bytes([0, 3, 0, 45])  # Java 1.1 (45.3)
    legacy_path.write_bytes(legacy)

    fixture = root / "mixed-j2me.jar"
    with ZipFile(fixture, "w", ZIP_DEFLATED) as archive:
        for cls in classes.rglob("*.class"):
            archive.write(cls, cls.relative_to(classes).as_posix())
        archive.writestr("assets/not-an-archive.jar", b"game resource, not a ZIP")
    return fixture


def verify_conversion(compiler, root):
    fixture = make_fixture(root)
    output = root / "converted.zip"
    java_home = Path(os.environ["JAVA_HOME"])
    run(
        "java", "-cp", str(compiler),
        "com.android.tools.r8.D8",
        "--release", "--min-api", "23",
        "--lib", str(java_home),
        "--output", str(output),
        str(fixture),
    )
    with ZipFile(output) as archive:
        names = archive.namelist()
        dex_files = [name for name in names if re.fullmatch(r"classes[0-9]*\\.dex", name)]
        if not dex_files:
            raise AssertionError(f"D8 emitted no DEX: {names}")
        dex = archive.read(dex_files[0])
        if not dex.startswith(b"dex\\n"):
            raise AssertionError("Invalid DEX magic")
        for descriptor in (b"Lsample/Legacy;", b"Lsample/Modern;"):
            if descriptor not in dex:
                raise AssertionError(f"Missing class descriptor: {descriptor!r}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("compiler", type=Path)
    args = parser.parse_args()
    compiler = args.compiler.resolve()
    verify_archive(compiler)
    result = subprocess.run(
        ["java", "-cp", str(compiler), "com.android.tools.r8.D8", "--version"],
        check=True, capture_output=True, text=True,
    )
    if "9.5.24" not in result.stdout + result.stderr:
        raise AssertionError(f"Unexpected D8 version: {result.stdout} {result.stderr}")
    with tempfile.TemporaryDirectory(prefix="d8-j2me-smoke-") as temp:
        verify_conversion(compiler, Path(temp))
    print("D8-J2ME bootstrap: retained D8, removed R8 CLI, mixed legacy DEX smoke passed")


if __name__ == "__main__":
    main()
