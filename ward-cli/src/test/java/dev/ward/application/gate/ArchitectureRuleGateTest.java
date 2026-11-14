package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.infrastructure.config.TomlConfigurationReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The naming and location rules, and the loud failures, of {@code ward-architecture.toml}. */
class ArchitectureRuleGateTest {
  private static final Path SWIFT =
      Path.of("src/test/resources/fixtures/swift-architecture/layered");
  private static final String RULE_FILE = "ward-architecture.toml";

  @TempDir Path root;

  @Test
  void namingAndLocationRulesRunOverFilesThatHaveNoEdges() throws IOException {
    ArchitectureGateRun.copy(SWIFT, root, file -> true);
    write(
        """
        [[rule]]
        name = "services-are-named"
        select = { directories = ["Service"] }
        file-name = "*Repository.swift"
        because = "services are stores"

        [[rule]]
        name = "controllers-in-folder"
        select = { paths = ["**/*Service.swift"] }
        must-be-in = { directories = ["Controller"] }
        because = "services are controllers"
        """);

    final GateResult result = ArchitectureGateRun.run(root);

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    final List<Finding> findings = result.findings().items();
    assertThat(findings)
        .extracting(Finding::ruleId)
        .containsExactlyInAnyOrder("ward:architecture/naming", "ward:architecture/location");
    assertThat(findings)
        .allSatisfy(
            finding -> {
              assertThat(finding.file()).isEqualTo("App/Service/OrderService.swift");
              assertThat(finding.line()).isEqualTo(1);
            });
    assertThat(findings.stream().map(Finding::message))
        .anyMatch(
            message ->
                message.startsWith("services-are-named: ")
                    && message.endsWith("because services are stores"));
  }

  @Test
  void aRuleThatSelectsNoFileFailsTheGateUnlessItAllowsIt() throws IOException {
    ArchitectureGateRun.copy(SWIFT, root, file -> true);
    final String rule =
        """
        [[rule]]
        name = "typo"
        select = { directories = ["Servce"] }
        file-name = "*.swift"
        because = "x"
        """;
    write(rule);

    final GateResult failing = ArchitectureGateRun.run(root);

    assertThat(failing.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(text(failing)).contains("typo", "selects no file");

    write(rule + "allow-empty = true\n");

    assertThat(ArchitectureGateRun.run(root).status()).isEqualTo(GateStatus.PASSED);
  }

  @Test
  void aProblemInTheFileStopsTheRunAndNamesTheFileAndTheKey() throws IOException {
    ArchitectureGateRun.copy(SWIFT, root, file -> true);
    write(
        """
        [[rule]]
        name = "r"
        select = { directories = ["Service"] }
        file-name = "*.swift"
        annotated-with = "Entity"
        because = "x"
        """);

    final GateResult result = ArchitectureGateRun.run(root);

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(text(result)).contains(RULE_FILE, "annotated-with");
  }

  @Test
  void aConfiguredFileIsReadAndTheRootFileIsNot() throws IOException {
    ArchitectureGateRun.copy(SWIFT, root, file -> true);
    write("not valid toml = = =\n");
    Files.createDirectories(root.resolve("config"));
    Files.writeString(
        root.resolve("config/architecture.toml"),
        """
        version = 1

        [[rule]]
        name = "services-are-named"
        select = { directories = ["Service"] }
        file-name = "*Repository.swift"
        because = "x"
        """);
    final Path ward = root.resolve("ward.toml");
    Files.writeString(
        ward,
        "version = 2\n[checks.ward]\nuses = \"ward\"\narchitecture-file = \"config/architecture.toml\"\n");
    final GateConfiguration check = new TomlConfigurationReader().read(ward).checks().get("ward");

    final GateResult result = ArchitectureGateRun.run(root, check);

    assertThat(result.findings().items())
        .singleElement()
        .satisfies(finding -> assertThat(finding.ruleId()).isEqualTo("ward:architecture/naming"));
  }

  @Test
  void aConfiguredFileThatDoesNotExistFailsTheGate() throws IOException {
    ArchitectureGateRun.copy(SWIFT, root, file -> true);
    final Path ward = root.resolve("ward.toml");
    Files.writeString(
        ward,
        "version = 2\n[checks.ward]\nuses = \"ward\"\narchitecture-file = \"config/missing.toml\"\n");
    final GateConfiguration check = new TomlConfigurationReader().read(ward).checks().get("ward");

    final GateResult result = ArchitectureGateRun.run(root, check);

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(text(result)).contains("config/missing.toml");
  }

  private void write(final String text) throws IOException {
    Files.writeString(
        root.resolve(RULE_FILE), text.startsWith("version") ? text : "version = 1\n\n" + text);
  }

  private static String text(final GateResult result) {
    return result.summary() + result.stdout() + result.stderr();
  }
}
