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
optimizer, rooted at D8 command-line and in-memory conversion entry points. A custom
input-JAR manifest prevents the SwissArmyKnife launcher from retaining R8. It removes
unreachable R8-only functionality from the delivered artifact without
deleting shared source prematurely. The initial smoke checks the D8 version,
the absence of the R8 and CompatProguard entry points, and conversion of a
mixed Java 1.1/Java 8 synthetic JAR (including multi-dimensional arrays,
synchronization, and a non-ZIP resource named `.jar`).

This first artifact is **not ready for JL-Mod Plus integration**.
Host-JVM tests have validated the CLI and in-memory D8 API. All 65 raw
J2ME sample JARs (2,174 classes) produced valid DEX with both routes, with
matching class-definition counts, Adler-32 checksums, and SHA-1 signatures.
The source-built API-only library was also loaded by a small Android app
with NIO core-library desugaring: the Android 6 (API 23) emulator successfully
converted a Java 8 class through the in-memory API (281 ms in one smoke run).
This establishes a working Android 6 packaging path, **not** whole-game runtime
compatibility. The ASM-transformed inputs, memory/performance comparison with
DX, and device gameplay remain outstanding. No original game files are stored
in this repository.

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

## Android 6 feasibility finding

The unmodified source-built D8 compiler was dexed on the host with
`--min-api 23` and executed with `app_process` on an Android 6 x86_64
emulator. Host-side conversion succeeded, but the Android process could
not initialize the compiler:

```
java.lang.NoClassDefFoundError: com.android.tools.r8.BaseCompilerCommand$Builder$1
```

The host dexer also warned about unavailable API 23 Java library types,
including `java.util.function`, `java.util.Spliterator`, and
`java.util.PrimitiveIterator`. Increasing emulator RAM/heap did not
resolve the issue. This is a deliberately strict, **unadapted**
compiler-runtime baseline, not a final judgment on a correctly
desugared and packaged Android integration.

A programmatic-only variant has now passed the Android 6 smoke test when
built into an APK with `desugar_jdk_libs_nio:2.1.5`. The older `app_process`
failure is retained here as a warning against packaging without desugaring.


### Reproducing the API-only game corpus test

The compiler API tests accept transformed or original class bytes in memory.
For JAR corpus checks on a host JDK, the library provider reads
`java.base.jmod` instead of passing a filesystem path into D8:

```sh
javac -cp build/libs/d8-j2me-api.jar -d build/j2me-tests tools/j2me/D8MemoryCorpus.java
java -cp "build/libs/d8-j2me-api.jar:build/j2me-tests" D8MemoryCorpus \
  "$JAVA_HOME/jmods/java.base.jmod" /path/to/game.jar
```

The host-JDK library provider is a test fixture, not the Android library
provider that JL-Mod Plus will use. Java classfile references missing from
that fixture are diagnostic warnings, not proof of a runtime defect.
