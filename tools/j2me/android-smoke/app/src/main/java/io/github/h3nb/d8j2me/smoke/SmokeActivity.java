package io.github.h3nb.d8j2me.smoke;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.ClassFileResourceProvider;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.origin.Origin;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class SmokeActivity extends Activity {
  private static final String TAG = "D8_J2ME_SMOKE";
  private static final Set<String> PLATFORM_TYPES = new HashSet<>(Arrays.asList(
      "Ljava/lang/Object;", "Ljava/lang/Throwable;", "Ljava/lang/String;",
      "Ljava/lang/Class;", "Ljava/lang/Cloneable;", "Ljava/io/Serializable;"));

  private byte[] readAsset(String path) throws IOException {
    try (InputStream in = getAssets().open(path);
         ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[8192];
      int size;
      while ((size = in.read(buffer)) != -1) {
        out.write(buffer, 0, size);
      }
      return out.toByteArray();
    }
  }

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    TextView view = new TextView(this);
    view.setText("Testing D8-J2ME on API " + android.os.Build.VERSION.SDK_INT);
    setContentView(view);
    new Thread(() -> {
      try {
        long start = System.nanoTime();
        ClassFileResourceProvider library = new ClassFileResourceProvider() {
          @Override
          public Set<String> getClassDescriptors() {
            return PLATFORM_TYPES;
          }

          @Override
          public ProgramResource getProgramResource(String descriptor) {
            if (!PLATFORM_TYPES.contains(descriptor)) {
              return null;
            }
            String path = descriptor.substring(1, descriptor.length() - 1) + ".class";
            try {
              return ProgramResource.fromBytes(
                  Origin.unknown(), ProgramResource.Kind.CF,
                  readAsset(path), Collections.singleton(descriptor));
            } catch (IOException e) {
              throw new IllegalStateException("Cannot read library class " + path, e);
            }
          }
        };
        final Set<String> output = new HashSet<>();
        DexIndexedConsumer consumer = new DexIndexedConsumer() {
          @Override
          public synchronized void accept(
              int index, ByteDataView data, Set<String> descriptors,
              DiagnosticsHandler handler) {
            byte[] dex = data.copyByteData();
            if (index != 0 || dex.length < 112 || dex[0] != 'd' || dex[1] != 'e' ||
                dex[2] != 'x' || dex[3] != '\n') {
              throw new AssertionError("Invalid DEX");
            }
            output.addAll(descriptors);
          }

          @Override
          public void finished(DiagnosticsHandler handler) {}
        };
        D8Command.Builder builder = D8Command.builder();
        builder.addClassProgramData(readAsset("sample/Legacy.class"), Origin.unknown());
        builder.addLibraryResourceProvider(library);
        builder.setMinApiLevel(23);
        builder.setMode(CompilationMode.RELEASE);
        builder.setProgramConsumer(consumer);
        D8.run(builder.build());
        if (!output.contains("Lsample/Legacy;")) {
          throw new AssertionError("Missing Legacy class");
        }
        Log.i(TAG, "PASS milliseconds=" + ((System.nanoTime() - start) / 1000000L));
      } catch (Throwable error) {
        Log.e(TAG, "FAIL", error);
      }
    }, "d8-j2me-smoke").start();
  }
}
