#!/usr/bin/env python3
# SPDX-License-Identifier: BSD-3-Clause
"""Run D8 against local J2ME games without uploading or modifying their JARs."""

import argparse
import csv
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile
import time
from zipfile import BadZipFile, ZipFile


DEX_NAME = re.compile(r"classes(?:[2-9][0-9]*|1[0-9]+)?\.dex\Z")


def test_jar(compiler, library, jar, timeout, heap_mb):
    record = {
        "file": jar.name,
        "sha256": hashlib.sha256(jar.read_bytes()).hexdigest(),
        "jar_bytes": jar.stat().st_size,
        "status": "failed",
        "dex_count": 0,
        "dex_bytes": 0,
        "seconds": 0,
        "detail": "",
    }
    try:
        with ZipFile(jar) as archive:
            classes = [name for name in archive.namelist() if name.endswith(".class")]
        record["classes"] = len(classes)
        if not classes:
            record["status"] = "no_classes"
            return record
    except (OSError, BadZipFile) as error:
        record["status"] = "invalid_zip"
        record["detail"] = str(error)[:300]
        return record

    with tempfile.TemporaryDirectory(prefix="d8-corpus-") as work:
        output = Path(work) / "converted.zip"
        args = [
            "java", f"-Xmx{heap_mb}m", "-cp", str(compiler),
            "com.android.tools.r8.D8", "--release", "--min-api", "23",
            "--lib", str(library), "--output", str(output), str(jar),
        ]
        started = time.monotonic()
        try:
            process = subprocess.run(
                args, capture_output=True, text=True, timeout=timeout, check=False
            )
        except subprocess.TimeoutExpired:
            record["status"] = "timeout"
            record["seconds"] = round(time.monotonic() - started, 3)
            return record
        record["seconds"] = round(time.monotonic() - started, 3)
        record["detail"] = (process.stderr or process.stdout)[-500:].strip()
        if process.returncode:
            record["status"] = "compiler_error"
            return record

        try:
            with ZipFile(output) as archive:
                dex_names = [name for name in archive.namelist() if DEX_NAME.fullmatch(name)]
                if not dex_names or len(set(dex_names)) != len(dex_names):
                    record["status"] = "missing_dex"
                    return record
                for name in dex_names:
                    data = archive.read(name)
                    if len(data) < 0x70 or not data.startswith(b"dex\n"):
                        record["status"] = "invalid_dex"
                        return record
                    record["dex_bytes"] += len(data)
                record["dex_count"] = len(dex_names)
        except (OSError, BadZipFile) as error:
            record["status"] = "invalid_output"
            record["detail"] = str(error)[:300]
            return record
        record["status"] = "success"
    return record


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("compiler", type=Path)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--lib", type=Path, required=True, help="JDK home or android.jar")
    parser.add_argument("--output", type=Path, default=Path("d8-corpus-results.csv"))
    parser.add_argument("--timeout", type=int, default=120)
    parser.add_argument("--heap-mb", type=int, default=768)
    args = parser.parse_args()

    compiler = args.compiler.resolve()
    library = args.lib.resolve()
    if not compiler.is_file() or not library.exists():
        parser.error("Compiler JAR or library path does not exist")
    jars = sorted(args.directory.rglob("*.jar"))
    if not jars:
        parser.error("No .jar files found")
    results = []
    for index, jar in enumerate(jars, 1):
        result = test_jar(compiler, library, jar, args.timeout, args.heap_mb)
        results.append(result)
        print(
            f"[{index}/{len(jars)}] {result['status']}: {jar.name} "
            f"{result['seconds']}s",
            flush=True,
        )
    fields = [
        "file", "sha256", "jar_bytes", "classes", "status",
        "dex_count", "dex_bytes", "seconds", "detail",
    ]
    with args.output.open("w", newline="", encoding="utf-8") as output:
        writer = csv.DictWriter(output, fieldnames=fields)
        writer.writeheader()
        writer.writerows(results)
    counts = {
        status: sum(row["status"] == status for row in results)
        for status in sorted({row["status"] for row in results})
    }
    print(json.dumps({"tested": len(results), "results": counts, "output": str(args.output)}))


if __name__ == "__main__":
    main()
