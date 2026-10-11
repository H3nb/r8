# D8-J2ME: first source-built feasibility target

This branch starts from Google R8/D8 9.5.24. JL-Mod Plus currently uses
an ASM compatibility transform followed by DX. D8-J2ME is intended to replace
**only the dexing backend**, not the transform, MIDlet loader, or resource store.

## Bootstrap artifact

```sh
python3 tools/gradle.py --no-internal -q :dist:assembleD8J2ME
python3 tools/j2me/verify_d8_j2me.py build/libs/d8-j2me.jar
```

The new target uses the existing upstream compiler build and bootstrap
optimizer, rooted at D8 command-line and in-memory conversion entry points. A custom\ninput-JAR manifest prevents the SwissArmyKnife launcher from retaining R8. It removes
unreachable R8-only functionality from the delivered artifact without
deleting shared source prematurely. The initial smoke checks the D8 version,
the absence of the R8 and CompatProguard entry points, and conversion of a
mixed Java 1.1/Java 8 synthetic JAR (including multi-dimensional arrays,
synchronization, and a non-ZIP resource named `.jar`).

This first artifact is **not ready for JL-Mod Plus integration**.
In particular, the public programmatic D8 API, CLDC `StackMap` inputs,
Android API 23 runtime/linkage, memory usage, and the real-game corpus have
not yet passed qualification. No user game files are stored in this repository.

## Exit criteria before replacing DX

1. Establish an Android API 23 runtime test for an Android-packaged compiler.
   Host-JVM success is not enough; JDK-only library calls must be audited.
2. Preserve and smoke-test D8's programmatic API for in-memory transformed
   class resources, bounded diagnostics, and indexed DEX output.
3. Compare conversions against DX using a locally supplied J2ME corpus:
   legacy classfile versions, CLDC preverification, reflection/static fields,
   obfuscated classes, malformed input, and large methods.
4. Validate installed MIDlet execution, reconversion safety, peak memory,
   conversion duration, APK size, and multi-DEX loading on actual Android.
5. Only then remove R8-exclusive source based on reachability and integration
   tests. Keep shared D8/R8 internals until no longer needed.

The root BSD-3-Clause LICENSE and inherited upstream notices remain intact.


Run the local corpus harness after obtaining a source-built artifact:

```sh
python3 tools/j2me/run_corpus.py build/libs/d8-j2me.jar /path/to/J2ME-jars --lib "$JAVA_HOME"
```

Keep copyrighted JARs outside the repository. Successful host-JVM conversion
does not establish Android API 23 runtime compatibility or MIDlet execution.
