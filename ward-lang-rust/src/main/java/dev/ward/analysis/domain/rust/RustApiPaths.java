package dev.ward.analysis.domain.rust;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Maps the path of a Rust source file to the module it declares and says which files count. */
final class RustApiPaths {
  private static final String SRC = "src/";
  private static final String EXTENSION = ".rs";
  private static final String MODULE_FILE = "mod";
  private static final String ROOT_FILE = "lib";
  private static final String ROOT_NAME = "crate";
  private static final List<String> NOT_API = List.of("main", "build");
  private static final String BIN = "bin/";

  private RustApiPaths() {}

  /** Returns whether the file is outside the library API: a binary, a build script or a test. */
  static boolean skipped(final String path) {
    final String rest = rest(path);
    return !path.endsWith(EXTENSION)
        || NOT_API.contains(rest.substring(0, rest.length() - EXTENSION.length()))
        || rest.startsWith(BIN);
  }

  /** Returns whether the file is the root of a library crate. */
  static boolean isRoot(final String path) {
    return (ROOT_FILE + EXTENSION).equals(rest(path));
  }

  /**
   * Returns the dotted module path the file declares, starting with the crate name: {@code crate}
   * for {@code src/lib.rs} and the directory name for a workspace member.
   */
  static String module(final String path) {
    final List<String> segments = segments(rest(path));
    return crate(path) + (segments.isEmpty() ? "" : "." + String.join(".", segments));
  }

  private static List<String> segments(final String rest) {
    final String stem = rest.substring(0, rest.length() - EXTENSION.length());
    final List<String> parts = Arrays.stream(stem.split("/")).collect(Collectors.toList());
    final String last = parts.getLast();
    if (MODULE_FILE.equals(last) || ROOT_FILE.equals(last) && parts.size() == 1) {
      parts.removeLast();
    }
    return parts;
  }

  private static String crate(final String path) {
    final int at = sourceRoot(path);
    if (at <= 0) {
      return ROOT_NAME;
    }
    final String prefix = path.substring(0, at - 1);
    return prefix.substring(prefix.lastIndexOf('/') + 1).replace('.', '_');
  }

  private static String rest(final String path) {
    final int at = sourceRoot(path);
    return at < 0 ? path : path.substring(at + SRC.length());
  }

  /** The index where the {@code src/} directory starts, or -1 when the path has none. */
  private static int sourceRoot(final String path) {
    if (path.startsWith(SRC)) {
      return 0;
    }
    final int inner = path.indexOf("/" + SRC);
    return inner < 0 ? -1 : inner + 1;
  }
}
