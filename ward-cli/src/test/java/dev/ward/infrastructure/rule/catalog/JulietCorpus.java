package dev.ward.infrastructure.rule.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/**
 * The Juliet Java 1.3 test suite (NIST SARD test suite 111, public domain), read from an extracted
 * copy. The suite is never part of the repository: a run downloads it and checks the archive.
 */
final class JulietCorpus {

  /** The SHA-256 of {@code 2017-10-01-juliet-test-suite-for-java-v1-3.zip}. */
  static final String ARCHIVE_SHA256 =
      "d985f4177c2bcd7b03455a05c1c8f2e755f55c9eb250accd052f05f877347e60";

  private static final int BUFFER = 8192;

  private final Path testcases;

  JulietCorpus(final Path root) {
    final Path nested = root.resolve("Java").resolve("src").resolve("testcases");
    testcases = Files.isDirectory(nested) ? nested : root.resolve("src").resolve("testcases");
  }

  /** Returns the SHA-256 of {@code archive} in lower-case hexadecimal. */
  static String sha256(final Path archive) {
    try (InputStream in = Files.newInputStream(archive)) {
      final MessageDigest digest = MessageDigest.getInstance("SHA-256");
      final byte[] buffer = new byte[BUFFER];
      for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
        digest.update(buffer, 0, read);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (final IOException exception) {
      throw new UncheckedIOException(exception);
    } catch (final NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is part of every JDK", exception);
    }
  }

  /**
   * Returns the Java files of the cases of one CWE, sorted by path.
   *
   * @param cwe the number of the weakness, such as {@code 89}
   */
  List<Path> cases(final int cwe) {
    final String prefix = "CWE" + cwe + "_";
    try (Stream<Path> directories = Files.list(testcases)) {
      return directories
          .filter(Files::isDirectory)
          .filter(directory -> directory.getFileName().toString().startsWith(prefix))
          .flatMap(JulietCorpus::javaFiles)
          .sorted()
          .toList();
    } catch (final IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  /** A case is a {@code CWE<number>_...java} file; {@code Main.java} is a generated driver. */
  private static boolean isCase(final Path file) {
    final String name = file.getFileName().toString();
    return name.startsWith("CWE") && name.endsWith(".java");
  }

  private static Stream<Path> javaFiles(final Path directory) {
    try (Stream<Path> files = Files.walk(directory)) {
      return files.filter(JulietCorpus::isCase).toList().stream();
    } catch (final IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }
}
