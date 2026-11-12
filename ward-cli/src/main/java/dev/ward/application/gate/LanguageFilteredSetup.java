package dev.ward.application.gate;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.RuleDefinition;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Keeps the rules of one language family out of, or only in, a gate's run after configuration. The
 * {@code ward} gate reads source files and leaves the OpenAPI rules to {@code openapi-lint}, which
 * reads one specification; both read the same rule configuration file, so the rules stay known to
 * it and are only dropped from the run.
 */
final class LanguageFilteredSetup implements RuleSetup {

  private final RuleSetup inner;
  private final Predicate<RuleDefinition> keep;

  private LanguageFilteredSetup(final RuleSetup inner, final Predicate<RuleDefinition> keep) {
    this.inner = inner;
    this.keep = keep;
  }

  /** Runs everything {@code inner} chooses except the OpenAPI rules. */
  static RuleSetup withoutOpenApi(final RuleSetup inner) {
    return new LanguageFilteredSetup(inner, rule -> !isOpenApi(rule));
  }

  /** Runs only the OpenAPI rules {@code inner} chooses. */
  static RuleSetup onlyOpenApi(final RuleSetup inner) {
    return new LanguageFilteredSetup(inner, LanguageFilteredSetup::isOpenApi);
  }

  @Override
  public RuleSet resolve(final Path root, final int javaRelease) {
    final RuleSet set = inner.resolve(root, javaRelease);
    final List<RuleDefinition> rules = set.rules().stream().filter(keep).toList();
    final Map<String, List<Pattern>> excludes = set.excludePaths();
    return new RuleSet(rules, excludes, set.problems());
  }

  private static boolean isOpenApi(final RuleDefinition rule) {
    return rule.languages().contains(AnalysisLanguage.OPENAPI);
  }
}
