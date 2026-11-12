package dev.ward.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.JsonTree;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;

class WardCliFindingsTest extends WardCliIntegrationSupport {
  // configureFailingGates() configures tsc, secrets, and tests; SARIF has a run per gate.
  private static final int CONFIGURED_GATES = 3;
  private static final int TYPE_ERROR_LINE = 42;
  private static final String SARIF = "sarif";
  private static final String OUTPUT_OPTION = "--output";
  // Built from two literals, so that no source file holds a token.
  private static final String SECRET = "ghp_" + "aB3dE5gH7jK9mN1pQ3sT5vW7yZ9bD1fH3jL5";

  private void script(final String name, final String body) throws IOException {
    final Path file = project.resolve(name);
    Files.writeString(file, "#!/bin/sh\n" + body);
    Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-xr-x"));
  }

  private void configureFailingGates() throws IOException {
    script(
        "fake-tsc",
        """
        echo "src/main/java/Foo.java(42,5): error TS2322: Type 'string' is not assignable."
        exit 1
        """);
    Files.createDirectories(project.resolve("config"));
    Files.writeString(project.resolve("config/app.yml"), "a: 1\nb: 2\ntoken: " + SECRET + "\n");
    Files.writeString(
        project.resolve(WARD_TOML),
        """
        version = 2
        [checks.tsc]
        uses = "tsc"
        command = ["./fake-tsc"]
        [checks.secrets]
        uses = "secrets"
        [checks.tests]
        uses = "tests"
        command = "./mvnw test"
        """);
  }

  @Test
  void jsonReportCarriesFindingsForFailedGates() throws IOException {
    configureFailingGates();

    final int exit = run(CHECK, FORMAT_OPTION, JSON, "-C", project.toString());

    assertThat(exit).isEqualTo(ExitCodes.FAILED);
    final JsonTree checks = JsonTree.parse(out.toString()).get(CHECKS);
    final JsonTree tsc = checks.get(0);
    assertThat(tsc.get("findingsTotal").asInt()).isEqualTo(1);
    final JsonTree finding = tsc.get("findings").get(0);
    assertThat(finding.get("rule").asString()).isEqualTo("TS2322");
    assertThat(finding.get("file").asString()).isEqualTo("src/main/java/Foo.java");
    assertThat(finding.get("line").asInt()).isEqualTo(TYPE_ERROR_LINE);
    final JsonTree secrets = checks.get(1);
    assertThat(secrets.get("findings").get(0).get("file").asString()).isEqualTo("config/app.yml");
    assertThat(checks.get(2).get("findings").size()).isZero();

    out.getBuffer().setLength(0);
    final int errorExit =
        run(CHECK, FORMAT_OPTION, JSON, "--severity", "error", "-C", project.toString());
    final JsonTree errorReport = JsonTree.parse(out.toString());
    out.getBuffer().setLength(0);

    final int infoExit =
        run(CHECK, FORMAT_OPTION, JSON, "--severity", "info", "-C", project.toString());
    final JsonTree infoReport = JsonTree.parse(out.toString());

    assertThat(errorExit).isEqualTo(infoExit).isEqualTo(ExitCodes.FAILED);
    assertThat(errorReport.get(STATUS).asString()).isEqualTo(infoReport.get(STATUS).asString());
    assertThat(errorReport.get("policies").toString())
        .isEqualTo(infoReport.get("policies").toString());
    assertThat(run(CHECK, "--severity", "debug", "-C", project.toString()))
        .isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("error", "warning", "info");
  }

  @Test
  void sarifReportGoesToStdoutWithoutSecrets() throws IOException {
    configureFailingGates();

    final int exit = run(CHECK, FORMAT_OPTION, SARIF, "-C", project.toString());

    assertThat(exit).isEqualTo(ExitCodes.FAILED);
    final String stdout = out.toString();
    assertThat(stdout).doesNotContain(SECRET);
    final JsonTree log = JsonTree.parse(stdout);
    assertThat(log.get("version").asString()).isEqualTo("2.1.0");
    assertThat(log.get("runs").size()).isEqualTo(CONFIGURED_GATES);
    assertThat(log.get("runs").get(0).get("results").get(0).get("ruleId").asString())
        .isEqualTo("TS2322");
    assertThat(log.get("runs").get(2).get("results").size()).isZero();
  }

  @Test
  void outputFileReceivesSarifWhileStdoutShowsText() throws IOException {
    configureFailingGates();
    final Path report = project.resolve("reports/nested/ward.sarif");

    final int exit =
        run(
            CHECK,
            FORMAT_OPTION,
            SARIF,
            OUTPUT_OPTION,
            report.toString(),
            "-C",
            project.toString());

    assertThat(exit).isEqualTo(ExitCodes.FAILED);
    assertThat(out.toString()).contains("Quality Gate: FAILED").doesNotContain("\"runs\"");
    assertThat(JsonTree.parse(Files.readString(report)).get("runs").size())
        .isEqualTo(CONFIGURED_GATES);
  }

  @Test
  void outputFileReceivesJson() throws IOException {
    pass();
    Files.writeString(
        project.resolve(WARD_TOML),
        "version = 2\n[checks.tests]\nuses = \"tests\"\ncommand = \"./mvnw test\"\n");
    final Path report = project.resolve("ward.json");

    assertThat(
            run(
                CHECK,
                FORMAT_OPTION,
                JSON,
                OUTPUT_OPTION,
                report.toString(),
                "-C",
                project.toString()))
        .isZero();
    assertThat(out.toString()).contains("Quality Gate: PASSED");
    assertThat(JsonTree.parse(Files.readString(report)).get(STATUS).asString()).isEqualTo("PASSED");
  }

  @Test
  void outputWithTextFormatIsUsageErrorAndWritesNothing() {
    final Path report = project.resolve("report.txt");

    final int exit =
        run(
            CHECK,
            FORMAT_OPTION,
            "text",
            OUTPUT_OPTION,
            report.toString(),
            "-C",
            project.toString());

    assertThat(exit).isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("--output requires --format json or --format sarif");
    assertThat(report).doesNotExist();
  }

  @Test
  void unknownFormatNamesAcceptedValues() {
    assertThat(run(CHECK, FORMAT_OPTION, "xml", "-C", project.toString()))
        .isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("text", "json", "sarif");
  }

  @Test
  void unwritableOutputIsAnError() throws IOException {
    pass();
    Files.writeString(
        project.resolve(WARD_TOML),
        "version = 2\n[checks.tests]\nuses = \"tests\"\ncommand = \"./mvnw test\"\n");
    Files.writeString(project.resolve("blocker"), "a file, not a directory");

    final int exit =
        run(
            CHECK,
            FORMAT_OPTION,
            SARIF,
            OUTPUT_OPTION,
            project.resolve("blocker/report.sarif").toString(),
            "-C",
            project.toString());

    assertThat(exit).isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("Cannot write report");
  }

  @Test
  void sarifErrorBeforeReportWritesNothingToStdout() {
    final int exit = run(CHECK, FORMAT_OPTION, SARIF, "-C", project.toString());

    assertThat(exit).isEqualTo(ExitCodes.ERROR);
    assertThat(out.toString()).isEmpty();
    assertThat(err.toString()).isNotBlank();
  }
}
