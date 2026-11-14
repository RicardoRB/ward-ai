package dev.ward.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.JsonTree;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The findings of {@code ward-architecture.toml} in the JSON and SARIF reports of {@code check}.
 */
class WardCliArchitectureRulesTest extends WardCliIntegrationSupport {
  private static final String DEPENDENCIES = "ward:architecture/dependencies";
  private static final String REASON = "because the domain must not know its adapters";

  private void configure() throws IOException {
    final Path domain = project.resolve("src/main/java/shop/domain");
    final Path infrastructure = project.resolve("src/main/java/shop/infrastructure");
    Files.createDirectories(domain);
    Files.createDirectories(infrastructure);
    Files.writeString(
        domain.resolve("Order.java"),
        "package shop.domain;\n\nimport shop.infrastructure.Db;\n\npublic class Order {}\n");
    Files.writeString(
        infrastructure.resolve("Db.java"), "package shop.infrastructure;\n\npublic class Db {}\n");
    Files.writeString(
        project.resolve("ward-architecture.toml"),
        """
        version = 1

        [[rule]]
        name = "domain-is-pure"
        select = { directories = ["domain"] }
        must-not-depend-on = { directories = ["infrastructure"] }
        because = "the domain must not know its adapters"
        """);
    Files.writeString(project.resolve(WARD_TOML), "version = 2\n[checks.ward]\nuses = \"ward\"\n");
  }

  @Test
  void jsonReportCarriesTheRuleNameAndReasonAndNothingElseOnStdout() throws IOException {
    configure();

    final int exit = run(CHECK, FORMAT_OPTION, JSON, "-C", project.toString());

    assertThat(exit).as(err.toString()).isEqualTo(ExitCodes.FAILED);
    final JsonTree findings = JsonTree.parse(out.toString()).get(CHECKS).get(0).get("findings");
    JsonTree architecture = null;
    for (int index = 0; index < findings.size(); index++) {
      if (DEPENDENCIES.equals(findings.get(index).get("rule").asString())) {
        architecture = findings.get(index);
      }
    }
    assertThat(architecture).as(out.toString()).isNotNull();
    assertThat(architecture.get("file").asString())
        .isEqualTo("src/main/java/shop/domain/Order.java");
    assertThat(architecture.get("line").asInt()).isEqualTo(3);
    assertThat(architecture.get("message").asString())
        .startsWith("domain-is-pure: ")
        .endsWith(REASON);
  }

  @Test
  void sarifReportCarriesTheRuleIdNameAndReason() throws IOException {
    configure();

    final int exit = run(CHECK, FORMAT_OPTION, "sarif", "-C", project.toString());

    assertThat(exit).as(err.toString()).isEqualTo(ExitCodes.FAILED);
    assertThat(out.toString()).contains(DEPENDENCIES, "domain-is-pure: ", REASON);
  }
}
