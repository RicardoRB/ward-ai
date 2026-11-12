package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CiWorkflowOpenApiTest {
  @Test
  void openApiGatesInstallOnlyPinnedVacuum() {
    final String body =
        CiWorkflowTemplate.render(
                "github",
                CiWorkflowTemplateTest.MAVEN,
                CiWorkflowTemplateTest.gates("openapi-lint", "openapi-diff", "openapi-compat"),
                "0.0.12")
            .body();

    assertThat(body)
        .contains("releases/download/v0.36.1/vacuum_0.36.1_linux_x86_64.tar.gz", "fetch-depth: 0")
        .doesNotContain("gitleaks", "trivy", "oasdiff");
  }
}
