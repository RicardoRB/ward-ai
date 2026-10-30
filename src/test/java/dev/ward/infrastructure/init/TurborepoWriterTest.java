package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.config.WardException;
import dev.ward.domain.init.TurboWorkspace;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Member scripts and turbo.json tasks are written, kept when they differ, and rolled back as one.
 */
class TurborepoWriterTest {
  private static final String CREATE_TURBO =
      """
      {
        "$schema": "https://turborepo.com/schema.json",
        "tasks": {
          "build": {
            "dependsOn": ["^build"],
            "outputs": [".next/**"]
          },
          "dev": {
            "cache": false
          }
        }
      }
      """;
  private static final List<String> TASKS = List.of("check-types", "lint", "test", "mutation");

  private static final String TURBO_JSON = "turbo.json";
  private static final String PACKAGE_JSON = "package.json";

  @TempDir Path root;

  private Path turboJson() {
    return root.resolve(TURBO_JSON);
  }

  @Test
  void createTurboTaskFileGetsTheGateTasksAndKeepsBuildAndDev() throws IOException {
    turbo(CREATE_TURBO);

    TurborepoWriter.apply(workspace(Map.of()), false);

    assertThat(Files.readString(turboJson()))
        .isEqualTo(
            """
            {
              "$schema": "https://turborepo.com/schema.json",
              "tasks": {
                "build": {
                  "dependsOn": ["^build"],
                  "outputs": [".next/**"]
                },
                "dev": {
                  "cache": false
                },
                "check-types": {},
                "lint": {},
                "test": {"dependsOn": ["^build"], "outputs": ["coverage/**"]},
                "mutation": {"outputs": ["reports/**"]}
              }
            }
            """);
  }

  @Test
  void testTaskDependsOnBuildOnlyWhenTheWorkspaceHasABuildTask() throws IOException {
    turbo("{\n  \"tasks\": {\n    \"lint\": {}\n  }\n}\n");

    TurborepoWriter.apply(workspace(Map.of()), false);

    assertThat(Files.readString(turboJson()))
        .contains("\"test\": {\"outputs\": [\"coverage/**\"]}")
        .doesNotContain("^build");
  }

  @Test
  void existingTaskKeepsItsSettings() throws IOException {
    turbo("{\n  \"tasks\": {\n    \"test\": {\"cache\": false}\n  }\n}\n");

    TurborepoWriter.apply(workspace(Map.of()), false);

    assertThat(Files.readString(turboJson()))
        .contains("\"test\": {\"cache\": false}")
        .doesNotContain("coverage/**");
  }

  @Test
  void rollbackRestoresEveryFileByteForByte() throws IOException {
    turbo(CREATE_TURBO);
    final Path ui = member("packages/ui", "{\n  \"name\": \"@repo/ui\"\n}\n");
    final String uiBefore = Files.readString(ui.resolve(PACKAGE_JSON));
    final String turboBefore = Files.readString(turboJson());

    final TurborepoWriter.Result result =
        TurborepoWriter.apply(
            workspace(Map.of("check-types", "tsc --noEmit --pretty false")), false);
    TurborepoWriter.rollback(result.written());

    assertThat(Files.readString(ui.resolve(PACKAGE_JSON))).isEqualTo(uiBefore);
    assertThat(Files.readString(turboJson())).isEqualTo(turboBefore);
  }

  @Test
  void failurePartwayRestoresTheFilesAlreadyChanged() throws IOException {
    turbo(CREATE_TURBO);
    final Path ui = member("packages/ui", "{\n  \"name\": \"@repo/ui\"\n}\n");
    final String uiBefore = Files.readString(ui.resolve(PACKAGE_JSON));
    final Path broken = member("packages/api", "{ not json");
    final String brokenBefore = Files.readString(broken.resolve(PACKAGE_JSON));
    final String turboBefore = Files.readString(turboJson());

    assertThatThrownBy(
            () ->
                TurborepoWriter.apply(
                    new TurboWorkspace(
                        root,
                        List.of(
                            new TurboWorkspace.Member("@repo/ui", ui, Map.of("lint", "biome ci .")),
                            new TurboWorkspace.Member(
                                "@repo/api", broken, Map.of("lint", "biome ci ."))),
                        TASKS),
                    false))
        .isInstanceOf(WardException.class);

    assertThat(Files.readString(ui.resolve(PACKAGE_JSON))).isEqualTo(uiBefore);
    assertThat(Files.readString(broken.resolve(PACKAGE_JSON))).isEqualTo(brokenBefore);
    assertThat(Files.readString(turboJson())).isEqualTo(turboBefore);
  }

  private TurboWorkspace workspace(final Map<String, String> scripts) throws IOException {
    final Path ui = root.resolve("packages/ui");
    if (!Files.isRegularFile(ui.resolve(PACKAGE_JSON))) {
      member("packages/ui", "{\n  \"name\": \"@repo/ui\"\n}\n");
    }
    return new TurboWorkspace(
        root, List.of(new TurboWorkspace.Member("@repo/ui", ui, scripts)), TASKS);
  }

  private void turbo(final String content) throws IOException {
    Files.writeString(turboJson(), content);
  }

  private Path member(final String relative, final String packageJson) throws IOException {
    final Path dir = root.resolve(relative);
    Files.createDirectories(dir);
    Files.writeString(dir.resolve(PACKAGE_JSON), packageJson);
    return dir;
  }
}
