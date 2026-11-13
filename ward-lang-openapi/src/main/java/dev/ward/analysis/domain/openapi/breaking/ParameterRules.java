package dev.ward.analysis.domain.openapi.breaking;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiParts.Operation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The rules about parameters that a request now has to send: new ones, and ones that became
 * required.
 */
final class ParameterRules {

  private static final String PATH = "path:";

  private ParameterRules() {}

  /** The parameter rules. */
  static List<BreakingRule> rules() {
    return List.of(
        RowRule.of(
            "new-required-request-parameter",
            pair -> added(pair, "new-required-request-parameter")),
        RowRule.of(
            "new-required-request-default-parameter-to-existing-path",
            pair -> added(pair, "new-required-request-default-parameter-to-existing-path")),
        RowRule.of("new-request-path-parameter", pair -> added(pair, "new-request-path-parameter")),
        RowRule.of(
            "request-parameter-became-required",
            pair -> OperationPairs.each(pair, (base, now) -> required(pair, base, now))));
  }

  /**
   * The parameters the revision declares and the base does not, reported under the id of their
   * kind.
   */
  private static List<BreakingFinding> added(final DocumentPair pair, final String id) {
    return OperationPairs.each(
        pair,
        (base, now) -> {
          final Map<String, DocNode> before = NamedParts.parameters(pair.base(), base);
          final Map<String, DocNode> pathLevel =
              NamedParts.declared(pair.revision(), now.pathItem());
          final List<BreakingFinding> findings = new ArrayList<>();
          NamedParts.parameters(pair.revision(), now)
              .forEach(
                  (key, parameter) -> {
                    if (!before.containsKey(key) && kind(key, parameter, pathLevel).equals(id)) {
                      findings.add(finding(id, now, parameter));
                    }
                  });
          return findings;
        });
  }

  private static String kind(
      final String key, final DocNode parameter, final Map<String, DocNode> pathLevel) {
    if (key.startsWith(PATH)) {
      return "new-request-path-parameter";
    }
    if (!Bodies.required(parameter)) {
      return "";
    }
    return pathLevel.containsKey(key)
        ? "new-required-request-default-parameter-to-existing-path"
        : "new-required-request-parameter";
  }

  private static List<BreakingFinding> required(
      final DocumentPair pair, final Operation base, final Operation now) {
    final Map<String, DocNode> after = NamedParts.parameters(pair.revision(), now);
    final List<BreakingFinding> findings = new ArrayList<>();
    NamedParts.parameters(pair.base(), base)
        .forEach(
            (key, before) -> {
              final DocNode parameter = after.get(key);
              if (parameter != null && !Bodies.required(before) && Bodies.required(parameter)) {
                findings.add(
                    finding(
                        "request-parameter-became-required",
                        now,
                        parameter.get("required").orElse(parameter)));
              }
            });
    return findings;
  }

  private static BreakingFinding finding(final String id, final Operation now, final DocNode at) {
    return new BreakingFinding(
        RowRule.PREFIX + id,
        OperationIndex.method(now) + " " + now.path() + ": " + id.replace('-', ' '),
        OperationIndex.method(now),
        now.path(),
        at);
  }
}
