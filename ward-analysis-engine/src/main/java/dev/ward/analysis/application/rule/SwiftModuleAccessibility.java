package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.domain.rule.RuleDefinition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The Swift redundant-public-accessibility rule: a public or open declaration that the project uses
 * only from files of its own module, so it could be internal. A use is a name a file spells,
 * including a name it uses only in a conformance clause. Nothing is reported when the module of the
 * declaring file, or of any other file that spells the name, is unknown.
 *
 * <p>shortcut: uses are matched by name only, so a same-named declaration elsewhere counts as a use
 * and blocks the finding; a declaration used only by its own file is left to unused-declaration.
 * Upgrade by resolving each use to its declaration through the type index.
 */
final class SwiftModuleAccessibility {

  private static final String SWIFT_SUFFIX = ".swift";
  private static final String QUALIFIER = ".";

  private SwiftModuleAccessibility() {}

  /** Whether {@code rule} is the redundant-public-accessibility rule. */
  static boolean isRule(final RuleDefinition rule) {
    return SwiftUnusedDefinitions.REDUNDANT_ACCESSIBILITY_ID.equals(rule.id());
  }

  /**
   * The findings of {@code rule} over the Swift summaries, given the module of each file.
   *
   * @param moduleOf the module of a project-relative path, or empty when it is unknown
   */
  static List<AnalysisFinding> findings(
      final RuleDefinition rule,
      final Map<String, FileSummary> summaries,
      final Function<String, Optional<String>> moduleOf) {
    final Map<String, List<String>> usersByName = usersByName(summaries);
    final List<AnalysisFinding> findings = new ArrayList<>();
    for (final FileSummary summary : swiftSummaries(summaries)) {
      final Optional<String> module = moduleOf.apply(summary.path());
      if (module.isEmpty()) {
        continue;
      }
      for (final Symbol symbol : summary.exports()) {
        if (symbol.kind().startsWith(SwiftUnusedDefinitions.PUBLIC_KIND_PREFIX)
            && onlyInModule(summary.path(), module.get(), symbol.name(), usersByName, moduleOf)) {
          findings.add(finding(rule, summary.path(), module.get(), symbol));
        }
      }
    }
    return List.copyOf(findings);
  }

  /** Whether every other file that spells {@code name} is in {@code module}, and one does. */
  private static boolean onlyInModule(
      final String declaringPath,
      final String module,
      final String name,
      final Map<String, List<String>> usersByName,
      final Function<String, Optional<String>> moduleOf) {
    final List<String> users =
        usersByName.getOrDefault(lastComponent(name), List.of()).stream()
            .filter(path -> !path.equals(declaringPath))
            .toList();
    return !users.isEmpty()
        && users.stream().allMatch(path -> moduleOf.apply(path).equals(Optional.of(module)));
  }

  /** Indexes the Swift files that spell each name, plainly or in a conformance clause. */
  private static Map<String, List<String>> usersByName(final Map<String, FileSummary> summaries) {
    return swiftSummaries(summaries).stream()
        .flatMap(
            summary ->
                spelledNames(summary.usedNames()).stream()
                    .map(name -> Map.entry(name, summary.path())))
        .collect(
            Collectors.groupingBy(
                Map.Entry::getKey,
                HashMap::new,
                Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
  }

  private static Set<String> spelledNames(final Set<String> usedNames) {
    return usedNames.stream()
        .map(
            name ->
                name.startsWith(SwiftUnusedDefinitions.CONFORMANCE_PREFIX)
                    ? name.substring(SwiftUnusedDefinitions.CONFORMANCE_PREFIX.length())
                    : name)
        .collect(Collectors.toUnmodifiableSet());
  }

  private static List<FileSummary> swiftSummaries(final Map<String, FileSummary> summaries) {
    return summaries.values().stream()
        .filter(summary -> summary.path().endsWith(SWIFT_SUFFIX))
        .sorted((left, right) -> left.path().compareTo(right.path()))
        .toList();
  }

  /** The last dot-separated part of a qualified name: the name a use writes. */
  private static String lastComponent(final String qualified) {
    return qualified.substring(qualified.lastIndexOf(QUALIFIER) + 1);
  }

  private static AnalysisFinding finding(
      final RuleDefinition rule, final String path, final String module, final Symbol symbol) {
    return new AnalysisFinding(
        rule.id(),
        rule.severity(),
        "public declaration '"
            + symbol.name()
            + "' is used only within module '"
            + module
            + "'; it can be internal",
        path,
        symbol.span().startLine(),
        symbol.span().startColumn(),
        null,
        List.of());
  }
}
