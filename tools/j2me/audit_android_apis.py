#!/usr/bin/env python3
# SPDX-License-Identifier: BSD-3-Clause
"""Inventory Java runtime references used by a D8 compiler artifact."""

import argparse
from collections import Counter, defaultdict
import json
from pathlib import Path
import struct
from zipfile import ZipFile


GROUPS = {
    "functional-interfaces": ("java/util/function/",),
    "streams": ("java/util/stream/", "java/util/Spliterator", "java/util/PrimitiveIterator"),
    "nio-files": ("java/nio/file/",),
    "time": ("java/time/",),
    "method-handles": ("java/lang/invoke/",),
    "other-modern-apis": ("java/util/Optional", "java/util/concurrent/CompletableFuture"),
}


def referenced_classes(bytecode):
    data = memoryview(bytecode)
    if len(data) < 10 or data[:4].tobytes() != b"\xca\xfe\xba\xbe":
        raise ValueError("Not a Java classfile")
    major = struct.unpack_from(">H", data, 6)[0]
    count = struct.unpack_from(">H", data, 8)[0]
    utf8_entries = {}
    class_indices = []
    offset = 10
    index = 1
    while index < count:
        tag = data[offset]
        offset += 1
        if tag == 1:
            length = struct.unpack_from(">H", data, offset)[0]
            offset += 2
            utf8_entries[index] = data[offset:offset + length].tobytes().decode(
                "utf-8", "replace"
            )
            offset += length
        elif tag == 7:
            class_indices.append(struct.unpack_from(">H", data, offset)[0])
            offset += 2
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            offset += 4
        elif tag in (5, 6):
            offset += 8
            index += 1
        elif tag in (8, 16, 19, 20):
            offset += 2
        elif tag == 15:
            offset += 3
        else:
            raise ValueError(f"Unsupported constant-pool tag {tag}")
        index += 1
    return major, {utf8_entries[i] for i in class_indices if i in utf8_entries}


def audit(compiler, android_jar=None):
    versions = Counter()
    references = defaultdict(set)
    with ZipFile(compiler) as archive:
        classfiles = sorted(n for n in archive.namelist() if n.endswith(".class"))
        for name in classfiles:
            major, symbols = referenced_classes(archive.read(name))
            versions[str(major)] += 1
            for symbol in symbols:
                if symbol.startswith(("java/", "javax/", "sun/", "jdk/")):
                    references[symbol].add(name)

    categories = {}
    for category, prefixes in GROUPS.items():
        symbols = sorted(name for name in references if name.startswith(prefixes))
        categories[category] = {
            "referenced_types": len(symbols),
            "examples": [
                {"type": name, "referencing_classes": len(references[name])}
                for name in symbols[:15]
            ],
        }

    result = {
        "compiler": str(compiler),
        "classfiles": len(classfiles),
        "classfile_versions": dict(sorted(versions.items())),
        "advisory_java_refs": categories,
    }
    if android_jar:
        with ZipFile(android_jar) as archive:
            available = {
                name[:-6] for name in archive.namelist() if name.endswith(".class")
            }
        absent = sorted(name for name in references if name not in available)
        result["android_jar"] = str(android_jar)
        result["platform_missing_reference_count"] = len(absent)
        result["platform_missing_references"] = [
            {"type": name, "referencing_classes": len(references[name])}
            for name in absent
        ]
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("compiler", type=Path)
    parser.add_argument("--android-jar", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()

    result = audit(args.compiler, args.android_jar)
    summary = {key: value for key, value in result.items()
               if key != "platform_missing_references"}
    print(json.dumps(summary, indent=2))
    print("Advisory static reference inventory; not a runtime linkage test.")
    if args.output:
        args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
