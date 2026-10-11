// SPDX-License-Identifier: BSD-3-Clause
// Verify the supported in-memory D8-J2ME class-to-DEX API.
import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.origin.Origin;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public final class D8ProgrammaticSmoke {
  public static void main(String[] args) throws Exception {
    if (args.length != 3) {
      throw new IllegalArgumentException("Expected: first.class second.class library");
    }
    final Set<String> written = new HashSet<>();
    DexIndexedConsumer consumer = new DexIndexedConsumer() {
      @Override
      public synchronized void accept(int index, ByteDataView view, Set<String> descriptors,
                                      DiagnosticsHandler handler) {
        byte[] data = view.copyByteData();
        if (index != 0 || data.length < 112 || data[0] != 'd' || data[1] != 'e' ||
            data[2] != 'x' || data[3] != '\n') {
          throw new AssertionError("Unexpected indexed DEX output");
        }
        written.addAll(descriptors);
      }

      @Override
      public void finished(DiagnosticsHandler handler) {}
    };
    D8Command.Builder builder = D8Command.builder();
    builder.addClassProgramData(Files.readAllBytes(Paths.get(args[0])), Origin.unknown());
    builder.addClassProgramData(Files.readAllBytes(Paths.get(args[1])), Origin.unknown());
    builder.addLibraryFiles(Paths.get(args[2]));
    builder.setMinApiLevel(23);
    builder.setMode(CompilationMode.RELEASE);
    builder.setProgramConsumer(consumer);
    D8.run(builder.build());
    if (!written.contains("Lsample/Legacy;") || !written.contains("Lsample/Modern;")) {
      throw new AssertionError("Missing classes from in-memory DEX: " + written);
    }
    System.out.println("In-memory D8 API produced indexed DEX for both classes");
  }
}
