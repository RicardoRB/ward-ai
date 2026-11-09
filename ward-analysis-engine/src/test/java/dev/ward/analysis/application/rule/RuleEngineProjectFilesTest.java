package dev.ward.analysis.application.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.LanguageFrontend;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.ProjectFiles;
import dev.ward.analysis.domain.rule.ProjectMatch;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.RuleMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import org.junit.jupiter.api.Test;

/** Project checks that read the manifest files of the project, through the rule engine. */
final class RuleEngineProjectFilesTest {

  private static final String RULE = "ward:typescript/project-files";
  private static final int LINE = 3;
  private static final int COLUMN = 4;
  private static final Span AT = new Span(LINE, COLUMN, LINE, 9, 20, 25);
  private static final String MANIFEST = "package.json";
  private static final String SOURCE = "src/a.ts";
  private static final String TEXT = "{\"name\":\"app\"}";

  @Test
  void aMatchAtAManifestPathIsAFindingAtThatPathLineAndColumn() {
    final List<AnalysisFinding> findings =
        run(
            (trees, files) -> List.of(new ProjectMatch(MANIFEST, new RuleMatch(AT, "manifest"))),
            new ProjectFiles(Map.of(MANIFEST, TEXT)));

    assertThat(findings)
        .containsExactly(
            new AnalysisFinding(
                RULE,
                AnalysisSeverity.WARNING,
                "manifest",
                MANIFEST,
                LINE,
                COLUMN,
                null,
                List.of()));
  }

  @Test
  void theCheckReceivesTheManifestTextsOfTheRun() {
    final List<String> seen = new ArrayList<>();
    run(
        (trees, files) -> {
          files.text(MANIFEST).ifPresent(seen::add);
          return List.of();
        },
        new ProjectFiles(Map.of(MANIFEST, TEXT)));

    assertThat(seen).containsExactly(TEXT);
  }

  @Test
  void aMatchAtAUnitPathStaysAFindingAtThatUnit() {
    final List<AnalysisFinding> findings =
        run(
            (trees, files) -> List.of(new ProjectMatch(SOURCE, new RuleMatch(AT, "unit"))),
            ProjectFiles.empty());

    assertThat(findings)
        .containsExactly(
            new AnalysisFinding(
                RULE, AnalysisSeverity.WARNING, "unit", SOURCE, LINE, COLUMN, null, List.of()));
  }

  @Test
  void theTreesOnlyRunPassesNoFiles() {
    final List<AnalysisFinding> findings =
        new RuleEngine(
                matcher(
                    (trees, files) -> List.of(new ProjectMatch(SOURCE, new RuleMatch(AT, "x")))))
            .runProject(List.of(unit()), List.of(frontend()), List.of(definition()));

    assertThat(findings).hasSize(1);
  }

  private static List<AnalysisFinding> run(final Check check, final ProjectFiles files) {
    return new RuleEngine(matcher(check))
        .runProject(List.of(unit()), List.of(frontend()), List.of(definition()), files);
  }

  private static SourceUnit unit() {
    return new SourceUnit(SOURCE, AnalysisLanguage.TYPESCRIPT, "export const a = 1;");
  }

  private static RuleDefinition definition() {
    return new RuleDefinition(
        RULE, AnalysisLanguage.TYPESCRIPT, RuleCapability.SYNTAX, AnalysisSeverity.WARNING, "body");
  }

  private static FrontendSupport frontend() {
    final LanguageFrontend parser =
        unit -> new ParseResult(new SyntaxNode("file", AT, Map.of(), List.of()), List.of());
    return new FrontendSupport(AnalysisLanguage.TYPESCRIPT, parser, Set.of(RuleCapability.SYNTAX));
  }

  /** A project check body: the trees and the manifest files in, the matches out. */
  @FunctionalInterface
  private interface Check {
    List<ProjectMatch> run(SortedMap<String, SyntaxNode> trees, ProjectFiles files);
  }

  private static RuleMatcher matcher(final Check check) {
    return definition ->
        new CompiledRule() {
          @Override
          public RuleDefinition definition() {
            return definition;
          }

          @Override
          public boolean isProject() {
            return true;
          }

          @Override
          public List<RuleMatch> matches(final SyntaxNode root) {
            return List.of();
          }

          @Override
          public List<ProjectMatch> project(
              final SortedMap<String, SyntaxNode> trees, final ProjectFiles files) {
            return check.run(trees, files);
          }
        };
  }
}
