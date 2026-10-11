// SPDX-License-Identifier: BSD-3-Clause
// In-memory API contract; deliberately avoids java.nio.file in compiler calls.
import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.ClassFileResourceProvider;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.origin.Origin;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class D8MemoryLibrarySmoke {
  private static final Set<String> LIBRARY_CLASSES = new HashSet<>(Arrays.asList(
      "Ljava/lang/Object;", "Ljava/lang/String;", "Ljava/lang/Throwable;",
      "Ljava/lang/Cloneable;", "Ljava/io/Serializable;", "Ljava/lang/Class;"));

  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      throw new IllegalArgumentException("Expected two classfile paths");
    }
    ClassFileResourceProvider library = new ClassFileResourceProvider() {
      @Override
      public Set<String> getClassDescriptors() {
        return LIBRARY_CLASSES;
      }

      @Override
      public ProgramResource getProgramResource(String descriptor) {
        if (!LIBRARY_CLASSES.contains(descriptor)) {
          return null;
        }
        String name = "/" + descriptor.substring(1, descriptor.length() - 1) + ".class";
        try (InputStream stream = Object.class.getResourceAsStream(name)) {
          if (stream == null) {
            throw new IllegalStateException("Missing fixture library class " + descriptor);
          }
          return ProgramResource.fromBytes(
              Origin.unknown(), ProgramResource.Kind.CF,
              stream.readAllBytes(), Collections.singleton(descriptor));
        } catch (java.io.IOException e) {
          throw new IllegalStateException("Cannot read fixture library " + descriptor, e);
        }
      }
    };

    Set<String> included = new HashSet<>();
    DexIndexedConsumer consumer = new DexIndexedConsumer() {
      @Override
      public synchronized void accept(
          int index, ByteDataView data, Set<String> descriptors, DiagnosticsHandler handler) {
        byte[] dex = data.copyByteData();
        if (index != 0 || dex.length < 112 || dex[0] != 'd' || dex[1] != 'e' ||
            dex[2] != 'x' || dex[3] != '\n') {
          throw new AssertionError("Invalid indexed DEX");
        }
        included.addAll(descriptors);
      }

      @Override
      public void finished(DiagnosticsHandler handler) {}
    };

    D8Command.Builder builder = D8Command.builder();
    for (String file : args) {
      builder.addClassProgramData(Files.readAllBytes(Paths.get(file)), Origin.unknown());
    }
    builder.addLibraryResourceProvider(library);
    builder.setMinApiLevel(23);
    builder.setMode(CompilationMode.RELEASE);
    builder.setProgramConsumer(consumer);
    D8.run(builder.build());
    if (!included.contains("Lsample/Legacy;") ||
        !included.contains("Lsample/Modern;")) {
      throw new AssertionError("Missing program classes: " + included);
    }
    System.out.println("D8 API-only: in-memory class and library conversion passed");
  }
}
