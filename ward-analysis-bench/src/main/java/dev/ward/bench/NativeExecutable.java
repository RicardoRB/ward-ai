package dev.ward.bench;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Checks that the benchmark target is a native executable, not a JVM build, and describes it. */
record NativeExecutable(long sizeBytes, String sha256) {

  private static final int MAGIC_BYTES = 4;
  private static final byte[] ELF = {0x7F, 'E', 'L', 'F'};
  private static final byte[] MACH_O_64 = {(byte) 0xCF, (byte) 0xFA, (byte) 0xED, (byte) 0xFE};
  private static final byte[] MACH_O_32 = {(byte) 0xCE, (byte) 0xFA, (byte) 0xED, (byte) 0xFE};
  private static final byte[] PE = {'M', 'Z', 0, 0};

  /** Fails before any measurement unless {@code binary} is an existing executable native file. */
  static NativeExecutable verify(final Path binary) {
    final String expected = "ward-cli/target/ward";
    if (!Files.isRegularFile(binary) || !Files.isExecutable(binary)) {
      throw new IllegalArgumentException(
          "Expected an executable native Ward file such as " + expected);
    }
    if (!hasNativeMagic(binary)) {
      throw new IllegalArgumentException(
          "The file is not a native executable; expected a native build such as "
              + expected
              + ", not a JVM jar or class file");
    }
    try {
      return new NativeExecutable(Files.size(binary), sha256(binary));
    } catch (IOException e) {
      throw new UncheckedIOException("Could not describe the native executable", e);
    }
  }

  private static boolean hasNativeMagic(final Path binary) {
    final byte[] head = new byte[MAGIC_BYTES];
    try (InputStream in = Files.newInputStream(binary)) {
      final int read = in.readNBytes(head, 0, MAGIC_BYTES);
      if (read < MAGIC_BYTES) {
        return false;
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read the executable", e);
    }
    return java.util.Arrays.equals(head, ELF)
        || java.util.Arrays.equals(head, MACH_O_64)
        || java.util.Arrays.equals(head, MACH_O_32)
        || java.util.Arrays.equals(head, PE);
  }

  private static String sha256(final Path binary) throws IOException {
    try {
      final MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(Files.readAllBytes(binary));
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }
}
