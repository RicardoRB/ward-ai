package dev.ward.analysis.application.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.openapi.YamlSyntax;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.domain.rule.ProjectMatch;
import java.util.List;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class OpenApiRuleRunTest {

  private static final String ENTRY = "api.yaml";
  private static final String MISSING = "openapi: 3.0.0\npaths:\n  /a:\n    get: {}\n";

  private static SyntaxNode tree(final String text) {
    return new OpenApiLanguageFrontend()
        .parse(new SourceUnit(ENTRY, AnalysisLanguage.OPENAPI, text))
        .tree();
  }

  private static List<ProjectMatch> run(final TreeMap<String, SyntaxNode> trees) {
    final OpenApiProjectCheck check =
        (OpenApiProjectCheck)
            new OpenApiCheckProvider().checks().get("openapi-operation-operation-id").create(null);
    return check.project(trees);
  }

  @Test
  void theFrontendReadsYamlAndJsonByTheirText() {
    final ParseResult yaml =
        new OpenApiLanguageFrontend()
            .parse(new SourceUnit("a.json", AnalysisLanguage.OPENAPI, "a: 1\n"));
    final ParseResult json =
        new OpenApiLanguageFrontend()
            .parse(new SourceUnit("a.yaml", AnalysisLanguage.OPENAPI, "{\"a\": 1}"));

    assertThat(yaml.diagnostics()).isEmpty();
    assertThat(yaml.tree().kind()).isEqualTo(YamlSyntax.OBJECT);
    assertThat(json.tree().children()).hasSize(1);
  }

  @Test
  void theFrontendReportsASyntaxErrorAsADiagnosticNextToAnEmptyDocument() {
    final ParseResult result =
        new OpenApiLanguageFrontend()
            .parse(new SourceUnit("a.yaml", AnalysisLanguage.OPENAPI, "a: 1\n b: 2\n"));

    assertThat(result.tree().kind()).isEqualTo(YamlSyntax.DOCUMENT);
    assertThat(result.diagnostics())
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.file()).isEqualTo("a.yaml");
              assertThat(finding.line()).isEqualTo(2);
              assertThat(finding.ruleId()).isEqualTo("syntax");
            });
  }

  @Test
  void aProjectCheckBuildsTheDocumentOfEachEntryFileFromTheTrees() {
    final TreeMap<String, SyntaxNode> trees = new TreeMap<>();
    trees.put(ENTRY, tree(MISSING));

    final List<ProjectMatch> matches = run(trees);

    assertThat(matches).hasSize(1);
    assertThat(matches.getFirst().path()).isEqualTo(ENTRY);
    assertThat(matches.getFirst().match().span().startLine()).isEqualTo(4);
    assertThat(matches.getFirst().match().message()).contains("`GET`");
  }

  @Test
  void aFileWithoutAnOpenapiKeyIsNotAnEntryAndABrokenDocumentRunsNoRule() {
    final TreeMap<String, SyntaxNode> trees = new TreeMap<>();
    trees.put("part.yaml", tree("paths: {}\n"));
    trees.put(
        "broken.yaml",
        tree("openapi: 3.0.0\ncomponents:\n  schemas:\n    A:\n      $ref: 'gone.yaml'\n"));

    assertThat(run(trees)).isEmpty();
  }

  @Test
  void aSingleFileCheckFindsNothing() {
    final OpenApiProjectCheck check =
        (OpenApiProjectCheck)
            new OpenApiCheckProvider().checks().get("openapi-operation-operation-id").create(null);

    assertThat(check.matches(tree(MISSING))).isEmpty();
  }

  @Test
  void theRuleFilesAreListed() {
    assertThat(new OpenApiRuleFiles().ruleFiles()).contains("rules/openapi/operations.toml");
  }
}
