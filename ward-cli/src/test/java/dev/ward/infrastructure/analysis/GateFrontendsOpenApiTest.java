package dev.ward.infrastructure.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.openapi.YamlSyntax;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import org.junit.jupiter.api.Test;

class GateFrontendsOpenApiTest {

  private final List<FrontendSupport> frontends = GateFrontends.create(new AntlrLanguageFrontend());

  private ParseResult parse(final AnalysisLanguage language, final String path, final String text) {
    return frontends.stream()
        .filter(support -> support.language() == language)
        .findFirst()
        .orElseThrow()
        .frontend()
        .parse(new SourceUnit(path, language, text));
  }

  @Test
  void anOpenApiUnitParsesAsYamlOrJsonByItsTextNotItsExtension() {
    final ParseResult yaml = parse(AnalysisLanguage.OPENAPI, "spec.json", "openapi: 3.0.0\n");
    final ParseResult json =
        parse(AnalysisLanguage.OPENAPI, "spec.yaml", "{\"openapi\": \"3.0.0\"}");

    assertThat(yaml.diagnostics()).isEmpty();
    assertThat(yaml.tree().kind()).isEqualTo(YamlSyntax.OBJECT);
    assertThat(json.tree().children()).hasSize(1);
  }

  @Test
  void aSyntaxErrorInAnOpenApiUnitIsADiagnosticAtItsPosition() {
    final ParseResult result = parse(AnalysisLanguage.OPENAPI, "spec.yaml", "a: 1\n  b: 2\n");

    assertThat(result.diagnostics()).hasSize(1);
    assertThat(result.diagnostics().getFirst().file()).isEqualTo("spec.yaml");
    assertThat(result.diagnostics().getFirst().line()).isEqualTo(2);
  }

  @Test
  void aJavaUnitStillParsesThroughAntlr() {
    final ParseResult result = parse(AnalysisLanguage.JAVA, "A.java", "class A { }\n");

    assertThat(result.diagnostics()).isEmpty();
    assertThat(result.tree().kind()).isNotEqualTo(YamlSyntax.OBJECT);
  }
}
