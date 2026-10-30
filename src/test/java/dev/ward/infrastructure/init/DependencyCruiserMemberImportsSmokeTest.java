package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import dev.ward.infrastructure.project.FileSystemNodeToolConfigs;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs a real dependency-cruiser on a Turborepo-shaped workspace whose members import each other.
 * The rules match layer directories anywhere in a path, so a member's domain may not import another
 * member's infrastructure. Needs {@code -Dward.depcruise.binary} like the other smoke test.
 */
@EnabledIfSystemProperty(named = "ward.depcruise.binary", matches = ".+")
class DependencyCruiserMemberImportsSmokeTest {
  private static final long TIMEOUT_SECONDS = 120;
  private static final String CONFIG = ".dependency-cruiser.mjs";

  @TempDir Path root;

  @Test
  void domainOfOneMemberMayNotImportInfrastructureOfAnother() throws Exception {
    workspace();
    Files.writeString(
        root.resolve("apps/web/src/domain/order.ts"),
        "import { db } from \"../../../../packages/ui/src/infrastructure/db\";\nexport const order = db;\n");

    final Result result = run();

    assertThat(result.exit()).isNotZero();
    assertThat(result.output()).contains("hexagonal-architecture");
  }

  @Test
  void aMemberPortOfAnotherMemberIsAllowed() throws Exception {
    workspace();
    Files.writeString(
        root.resolve("apps/web/src/controller/page.ts"),
        "import { button } from \"../../../../packages/ui/src/domain/button\";\nexport const page = button;\n");

    final Result result = run();

    assertThat(result.exit()).as(result.output()).isZero();
  }

  /** Writes two members with layered sources, and generates the hexagonal config at the root. */
  private void workspace() throws IOException {
    write("packages/ui/src/domain/button.ts", "export const button = \"ui\";\n");
    write("packages/ui/src/infrastructure/db.ts", "export const db = 1;\n");
    write("apps/web/src/controller/index.ts", "export {};\n");
    write("apps/web/src/domain/index.ts", "export {};\n");
    new DependencyCruiserConfigGenerator(new FileSystemNodeToolConfigs())
        .apply(
            new Project(
                root,
                Language.TYPESCRIPT,
                BuildTool.NPM,
                Framework.NONE,
                false,
                false,
                root,
                Set.of(),
                "24",
                TaskRunner.TURBO),
            "hexagonal",
            new QualityPreset.ArchitectureExtras(false, false, false));
  }

  private void write(final String relative, final String content) throws IOException {
    final Path file = root.resolve(relative);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }

  private Result run() throws IOException, InterruptedException {
    final Path log = root.resolve("depcruise.log");
    final Process process =
        new ProcessBuilder(
                System.getProperty("ward.depcruise.binary"),
                ".",
                "--config",
                CONFIG,
                "--output-type",
                "err")
            .directory(root.toFile())
            .redirectErrorStream(true)
            .redirectOutput(log.toFile())
            .start();
    assertThat(process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    return new Result(process.exitValue(), Files.readString(log, StandardCharsets.UTF_8));
  }

  private record Result(int exit, String output) {}
}
