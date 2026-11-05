package dev.ward.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.JsonTree;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** {@code ward check --path} with the {@code ward} gate: narrowed, and JSON stays on stdout. */
@DisabledOnOs(OS.WINDOWS)
class WardCliWardPartialTest extends WardCliIntegrationSupport {
  private static final String PATH_OPTION = "--path";
  private static final String ORDER = "src/main/java/com/acme/order.java";
  private static final String CUSTOMER = "src/main/java/com/acme/customer.java";

  @BeforeEach
  void configure() throws IOException {
    Files.writeString(
        project.resolve(WARD_TOML),
        """
        version = 2
        [checks.ward]
        uses = "ward"
        profiles = ["default", "edit"]
        rules = ["ward:java/type-name"]
        [checks.ward.paths]
        match = ["src/**/*.java"]
        full = ["config/ward.rules"]
        """);
    write(ORDER, "package com.acme;\n\nclass order {}\n");
    write(CUSTOMER, "package com.acme;\n\nclass customer {}\n");
  }

  @Test
  void editedFileIsNarrowedAndTheJsonReportIsTheOnlyStdout() {
    assertThat(run(CHECK, "-C", project.toString(), FORMAT_OPTION, JSON, PATH_OPTION, ORDER))
        .isEqualTo(ExitCodes.FAILED);

    final JsonTree report = JsonTree.parse(out.toString());
    assertThat(report.get(CHECKS).get(0).get("scope").asString()).isEqualTo("narrowed");
    assertThat(report.get(STATUS).asString()).isEqualTo("FAILED");
    assertThat(out.toString()).contains("order.java").doesNotContain("customer.java");
    assertThat(err.toString()).doesNotContain("{");
  }

  @Test
  void editWithoutSourcesSkipsTheWardCheck() {
    assertThat(run(CHECK, "-C", project.toString(), FORMAT_OPTION, JSON, PATH_OPTION, "README.md"))
        .isEqualTo(ExitCodes.OK);

    assertThat(JsonTree.parse(out.toString()).get(CHECKS).get(0).get("scope").asString())
        .isEqualTo("skipped");
  }

  private void write(final String relative, final String content) throws IOException {
    Files.createDirectories(project.resolve(relative).getParent());
    Files.writeString(project.resolve(relative), content);
  }
}
