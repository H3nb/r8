// SPDX-License-Identifier: BSD-3-Clause
// Host-only corpus runner; game JARs remain outside the repository.
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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.zip.Adler32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class D8MemoryCorpus {
  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      throw new IllegalArgumentException("Expected java.base.jmod and game.jar");
    }
    try (ZipFile jmod = new ZipFile(args[0]); ZipFile jar = new ZipFile(args[1])) {
      Set<String> descriptors = new HashSet<>();
      jmod.stream()
          .filter(e -> e.getName().startsWith("classes/") &&
              e.getName().endsWith(".class") &&
              !e.getName().equals("classes/module-info.class"))
          .forEach(e -> descriptors.add(
              "L" + e.getName().substring(8, e.getName().length() - 6) + ";"));
      ClassFileResourceProvider library = new ClassFileResourceProvider() {
        @Override
        public Set<String> getClassDescriptors() {
          return descriptors;
        }

        @Override
        public ProgramResource getProgramResource(String descriptor) {
          if (!descriptors.contains(descriptor)) {
            return null;
          }
          String entry = "classes/" + descriptor.substring(1, descriptor.length() - 1) + ".class";
          try (InputStream in = jmod.getInputStream(jmod.getEntry(entry))) {
            return ProgramResource.fromBytes(
                Origin.unknown(), ProgramResource.Kind.CF, in.readAllBytes(),
                Collections.singleton(descriptor));
          } catch (java.io.IOException e) {
            throw new IllegalStateException("Library read failed: " + descriptor, e);
          }
        }
      };

      SortedMap<Integer, byte[]> output = Collections.synchronizedSortedMap(new TreeMap<>());
      DexIndexedConsumer consumer = new DexIndexedConsumer() {
        @Override
        public void accept(
            int index, ByteDataView data, Set<String> classes, DiagnosticsHandler handler) {
          output.put(index, data.copyByteData());
        }

        @Override
        public void finished(DiagnosticsHandler handler) {}
      };
      D8Command.Builder builder = D8Command.builder();
      int classCount = 0;
      Enumeration<? extends ZipEntry> entries = jar.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
          try (InputStream in = jar.getInputStream(entry)) {
            builder.addClassProgramData(in.readAllBytes(), Origin.unknown());
            classCount++;
          }
        }
      }
      builder.addLibraryResourceProvider(library);
      builder.setMinApiLevel(23);
      builder.setMode(CompilationMode.RELEASE);
      builder.setProgramConsumer(consumer);
      long started = System.nanoTime();
      D8.run(builder.build());

      int dexBytes = 0;
      int dexClasses = 0;
      for (byte[] dex : output.values()) {
        ByteBuffer header = ByteBuffer.wrap(dex).order(ByteOrder.LITTLE_ENDIAN);
        if (dex.length < 112 || dex[0] != 'd' || dex[1] != 'e' ||
            dex[2] != 'x' || dex[3] != '\n' ||
            header.getInt(32) != dex.length || header.getInt(36) != 112) {
          throw new AssertionError("Invalid DEX header");
        }
        Adler32 adler = new Adler32();
        adler.update(dex, 12, dex.length - 12);
        if (header.getInt(8) != (int) adler.getValue()) {
          throw new AssertionError("Invalid DEX checksum");
        }
        MessageDigest sha = MessageDigest.getInstance("SHA-1");
        sha.update(dex, 32, dex.length - 32);
        if (!Arrays.equals(Arrays.copyOfRange(dex, 12, 32), sha.digest())) {
          throw new AssertionError("Invalid DEX signature");
        }
        dexClasses += header.getInt(96);
        dexBytes += dex.length;
      }
      if (dexClasses < classCount || output.isEmpty()) {
        throw new AssertionError("Missing DEX class definitions");
      }
      System.out.println(
          "SUCCESS classes=" + classCount + " dex_classes=" + dexClasses +
          " dex_count=" + output.size() + " bytes=" + dexBytes +
          " ms=" + ((System.nanoTime() - started) / 1000000));
    }
  }
}
