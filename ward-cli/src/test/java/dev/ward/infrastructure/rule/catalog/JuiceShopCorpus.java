package dev.ward.infrastructure.rule.catalog;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The OWASP Juice Shop source (MIT license), read from an extracted release archive. The source is
 * never part of the repository and is only read: nothing in it is installed or run.
 */
final class JuiceShopCorpus {

  /** The release the committed report measures. */
  static final String TAG = "v20.4.0";

  /** The SHA-256 of the source archive {@code juice-shop-v20.4.0.tar.gz} of that tag. */
  static final String ARCHIVE_SHA256 =
      "90551e4bcf83e7019de5bfcba8ab28b35df35b9243d91fbdc7c7e6db30c23e4e";

  private static final Set<String> TYPESCRIPT = Set.of(".ts", ".tsx", ".mts", ".cts");
  private static final Set<String> JAVASCRIPT = Set.of(".js", ".jsx", ".mjs", ".cjs");
  private static final String DECLARATIONS = ".d.ts";
  private static final String DEPENDENCIES = "node_modules";

  /** The folders and minified scripts that the gate's source reader skips too. */
  private static final Set<String> SKIPPED_FOLDERS = Set.of("target", "build", "dist", "mutants");

  private static final Pattern MINIFIED = Pattern.compile("\\.min\\.[cm]?js$");

  private final Path root;

  /** Reads the sources below {@code root}, the directory the archive was extracted to. */
  JuiceShopCorpus(final Path root) {
    this.root = root;
  }

  /** Returns the TypeScript and JavaScript sources, with root-relative paths, in path order. */
  List<SourceUnit> units() throws IOException {
    try (Stream<Path> files = Files.walk(root)) {
      return files
          .filter(Files::isRegularFile)
          .filter(file -> !root.relativize(file).toString().contains(DEPENDENCIES))
          .filter(file -> !file.toString().endsWith(DECLARATIONS))
          .filter(file -> !MINIFIED.matcher(file.getFileName().toString()).find())
          .filter(file -> !skippedFolder(file))
          .filter(file -> language(file) != null)
          .sorted()
          .map(this::unit)
          .toList();
    }
  }

  private boolean skippedFolder(final Path file) {
    for (final Path part : root.relativize(file.getParent())) {
      if (SKIPPED_FOLDERS.contains(part.toString())) {
        return true;
      }
    }
    return false;
  }

  private SourceUnit unit(final Path file) {
    try {
      return new SourceUnit(
          root.relativize(file).toString().replace('\\', '/'),
          language(file),
          Files.readString(file, StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static AnalysisLanguage language(final Path file) {
    final String name = file.getFileName().toString();
    final String extension = name.substring(name.lastIndexOf('.') + 1);
    if (TYPESCRIPT.contains("." + extension)) {
      return AnalysisLanguage.TYPESCRIPT;
    }
    return JAVASCRIPT.contains("." + extension) ? AnalysisLanguage.JAVASCRIPT : null;
  }
}
