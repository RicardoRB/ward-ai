package dev.ward.analysis.domain.rule;

import static dev.ward.analysis.domain.rule.GraphRuleFixtures.definition;
import static dev.ward.analysis.domain.rule.GraphRuleFixtures.edge;
import static dev.ward.analysis.domain.rule.GraphRuleFixtures.graph;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.graph.ProjectFiles;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The unused-file rule: a source file nothing imports is reported unless it is an entry. */
class UnusedFileRuleTest {

  private static final Set<String> FILES =
      Set.of("src/index.ts", "src/util.ts", "src/orphan.ts", "src/util.test.ts", "README.md");

  @Test
  void reportsASourceFileNothingImportsAndKeepsImportedOnes() {
    final List<GraphMatch> matches =
        rule(List.of()).matches(graph(edge("src/index.ts", "src/util.ts", 1)), project(Set.of()));

    assertThat(matches)
        .extracting(GraphMatch::file)
        .containsExactly("src/index.ts", "src/orphan.ts", "src/util.test.ts");
  }

  @Test
  void aManifestEntryIsUsedWithoutAnImporter() {
    final List<GraphMatch> matches =
        rule(List.of()).matches(graph(), project(Set.of("src/index.ts")));

    assertThat(matches).extracting(GraphMatch::file).doesNotContain("src/index.ts");
  }

  @Test
  void anEntryGlobKeepsMatchingFilesOut() {
    final List<GraphMatch> matches =
        rule(List.of("**/*.test.ts")).matches(graph(), project(Set.of()));

    assertThat(matches).extracting(GraphMatch::file).doesNotContain("src/util.test.ts");
  }

  @Test
  void nonSourceFilesAreNeverReported() {
    final List<GraphMatch> matches = rule(List.of()).matches(graph(), project(Set.of()));

    assertThat(matches).extracting(GraphMatch::file).doesNotContain("README.md");
  }

  @Test
  void withoutAFileListNothingIsReported() {
    assertThat(rule(List.of()).matches(graph())).isEmpty();
  }

  @Test
  void theCompiledRuleReadsItsGlobsFromTheBody() {
    final RuleDefinition definition = definition(UnusedFileRule.ID, "**/*.test.ts,**/orphan.ts");

    final List<GraphMatch> matches =
        GraphRules.compile(definition).matches(graph(), project(Set.of()));

    assertThat(matches).extracting(GraphMatch::file).containsExactly("src/index.ts", "src/util.ts");
  }

  @Test
  void aCompiledRuleWithAnEmptyBodyHasNoGlobs() {
    final RuleDefinition definition = definition(UnusedFileRule.ID, "");

    assertThat(GraphRules.isGraphRule(definition)).isTrue();
    assertThat(GraphRules.compile(definition).definition()).isEqualTo(definition);
  }

  private static UnusedFileRule rule(final List<String> entries) {
    return new UnusedFileRule(definition(UnusedFileRule.ID, ""), entries);
  }

  private static ProjectFiles project(final Set<String> entries) {
    return new ProjectFiles(FILES, entries);
  }
}
