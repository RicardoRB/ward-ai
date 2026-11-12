package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CiWorkflowApiCompatTest {
  @Test
  void apiCompatFetchesFullHistoryAndTags() {
    final String body =
        CiWorkflowTemplate.render(
                "github",
                CiWorkflowTemplateTest.MAVEN,
                CiWorkflowTemplateTest.gates("tests", "api-compat"),
                "0.0.12")
            .body();

    assertThat(body).contains("fetch-depth: 0");
  }

  @Test
  void withoutApiCompatTheCheckoutIsShallow() {
    final String body =
        CiWorkflowTemplate.render(
                "github",
                CiWorkflowTemplateTest.MAVEN,
                CiWorkflowTemplateTest.gates("tests"),
                "0.0.12")
            .body();

    assertThat(body).doesNotContain("fetch-depth");
  }
}
