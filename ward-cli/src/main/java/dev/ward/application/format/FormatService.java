package dev.ward.application.format;

import dev.ward.analysis.domain.format.FormatOption;
import dev.ward.analysis.domain.format.FormatterRegistry;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.port.AnalysisCache;
import dev.ward.domain.format.FormatTarget;
import dev.ward.domain.format.FormatTargets;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;

/**
 * Formats the project's source files with the native formatters, for {@code ward format} and the
 * {@code ward-format} gate. Each file's settings come from its {@code .editorconfig}, and a file is
 * written only when its formatted output passes every safety check.
 */
public final class FormatService {
  /** The message of a finding for a file that the formatter would change. */
  public static final String NOT_FORMATTED = FormatFileEvaluator.NOT_FORMATTED;

  private final FormatterRegistry registry;
  private final FormatTargets targets;
  private final FormatCaches caches;
  private final FormatFileEvaluator evaluator;

  /** Creates the service over the formatters, the file selection and the result cache. */
  @Inject
  public FormatService(
      final FormatterRegistry registry,
      final FormatTargets targets,
      final FormatCaches caches,
      final FormatFileEvaluator evaluator) {
    this.registry = registry;
    this.targets = targets;
    this.caches = caches;
    this.evaluator = evaluator;
  }

  /**
   * Formats the files under the given paths, or every selected file when no path is given. Relative
   * paths are resolved against the project root, and every path must lie inside the project.
   *
   * @param root the project root
   * @param paths the files or folders to format
   * @param write whether to rewrite the files that need formatting
   * @return one outcome per selected file, sorted by path
   * @throws dev.ward.domain.config.WardException if a path lies outside the project, matches no
   *     file, or the configuration is invalid
   */
  public List<FormatFileOutcome> format(
      final Path root, final List<Path> paths, final boolean write) {
    final List<FormatTarget> all = targets.select(root, declaredOptions());
    final List<FormatTarget> selected =
        paths.isEmpty() ? all : FormatTargetPaths.select(root, all, paths);
    return selected.stream()
        .map(target -> evaluator.process(root, target, Optional.empty(), write))
        .toList();
  }

  /**
   * Checks the files that the gate focuses on, or every selected file when {@code focus} is empty.
   * Results of files that are already formatted are cached by content and settings.
   *
   * @param root the project root
   * @param focus the root-relative paths to check, or none for every file
   * @return one outcome per checked file
   * @throws dev.ward.domain.config.WardException if the configuration is invalid
   */
  public List<FormatFileOutcome> check(final Path root, final List<String> focus) {
    final AnalysisCache cache = caches.open(root);
    return targets.select(root, declaredOptions()).stream()
        .filter(target -> focus.isEmpty() || focus.contains(target.relativePath()))
        .map(target -> evaluator.process(root, target, Optional.of(cache), false))
        .toList();
  }

  private Map<AnalysisLanguage, List<FormatOption>> declaredOptions() {
    final Map<AnalysisLanguage, List<FormatOption>> declared =
        new EnumMap<>(AnalysisLanguage.class);
    for (final AnalysisLanguage language : registry.languages()) {
      declared.put(language, evaluator.formatter(language).formatter().options());
    }
    return declared;
  }
}
