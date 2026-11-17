package dev.ward.application.gate;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.port.DependencyResolver;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.port.DependencySupport;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Dependency supports with empty summaries, for the tests of the dependency step. */
final class DependencyStepFakes {
  private DependencyStepFakes() {}

  /** Selected rules that read the dependencies of {@code languages}, so they are summarized. */
  static List<RuleDefinition> readers(final AnalysisLanguage... languages) {
    return readers(
        "dependency-reader",
        "measure=lines; node=no_such_node; max=1000000; message=unused",
        languages
    );
  }

  /** Like {@link #readers(AnalysisLanguage...)}, for a matcher that knows the rule {@code name}. */
  static List<RuleDefinition> readers(
      final String name,
      final String body,
      final AnalysisLanguage... languages
  ) {
    return Stream.of(languages)
        .map(
            language ->
                new RuleDefinition(
                    "ward:" + language.name().toLowerCase(Locale.ROOT) + "/" + name,
                    language,
                    RuleCapability.SYMBOLS,
                    AnalysisSeverity.ERROR,
                    body
                )
        )
        .toList();
  }

  static List<DependencySupport> separateResolvers(final List<AnalysisLanguage> languages) {
    return languages.stream().map(language -> support(language, resolver())).toList();
  }

  static List<DependencySupport> sharedJvmResolver(final List<AnalysisLanguage> languages) {
    final DependencyResolver jvm = resolver();
    return languages.stream()
        .map(language -> support(language, jvmLanguage(language) ? jvm : resolver()))
        .toList();
  }

  private static boolean jvmLanguage(final AnalysisLanguage language) {
    return language == AnalysisLanguage.JAVA || language == AnalysisLanguage.KOTLIN;
  }

  /** A resolver of its own: a non-capturing lambda would be one shared instance. */
  private static DependencyResolver resolver() {
    return new DependencyResolver() {
      @Override
      public List<DependencyEdge> resolve(final List<FileSummary> summaries) {
        return List.of();
      }
    };
  }

  private static DependencySupport support(
      final AnalysisLanguage language,
      final DependencyResolver resolver
  ) {
    return new DependencySupport(
        language,
        (unit, tree) -> new FileSummary(unit.path(), List.of(), List.of(), List.of()),
        resolver
    );
  }
}
