package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CiWorkflowOpenApiTest {
  @Test
  void openApiGatesInstallPinnedVacuumAndOasdiff() {
    final String body =
        CiWorkflowTemplate.render(
                "github",
                CiWorkflowTemplateTest.MAVEN,
                CiWorkflowTemplateTest.gates("openapi-lint", "openapi-diff", "openapi-compat"),
                "0.0.12")
            .body();

    assertThat(body)
        .contains(
            "releases/download/v0.36.1/vacuum_0.36.1_linux_x86_64.tar.gz",
            "releases/download/v1.35.0/oasdiff_1.35.0_linux_amd64.tar.gz",
            "fetch-depth: 0")
        .doesNotContain("gitleaks", "trivy");
  }
}
