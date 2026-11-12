package dev.ward.analysis.domain.openapi.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.openapi.OpenApiDocuments;
import dev.ward.analysis.domain.openapi.YamlParser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OperationOperationIdTest {

  private static List<OpenApiFinding> check(final String spec) {
    return new OperationOperationId()
        .check(
            OpenApiDocuments.fromTrees(
                    "api.yaml", Map.of("api.yaml", YamlParser.parse(spec).tree()))
                .document()
                .orElseThrow());
  }

  @Test
  void reportsAnOperationWithoutAnOperationIdAtItsKey() {
    final List<OpenApiFinding> findings =
        check("openapi: 3.0.0\npaths:\n  /a:\n    get:\n      summary: x\n");

    assertThat(findings).hasSize(1);
    assertThat(findings.getFirst().file()).isEqualTo("api.yaml");
    assertThat(findings.getFirst().span().startLine()).isEqualTo(4);
    assertThat(findings.getFirst().message())
        .isEqualTo("the `GET` operation does not contain an `operationId`");
  }

  @Test
  void anEmptyOperationIdCountsAsMissing() {
    assertThat(check("openapi: 3.0.0\npaths:\n  /a:\n    put:\n      operationId: ''\n"))
        .hasSize(1);
  }

  @Test
  void anOperationWithAnOperationIdPasses() {
    assertThat(check("openapi: 3.0.0\npaths:\n  /a:\n    get:\n      operationId: a\n")).isEmpty();
  }

  @Test
  void everyImplementedRuleIsRegisteredByName() {
    assertThat(OpenApiRules.all()).containsKey("operation-operation-id");
  }
}
