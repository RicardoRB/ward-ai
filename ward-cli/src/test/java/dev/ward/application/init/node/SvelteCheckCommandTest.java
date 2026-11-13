package dev.ward.application.init.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.init.InitGeneralCatalog;
import dev.ward.application.init.InitInputs;
import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import dev.ward.domain.project.node.PackageManifest;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The command of a SvelteKit project's svelte-check gate, which depends on its check script. */
class SvelteCheckCommandTest {
  private static final Path ROOT = Path.of("/shop");
  private static final String SYNCING_CHECK =
      "svelte-kit sync && svelte-check --tsconfig ./tsconfig.json";

  @ParameterizedTest
  @CsvSource({
    "NPM, npm run check",
    "PNPM, pnpm run check",
    "YARN, yarn run check",
    "BUN, bun run check"
  })
  void runsTheCheckScriptThatSyncsSvelteKit(final BuildTool manager, final String command) {
    assertThat(String.join(" ", command(manager, syncing()))).isEqualTo(command);
  }

  @Test
  void keepsSvelteCheckWithoutASyncingCheckScript() {
    assertThat(String.join(" ", command(BuildTool.NPM, check("svelte-check"))))
        .isEqualTo("npx svelte-check");
    assertThat(String.join(" ", command(BuildTool.NPM, PackageManifest.EMPTY)))
        .isEqualTo("npx svelte-check");
  }

  private static PackageManifest syncing() {
    return check(SYNCING_CHECK);
  }

  private static PackageManifest check(final String script) {
    return new PackageManifest(Map.of("@sveltejs/kit", "^3.0.0"), null, null, null, script);
  }

  private static List<String> command(final BuildTool manager, final PackageManifest manifest) {
    final InitGateOption gate =
        new InitGeneralCatalog(
                new InitInputs(
                    new FakeEnvironmentInspection("npm", "pnpm", "yarn", "bun"),
                    root -> manifest,
                    root -> false))
            .gates(project(manager), null).stream()
                .filter(option -> "svelte-check".equals(option.id()))
                .findFirst()
                .orElseThrow();
    return gate.command();
  }

  private static Project project(final BuildTool manager) {
    return new Project(
        ROOT,
        Language.TYPESCRIPT,
        manager,
        Framework.SVELTE,
        false,
        ROOT,
        Set.of(),
        "24",
        TaskRunner.NONE,
        null);
  }
}
