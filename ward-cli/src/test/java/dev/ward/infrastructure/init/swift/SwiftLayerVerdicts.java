package dev.ward.infrastructure.init.swift;

import dev.ward.analysis.application.rule.DependencyAnalysis;
import dev.ward.analysis.application.rule.DependencySnapshot;
import dev.ward.analysis.application.rule.FileSummaryCache;
import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.DependencySupport;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.swift.SwiftFileSummarizer;
import dev.ward.analysis.domain.swift.SwiftModuleResolver;
import dev.ward.analysis.domain.swift.SwiftProjectLayout;
import dev.ward.analysis.domain.swift.SwiftTarget;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.infrastructure.init.swift.HarmonizeRulesSource.SourceLayer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The layer verdicts of the generated Harmonize rules, derived from the Swift dependency graph of a
 * project whose modules are its folders. A layer fails when a file under its folder imports a
 * module named like a forbidden folder, or depends on a file under one.
 */
final class SwiftLayerVerdicts {
  private static final String SEPARATOR = "/";
  private static final SwiftProjectLayout LAYOUT =
      new SwiftProjectLayout(List.of(new SwiftTarget("App", List.of("App"), List.of(), List.of())));
  private static final FileSummaryCache NO_SUMMARIES =
      new FileSummaryCache() {
        @Override
        public Optional<FileSummary> read(final SourceUnit unit) {
          return Optional.empty();
        }

        @Override
        public void write(final SourceUnit unit, final FileSummary summary) {
          // nothing is stored: every verdict summarizes the files again
        }
      };

  private SwiftLayerVerdicts() {}

  /**
   * Returns the layers of {@code style} whose rule fails on {@code files}, which are keyed by path
   * relative to the project root.
   */
  static List<String> failingLayers(final String style, final Map<String, String> files) {
    final DependencySnapshot snapshot = analyze(files);
    return HarmonizeRulesSource.layers(style).stream()
        .filter(layer -> fails(layer, snapshot))
        .map(SourceLayer::name)
        .toList();
  }

  private static boolean fails(final SourceLayer layer, final DependencySnapshot snapshot) {
    return snapshot.summaries().entrySet().stream()
        .filter(entry -> folders(entry.getKey()).contains(layer.name()))
        .anyMatch(
            entry ->
                violates(
                    entry.getValue(),
                    snapshot.graph().dependenciesOf(entry.getKey()),
                    layer.forbidden()));
  }

  private static boolean violates(
      final FileSummary summary, final List<String> dependencies, final List<String> forbidden) {
    final boolean importsForbidden =
        summary.imports().stream()
            .map(Reference::specifier)
            .filter(specifier -> specifier.startsWith(SwiftFileSummarizer.MODULE_PREFIX))
            .map(specifier -> specifier.substring(SwiftFileSummarizer.MODULE_PREFIX.length()))
            .map(module -> module.toLowerCase(Locale.ROOT))
            .anyMatch(forbidden::contains);
    final boolean dependsOnForbidden =
        dependencies.stream()
            .map(SwiftLayerVerdicts::folders)
            .anyMatch(folders -> folders.stream().anyMatch(forbidden::contains));
    return importsForbidden || dependsOnForbidden;
  }

  /** The lowercased folders of a file, from its path: every directory above the file. */
  private static Set<String> folders(final String path) {
    final String[] parts = path.split(SEPARATOR);
    return Arrays.stream(parts, 0, parts.length - 1)
        .map(part -> part.toLowerCase(Locale.ROOT))
        .collect(Collectors.toSet());
  }

  private static DependencySnapshot analyze(final Map<String, String> files) {
    final List<SourceUnit> units =
        files.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> new SourceUnit(entry.getKey(), AnalysisLanguage.SWIFT, entry.getValue()))
            .toList();
    final FrontendSupport frontend =
        new FrontendSupport(
            AnalysisLanguage.SWIFT, new AntlrLanguageFrontend(), Set.of(RuleCapability.SYNTAX));
    final DependencySupport support =
        new DependencySupport(
            AnalysisLanguage.SWIFT, new SwiftFileSummarizer(), new SwiftModuleResolver(LAYOUT));
    return new DependencyAnalysis(List.of(frontend), List.of(support)).analyze(units, NO_SUMMARIES);
  }
}
