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

/** The bundled structural rules report only through a {@code ward} check that names them. */
@DisabledOnOs(OS.WINDOWS)
class WardCliStructuralFindingsTest extends WardCliIntegrationSupport {
  private static final String NAMING = "ward:typescript/naming";
  private static final String TYPESCRIPT_PREFIX = "ward:typescript/";
  private static final String JAVASCRIPT_PREFIX = "ward:javascript/";

  @TempDir Path source;

  private void nodeProject() throws IOException {
    Files.writeString(source.resolve("package.json"), "{\"packageManager\": \"pnpm@9.12.0\"}");
    Files.writeString(source.resolve("tsconfig.json"), "{}");
  }

  private void wardProjectWithUnderscoredName() throws IOException {
    nodeProject();
    Files.createDirectories(source.resolve("src"));
    Files.writeString(source.resolve("src/app.ts"), "export const user_name = 1;\n");
    Files.writeString(
        source.resolve(WARD_TOML),
        """
        version = 2
        [checks.structure]
        uses = "ward"
        rules = ["%s"]
        """
            .formatted(NAMING));
  }

  @Test
  void twoRunsOverTheSameProjectProduceByteIdenticalJson() throws IOException {
    wardProjectWithUnderscoredName();

    assertThat(run(CHECK, FORMAT_OPTION, JSON, "-C", source.toString()))
        .isEqualTo(ExitCodes.FAILED);
    final String first = out.toString();
    out.getBuffer().setLength(0);
    assertThat(run(CHECK, FORMAT_OPTION, JSON, "-C", source.toString()))
        .isEqualTo(ExitCodes.FAILED);

    assertThat(out.toString()).isEqualTo(first).contains(NAMING);
  }

  @Test
  void projectWithoutAWardCheckReportsNoStructuralRules() throws IOException {
    nodeProject();
    Files.writeString(source.resolve("fake-eslint"), "#!/bin/sh\necho '[]'\n");
    Files.setPosixFilePermissions(
        source.resolve("fake-eslint"), PosixFilePermissions.fromString("rwxr-xr-x"));
    Files.writeString(
        source.resolve(WARD_TOML),
        """
        version = 2
        [checks.eslint]
        uses = "eslint"
        command = ["./fake-eslint"]
        """);

    assertThat(run(CHECK, FORMAT_OPTION, JSON, "-C", source.toString())).isZero();

    final JsonTree checks = JsonTree.parse(out.toString()).get(CHECKS);
    assertThat(checks.size()).isEqualTo(1);
    assertThat(out.toString()).doesNotContain(TYPESCRIPT_PREFIX).doesNotContain(JAVASCRIPT_PREFIX);
  }
}
