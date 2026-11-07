package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.domain.rule.RuleDefinition;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Runs the Python unused-function and unused-class rules over the whole project. A definition is
 * unused only if no file spells its name, so the rules need every summary and run once per run,
 * after the per-unit cache, like the duplicate-code rules.
 *
 * <p>shortcut: the checks are by name and by file name, with no entry-point tables or string
 * lookups, so a definition reached only through {@code getattr} or a packaging entry point is
 * reported; upgrade by reading {@code pyproject.toml} entry points.
 */
public final class UnusedDefinitionFindings {

  /** The rule id for unused functions. */
  public static final String FUNCTION_ID = "ward:python/unused-function";

  /** The rule id for unused classes. */
  public static final String CLASS_ID = "ward:python/unused-class";

  private static final Map<String, String> KINDS =
      Map.of(FUNCTION_ID, "function", CLASS_ID, "class");
  private static final String PYTHON_SUFFIX = ".py";
  private static final String DUNDER = "__";
  private static final String TEST_PREFIX = "test";
  private static final String ENTRY_FUNCTION = "main";
  // Files whose definitions are found by name: package roots, test discovery and setup scripts.
  private static final Set<String> DISCOVERED_FILES =
      Set.of("__init__.py", "__main__.py", "conftest.py", "setup.py");

  private UnusedDefinitionFindings() {}

  /** Whether {@code definition} is one of the unused-definition rules. */
  public static boolean isUnusedDefinitionRule(final RuleDefinition definition) {
    return KINDS.containsKey(definition.id());
  }

  /**
   * The findings of every unused-definition rule among {@code rules}, over {@code summaries}.
   *
   * @throws NullPointerException if an argument is missing
   */
  public static List<AnalysisFinding> findings(
      final List<RuleDefinition> rules, final Map<String, FileSummary> summaries) {
    Objects.requireNonNull(summaries, "summaries");
    final Set<String> used = new HashSet<>();
    summaries.values().forEach(summary -> used.addAll(summary.usedNames()));
    final List<AnalysisFinding> findings = new ArrayList<>();
    for (final RuleDefinition rule : rules) {
      if (isUnusedDefinitionRule(rule)) {
        findings.addAll(ruleFindings(rule, new TreeMap<>(summaries), used));
      }
    }
    return List.copyOf(findings);
  }

  private static List<AnalysisFinding> ruleFindings(
      final RuleDefinition rule, final Map<String, FileSummary> summaries, final Set<String> used) {
    final String kind = KINDS.get(rule.id());
    final List<AnalysisFinding> findings = new ArrayList<>();
    for (final FileSummary summary : summaries.values()) {
      if (!isScanned(summary.path())) {
        continue;
      }
      for (final Symbol symbol : summary.declared()) {
        if (kind.equals(symbol.kind()) && !used.contains(symbol.name()) && !isExempt(symbol)) {
          findings.add(finding(rule, summary.path(), symbol, kind));
        }
      }
    }
    return findings;
  }

  private static boolean isScanned(final String path) {
    final String name = path.substring(path.lastIndexOf('/') + 1);
    return path.endsWith(PYTHON_SUFFIX)
        && !DISCOVERED_FILES.contains(name)
        && !name.startsWith(TEST_PREFIX + "_")
        && !name.endsWith("_" + TEST_PREFIX + PYTHON_SUFFIX);
  }

  private static boolean isExempt(final Symbol symbol) {
    final String name = symbol.name();
    return (name.startsWith(DUNDER) && name.endsWith(DUNDER))
        || name.startsWith(TEST_PREFIX)
        || ENTRY_FUNCTION.equals(name);
  }

  private static AnalysisFinding finding(
      final RuleDefinition rule, final String path, final Symbol symbol, final String kind) {
    return new AnalysisFinding(
        rule.id(),
        rule.severity(),
        "unused " + kind + " '" + symbol.name() + "' is not used in any file",
        path,
        symbol.span().startLine(),
        symbol.span().startColumn(),
        null,
        List.of());
  }
}
