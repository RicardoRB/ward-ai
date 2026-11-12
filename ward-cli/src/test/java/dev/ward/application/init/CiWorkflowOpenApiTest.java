package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CiWorkflowOpenApiTest {
  @Test
  void openApiGatesInstallNoTool() {
    final String body =
        CiWorkflowTemplate.render(
                "github",
                CiWorkflowTemplateTest.MAVEN,
                CiWorkflowTemplateTest.gates("openapi-lint", "openapi-diff", "openapi-compat"),
                "0.0.12")
            .body();

    assertThat(body)
        .contains("fetch-depth: 0")
        .doesNotContain("gitleaks", "trivy", "oasdiff", "vacuum");
  }
}
