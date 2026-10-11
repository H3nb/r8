# D8-J2ME Android 6 feasibility test

A standalone Android application exercises the source-built API-only D8
compiler with in-memory class and library providers on API 23.

Toolchain: AGP 8.9.2, Gradle 8.11.1, JDK 17, Android 35 compile SDK,
`minSdk 23`, and `com.android.tools:desugar_jdk_libs_nio:2.1.5`.

The test application does not depend on JL-Mod Plus, its runtime, or its DX
converter. A successful run prints `D8_J2ME_SMOKE: PASS milliseconds=...`
to logcat after converting the Java 8 fixture. The existing unadapted
`app_process` test is deliberately kept separate for comparison.

The first CI success was GitHub Actions run 38160508866. The Android 6
emulator completed the in-memory conversion in 281 ms for a single small
class. This is not a game conversion benchmark. Neither the complete
J2ME compatibility transform nor launch-time gameplay is exercised.
