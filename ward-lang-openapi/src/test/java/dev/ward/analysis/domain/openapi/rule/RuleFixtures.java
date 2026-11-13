package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.LoadResult;
import dev.ward.analysis.domain.openapi.OpenApiDocuments;
import dev.ward.analysis.domain.openapi.YamlParser;
import java.util.List;
import java.util.Map;

/** Runs a named rule over a document written in the test, and lists where it reports. */
final class RuleFixtures {

  private RuleFixtures() {}

  /** The {@code line:column} of each finding of rule {@code name} in the spec {@code text}. */
  static List<String> positions(final String name, final String text) {
    return run(name, text).stream()
        .map(finding -> finding.span().startLine() + ":" + finding.span().startColumn())
        .toList();
  }

  static List<OpenApiFinding> run(final String name, final String text) {
    final LoadResult loaded =
        OpenApiDocuments.fromTrees("api.yaml", Map.of("api.yaml", YamlParser.parse(text).tree()));
    return OpenApiRules.all().get(name).check(loaded.document().orElseThrow());
  }
}
