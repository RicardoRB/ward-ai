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

/**
 * Which files a project check reads when a frontend declares other languages its project checks
 * read: a TypeScript check reads its JavaScript files once, and the JavaScript check is not run on
 * them again.
 */
final class RuleEngineProjectLanguagesTest {

  private static final Span AT = new Span(1, 1, 1, 2, 0, 1);
  private static final String TS_FILE = "src/a.ts";
  private static final String JS_FILE = "src/b.js";
  private static final String TS_RULE = "ward:typescript/knip-unresolved";
  private static final String JS_RULE = "ward:javascript/knip-unresolved";

  @Test
  void aTypescriptProjectCheckReadsTheJavascriptFilesThatItsProjectDeclares() {
    final List<String> seen = new ArrayList<>();
    final List<AnalysisFinding> findings =
        run(
            (trees, files) -> {
              seen.addAll(trees.keySet());
              return List.of();
            },
            withJavascript(),
            List.of(definition(TS_RULE, AnalysisLanguage.TYPESCRIPT)));

    assertThat(findings).isEmpty();
    assertThat(seen).containsExactly(TS_FILE, JS_FILE);
  }

  @Test
  void aJavascriptProjectCheckIsNotRunAgainOnFilesATypescriptCheckCovers() {
    final List<AnalysisFinding> findings =
        run(
            (trees, files) -> matchEachFile(trees),
            withJavascript(),
            List.of(
                definition(TS_RULE, AnalysisLanguage.TYPESCRIPT),
                definition(JS_RULE, AnalysisLanguage.JAVASCRIPT)));

    assertThat(findings)
        .extracting(AnalysisFinding::file)
        .containsExactlyInAnyOrder(JS_FILE, TS_FILE);
  }

  @Test
  void aTypescriptProjectCheckReadsNoJavascriptFileWithoutTheDeclaration() {
    final List<AnalysisFinding> findings =
        run(
            (trees, files) -> matchEachFile(trees),
            typescriptOnly(),
            List.of(definition(TS_RULE, AnalysisLanguage.TYPESCRIPT)));

    assertThat(findings).extracting(AnalysisFinding::file).containsExactly(TS_FILE);
  }

  private static List<ProjectMatch> matchEachFile(final SortedMap<String, SyntaxNode> trees) {
    final List<ProjectMatch> matches = new ArrayList<>();
    trees.keySet().forEach(path -> matches.add(new ProjectMatch(path, new RuleMatch(AT, "m"))));
    return matches;
  }

  private static List<AnalysisFinding> run(
      final Check check, final List<FrontendSupport> frontends, final List<RuleDefinition> rules) {
    return new RuleEngine(matcher(check))
        .runProject(
            List.of(
                new SourceUnit(TS_FILE, AnalysisLanguage.TYPESCRIPT, "export const a = 1;"),
                new SourceUnit(JS_FILE, AnalysisLanguage.JAVASCRIPT, "export const b = 1;")),
            frontends,
            rules,
            ProjectFiles.empty());
  }

  private static List<FrontendSupport> withJavascript() {
    return List.of(
        support(AnalysisLanguage.TYPESCRIPT)
            .withProjectLanguages(Set.of(AnalysisLanguage.JAVASCRIPT)),
        support(AnalysisLanguage.JAVASCRIPT));
  }

  private static List<FrontendSupport> typescriptOnly() {
    return List.of(support(AnalysisLanguage.TYPESCRIPT), support(AnalysisLanguage.JAVASCRIPT));
  }

  private static FrontendSupport support(final AnalysisLanguage language) {
    final LanguageFrontend parser =
        unit -> new ParseResult(new SyntaxNode("file", AT, Map.of(), List.of()), List.of());
    return new FrontendSupport(language, parser, Set.of(RuleCapability.SYNTAX));
  }

  private static RuleDefinition definition(final String id, final AnalysisLanguage language) {
    return new RuleDefinition(
        id, Set.of(language), RuleCapability.SYNTAX, AnalysisSeverity.WARNING, "body");
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
