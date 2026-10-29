package dev.ward.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.JsonTree;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** ESLint problems of a TypeScript project reach the JSON and SARIF reports as findings. */
@DisabledOnOs(OS.WINDOWS)
class WardCliNodeFindingsTest extends WardCliIntegrationSupport {
  private static final String RULE = "no-unused-vars";
  private static final String FILE = "src/app.ts";

  @TempDir Path node;

  private void failingEslintProject() throws IOException {
    Files.writeString(node.resolve("package.json"), "{\"packageManager\": \"pnpm@9.12.0\"}");
    Files.writeString(node.resolve("tsconfig.json"), "{}");
    final Path eslint = node.resolve("fake-eslint");
    // Prints what `eslint --format json` prints, with ESLint's absolute file path.
    Files.writeString(
        eslint,
        """
        #!/bin/sh
        echo "> web@1.0.0 lint"
        cat <<JSON
        [{"filePath":"$PWD/src/app.ts","messages":[{"ruleId":"no-unused-vars","severity":2,
          "message":"x is never used.","line":4,"column":7}]}]
        JSON
        exit 1
        """);
    Files.setPosixFilePermissions(eslint, PosixFilePermissions.fromString("rwxr-xr-x"));
    Files.writeString(
        node.resolve(WARD_TOML),
"""
        version = 2
        [checks.eslint]
uses = "eslint"
        command = ["./fake-eslint"]
        """);
  }

  @Test
  void jsonReportCarriesEslintFindings() throws IOException {
    failingEslintProject();

    assertThat(run(CHECK, FORMAT_OPTION, JSON, "-C", node.toString())).isEqualTo(ExitCodes.FAILED);
    final JsonTree eslint = JsonTree.parse(out.toString()).get(CHECKS).get(0);
    assertThat(eslint.get("findingsTotal").asInt()).isEqualTo(1);
    final JsonTree finding = eslint.get("findings").get(0);
    assertThat(finding.get("rule").asString()).isEqualTo(RULE);
    assertThat(finding.get("file").asString()).isEqualTo(FILE);
  }

  @Test
  void sarifReportCarriesEslintFindings() throws IOException {
    failingEslintProject();

    assertThat(run(CHECK, FORMAT_OPTION, "sarif", "-C", node.toString()))
        .isEqualTo(ExitCodes.FAILED);
    final JsonTree result = JsonTree.parse(out.toString()).get("runs").get(0).get("results").get(0);
    assertThat(result.get("ruleId").asString()).isEqualTo(RULE);
    assertThat(out.toString()).contains(FILE);
  }
}
