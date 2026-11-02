package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.application.gate.AnalysisSourceUnits;
import dev.ward.domain.config.WardException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;

/** Reads source files from the project working tree, skipping build output and hidden folders. */
public final class FileSystemSourceUnits implements AnalysisSourceUnits {

  private static final Map<String, AnalysisLanguage> LANGUAGES_BY_EXTENSION =
      Map.ofEntries(
          Map.entry("java", AnalysisLanguage.JAVA),
          Map.entry("kt", AnalysisLanguage.KOTLIN),
          Map.entry("ts", AnalysisLanguage.TYPESCRIPT),
          Map.entry("tsx", AnalysisLanguage.TYPESCRIPT),
          Map.entry("js", AnalysisLanguage.JAVASCRIPT),
          Map.entry("jsx", AnalysisLanguage.JAVASCRIPT),
          Map.entry("mjs", AnalysisLanguage.JAVASCRIPT),
          Map.entry("cjs", AnalysisLanguage.JAVASCRIPT),
          Map.entry("py", AnalysisLanguage.PYTHON),
          Map.entry("swift", AnalysisLanguage.SWIFT));
  private static final Set<String> SKIPPED_DIRECTORIES =
      Set.of("target", "build", "dist", "node_modules", "mutants");

  /** Creates the stateless reader that Dagger injects. */
  @Inject
  public FileSystemSourceUnits() {}

  @Override
  public List<SourceUnit> read(final Path root, final Set<AnalysisLanguage> languages) {
    if (languages.isEmpty()) {
      return List.of();
    }
    final List<SourceUnit> units = new ArrayList<>();
    try {
      Files.walkFileTree(root, new Collector(root, languages, units));
    } catch (final IOException exception) {
      throw new WardException("Could not list the source files under " + root + ".", exception);
    }
    units.sort(Comparator.comparing(SourceUnit::path));
    return List.copyOf(units);
  }

  /** Adds each source file in a wanted language to the units. */
  private static final class Collector extends SimpleFileVisitor<Path> {
    private final Path root;
    private final Set<AnalysisLanguage> languages;
    private final List<SourceUnit> units;

    Collector(
        final Path root, final Set<AnalysisLanguage> languages, final List<SourceUnit> units) {
      super();
      this.root = root;
      this.languages = languages;
      this.units = units;
    }

    @Override
    public FileVisitResult preVisitDirectory(
        final Path directory, final BasicFileAttributes attributes) {
      final String name = nameOf(directory);
      final boolean skipped =
          !directory.equals(root) && (name.startsWith(".") || SKIPPED_DIRECTORIES.contains(name));
      return skipped ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(final Path file, final BasicFileAttributes attributes)
        throws IOException {
      final AnalysisLanguage language = language(nameOf(file));
      if (language != null && languages.contains(language) && attributes.isRegularFile()) {
        final String text = Files.readString(file, StandardCharsets.UTF_8);
        units.add(new SourceUnit(relative(file), language, text));
      }
      return FileVisitResult.CONTINUE;
    }

    private String relative(final Path file) {
      return root.relativize(file).toString().replace('\\', '/');
    }
  }

  /** Returns the last name of {@code path}, or an empty string for a root with no name. */
  private static String nameOf(final Path path) {
    final Path name = path.getFileName();
    return name == null ? "" : name.toString();
  }

  /** Returns the language of a file name by its extension, or {@code null} when none is known. */
  static AnalysisLanguage language(final String fileName) {
    final int dot = fileName.lastIndexOf('.');
    if (dot < 0) {
      return null;
    }
    return LANGUAGES_BY_EXTENSION.get(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
  }
}
