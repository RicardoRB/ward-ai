package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.ProjectFiles;
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
import java.util.regex.Pattern;
import javax.inject.Inject;

/**
 * Reads source files from the project working tree, skipping build output, hidden folders, and JVM
 * files under a Maven or Gradle resource folder, which the build never compiles.
 */
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
  private static final Set<AnalysisLanguage> JVM_LANGUAGES =
      Set.of(AnalysisLanguage.JAVA, AnalysisLanguage.KOTLIN);
  private static final Pattern RESOURCE_FOLDER = Pattern.compile("(^|/)src/[^/]+/resources/");

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

  @Override
  public ProjectFiles manifests(final Path root, final Set<String> names) {
    return FileSystemManifests.read(root, names);
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
      final boolean skipped = !directory.equals(root) && skipsDirectory(nameOf(directory));
      return skipped ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(final Path file, final BasicFileAttributes attributes)
        throws IOException {
      final AnalysisLanguage language = language(nameOf(file));
      final String path = relative(file);
      if (language != null
          && languages.contains(language)
          && attributes.isRegularFile()
          && !isJvmResource(language, path)) {
        final String text = Files.readString(file, StandardCharsets.UTF_8);
        units.add(new SourceUnit(path, language, text));
      }
      return FileVisitResult.CONTINUE;
    }

    private String relative(final Path file) {
      return root.relativize(file).toString().replace('\\', '/');
    }
  }

  /** Java or Kotlin under {@code src/<set>/resources} is data for the build, not a source file. */
  private static boolean isJvmResource(final AnalysisLanguage language, final String path) {
    return JVM_LANGUAGES.contains(language) && RESOURCE_FOLDER.matcher(path).find();
  }

  /** Returns the last name of {@code path}, or an empty string for a root with no name. */
  private static String nameOf(final Path path) {
    final Path name = path.getFileName();
    return name == null ? "" : name.toString();
  }

  /**
   * Tells whether a folder is skipped: hidden folders, and build output such as {@code target} or
   * {@code node_modules}.
   *
   * @param name the folder name
   * @return true when the folder and everything under it is skipped
   */
  public static boolean skipsDirectory(final String name) {
    return name.startsWith(".") || SKIPPED_DIRECTORIES.contains(name);
  }

  /**
   * Returns the language of a file name by its extension, or {@code null} when none is known.
   *
   * @param fileName the file name
   * @return the language, or null
   */
  public static AnalysisLanguage language(final String fileName) {
    final int dot = fileName.lastIndexOf('.');
    if (dot < 0) {
      return null;
    }
    return LANGUAGES_BY_EXTENSION.get(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
  }
}
