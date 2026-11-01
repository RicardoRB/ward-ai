package dev.ward.infrastructure.init.node.turbo;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.TurboWorkspace;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Member scripts that differ from the plan are kept, or replaced on approval. */
class TurborepoScriptWriterTest {
  private static final String TURBO_JSON = "turbo.json";
  private static final String PACKAGE_JSON = "package.json";
  private static final String UI_DIR = "packages/ui";
  private static final String UI_NAME = "@repo/ui";
  private static final String LINT_SCRIPTS =
      "{\n  \"name\": \"@repo/ui\",\n  \"scripts\": {\n    \"lint\": \"eslint .\"\n  }\n}\n";
  private static final List<String> TASKS = List.of("check-types", "lint");

  @TempDir Path root;

  @Test
  void missingScriptIsWrittenAndDifferingScriptIsKept() throws IOException {
    turbo();
    final Path ui = member(LINT_SCRIPTS);

    final TurborepoWriter.Result result =
        TurborepoWriter.apply(
            workspace(
                ui, Map.of("check-types", "tsc --noEmit --pretty false", "lint", "biome ci .")),
            false);

    assertThat(Files.readString(ui.resolve(PACKAGE_JSON)))
        .contains("\"lint\": \"eslint .\"")
        .contains("\"check-types\": \"tsc --noEmit --pretty false\"");
    assertThat(result.kept()).containsExactly(UI_NAME + " keeps its lint script");
  }

  @Test
  void approvedReplacementReplacesTheDifferingScript() throws IOException {
    turbo();
    final Path ui = member(LINT_SCRIPTS);

    final TurborepoWriter.Result result =
        TurborepoWriter.apply(workspace(ui, Map.of("lint", "biome ci .")), true);

    assertThat(Files.readString(ui.resolve(PACKAGE_JSON))).contains("\"lint\": \"biome ci .\"");
    assertThat(result.kept()).isEmpty();
  }

  private TurboWorkspace workspace(final Path ui, final Map<String, String> scripts) {
    return new TurboWorkspace(
        root, List.of(new TurboWorkspace.Member(UI_NAME, ui, scripts)), TASKS);
  }

  private void turbo() throws IOException {
    Files.writeString(root.resolve(TURBO_JSON), "{\n  \"tasks\": {\n    \"build\": {}\n  }\n}\n");
  }

  private Path member(final String packageJson) throws IOException {
    final Path dir = root.resolve(UI_DIR);
    Files.createDirectories(dir);
    Files.writeString(dir.resolve(PACKAGE_JSON), packageJson);
    return dir;
  }
}
