package dev.ward.infrastructure.format;

import dev.ward.analysis.domain.format.FormatOption;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.domain.config.WardException;
import dev.ward.domain.format.FormatConfiguration;
import dev.ward.domain.format.FormatSettings;
import dev.ward.domain.format.FormatTarget;
import dev.ward.infrastructure.rule.FileSystemSourceUnits;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Selects the files to format: engine-language sources that the configuration does not exclude. */
public final class FormatFileSelection {

  private final FormatConfiguration configuration;

  /**
   * Creates the selection over one configuration.
   *
   * @param configuration the configuration that resolves each file's settings
   * @throws NullPointerException if the configuration is missing
   */
  public FormatFileSelection(final FormatConfiguration configuration) {
    this.configuration = Objects.requireNonNull(configuration, "configuration");
  }

  /**
   * Lists the files to format under a project root, sorted by path. A configuration error in any
   * file fails the whole selection, so nothing is formatted.
   *
   * @param root the project root
   * @param declared the options of each language that has a formatter
   * @return the selected files
   * @throws dev.ward.domain.format.FormatConfigurationException if a configuration file is invalid
   */
  public List<FormatTarget> select(
      final Path root, final Map<AnalysisLanguage, List<FormatOption>> declared) {
    final Map<String, AnalysisLanguage> found = new TreeMap<>();
    try {
      Files.walkFileTree(root, new Collector(root, declared.keySet(), found));
    } catch (final IOException exception) {
      throw new WardException("Could not list the source files under " + root + ".", exception);
    }
    final List<FormatTarget> targets = new ArrayList<>();
    for (final Map.Entry<String, AnalysisLanguage> entry : found.entrySet()) {
      final AnalysisLanguage language = entry.getValue();
      final FormatSettings settings =
          configuration.resolve(entry.getKey(), language, declared.get(language));
      if (settings.enabled()) {
        targets.add(new FormatTarget(entry.getKey(), language, settings));
      }
    }
    return List.copyOf(targets);
  }

  /** Records each source file in a wanted language, skipping build output and hidden folders. */
  private static final class Collector extends SimpleFileVisitor<Path> {
    private final Path root;
    private final Set<AnalysisLanguage> languages;
    private final Map<String, AnalysisLanguage> found;

    Collector(
        final Path root,
        final Set<AnalysisLanguage> languages,
        final Map<String, AnalysisLanguage> found) {
      super();
      this.root = root;
      this.languages = languages;
      this.found = found;
    }

    @Override
    public FileVisitResult preVisitDirectory(
        final Path directory, final BasicFileAttributes attributes) {
      if (directory.equals(root)) {
        return FileVisitResult.CONTINUE;
      }
      return FileSystemSourceUnits.skipsDirectory(nameOf(directory))
          ? FileVisitResult.SKIP_SUBTREE
          : FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(final Path file, final BasicFileAttributes attributes) {
      final AnalysisLanguage language = FileSystemSourceUnits.language(nameOf(file));
      if (language != null && languages.contains(language) && Files.isRegularFile(file)) {
        found.put(root.relativize(file).toString().replace('\\', '/'), language);
      }
      return FileVisitResult.CONTINUE;
    }

    private static String nameOf(final Path path) {
      final Path name = path.getFileName();
      return name == null ? "" : name.toString();
    }
  }
}
