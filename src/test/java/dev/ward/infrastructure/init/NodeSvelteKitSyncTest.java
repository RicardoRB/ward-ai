package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The SvelteKit sync note: a plan note for a missing {@code svelte-kit sync}, never a finding. */
class NodeSvelteKitSyncTest {
  private static final String SCAFFOLDED = "svelte-kit sync || echo ''";
  private static final String KIT_NOTE =
      "svelte-check needs svelte-kit sync: add \"prepare\": \"svelte-kit sync || echo ''\" to"
          + " package.json scripts.";

  @TempDir Path root;

  @Test
  void scaffoldedPrepareScriptGivesNoNote() {
    assertThat(check(kit(SCAFFOLDED), Set.of("svelte-check")).notes()).isEmpty();
  }

  @Test
  void missingPrepareScriptGivesNote() {
    final NodeCompatibility compatibility =
        check(
            new PackageManifest(Map.of("@sveltejs/kit", "^3.1.0"), null, null),
            Set.of("svelte-check"));

    assertThat(compatibility.notes()).containsExactly(KIT_NOTE);
    assertThat(compatibility.incompatible()).isFalse();
  }

  @Test
  void huskyPrepareScriptGivesNote() {
    assertThat(check(kit("husky"), Set.of("svelte-check")).notes()).containsExactly(KIT_NOTE);
  }

  @Test
  void svelteWithoutKitGivesNoNote() {
    final PackageManifest svelte = new PackageManifest(Map.of("svelte", "^5.0.0"), null, null);

    assertThat(check(svelte, Set.of("svelte-check")).notes()).isEmpty();
  }

  @Test
  void noteIsOnlyForSelectedSvelteCheckGate() {
    assertThat(check(kit("husky"), Set.of("eslint")).notes()).isEmpty();
  }

  private PackageManifest kit(final String prepare) {
    return new PackageManifest(Map.of("@sveltejs/kit", "^3.1.0"), null, null, prepare);
  }

  private NodeCompatibility check(final PackageManifest manifest, final Set<String> gates) {
    return NodeCompatibility.check(project(), manifest, List.of(), gates);
  }

  private Project project() {
    return new Project(
        root,
        Language.TYPESCRIPT,
        BuildTool.NPM,
        Framework.SVELTE,
        false,
        false,
        root,
        Set.of(),
        "24",
        TaskRunner.NONE);
  }
}
