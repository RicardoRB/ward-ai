package dev.ward.application.init.node.turbo;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.WorkspaceMember;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Member scripts of a Turborepo workspace: each member's own command, without the runner. */
class TurborepoMemberScriptsTest {
  @TempDir Path root;

  @Test
  void frameworkMemberGetsItsOwnChecker() {
    final WorkspaceMember astro = TurborepoFixtures.memberOf(root, "apps/astro", Framework.ASTRO);

    final List<TurborepoPlan.MemberPlan> scripts =
        TurborepoPlan.memberScripts(
            TurborepoFixtures.rootProject(root, BuildTool.PNPM),
            QualityPreset.STANDARD,
            TurborepoFixtures.inputs(
                root,
                List.of(astro),
                Map.of(
                    astro.directory(),
                    new PackageManifest(Map.of("astro", "^5.0.0"), null, null))));

    assertThat(scripts).hasSize(1);
    assertThat(scripts.getFirst().scripts().get("check-types")).isEqualTo("astro check");
  }

  @Test
  void configOnlyMemberIsSkipped() {
    final WorkspaceMember config =
        TurborepoFixtures.memberOf(root, "packages/eslint-config", Framework.NONE);

    assertThat(
            TurborepoPlan.memberScripts(
                TurborepoFixtures.rootProject(root, BuildTool.PNPM),
                QualityPreset.STANDARD,
                TurborepoFixtures.inputs(root, List.of(config), Map.of(), Set.of())))
        .isEmpty();
  }

  @Test
  void biomeAtTheRootIsTheLintToolOfEveryMember() {
    final WorkspaceMember web =
        TurborepoFixtures.memberOf(root, TurborepoFixtures.WEB, Framework.NONE);
    final PackageManifest biome =
        new PackageManifest(Map.of("@biomejs/biome", "2.3.0"), null, null);

    final TurborepoPlan.MemberPlan member =
        TurborepoPlan.memberScripts(
                TurborepoFixtures.rootProject(root, BuildTool.PNPM),
                QualityPreset.STANDARD,
                TurborepoFixtures.inputs(
                    root, List.of(web), Map.of(root, biome), Set.of(web.directory())))
            .getFirst();

    assertThat(member.scripts().get("lint")).startsWith("biome ci");
  }
}
