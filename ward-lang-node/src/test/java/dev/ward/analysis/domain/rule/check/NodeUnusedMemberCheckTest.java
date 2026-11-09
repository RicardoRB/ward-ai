package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.ProjectMatch;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The unused enum and namespace member check: reaches through named imports, and escapes. */
final class NodeUnusedMemberCheckTest {

  private static final String MESSAGE = "unused {value}";
  private static final String ENUM = "enum";
  private static final String ENUM_DECLARATION = "export enum Color { Red, Green }\n";

  @Test
  void reportsAnEnumMemberNoImporterReaches() {
    final List<ProjectMatch> found =
        new NodeUnusedMemberCheck(MESSAGE, ENUM)
            .project(
                ProjectFixtures.trees(
                    Map.of(
                        "color.ts",
                        ENUM_DECLARATION,
                        "index.ts",
                        "import { Color } from './color';\nexport const c = Color.Red;")),
                ProjectFixtures.manifests(Map.of()));

    assertThat(ProjectFixtures.positions(found)).containsExactly("color.ts:1:26");
  }

  @Test
  void treatsEveryMemberAsUsedWhenTheObjectEscapes() {
    final List<ProjectMatch> found =
        new NodeUnusedMemberCheck(MESSAGE, ENUM)
            .project(
                ProjectFixtures.trees(
                    Map.of(
                        "color.ts",
                        ENUM_DECLARATION,
                        "index.ts",
                        "import { Color } from './color';\nexport const all = Object.keys(Color);")),
                ProjectFixtures.manifests(Map.of()));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }

  @Test
  void treatsEveryMemberAsUsedWhenANamespaceIsImported() {
    final List<ProjectMatch> found =
        new NodeUnusedMemberCheck(MESSAGE, ENUM)
            .project(
                ProjectFixtures.trees(
                    Map.of(
                        "color.ts",
                        ENUM_DECLARATION,
                        "index.ts",
                        "import * as all from './color';\nexport const c = all;")),
                ProjectFixtures.manifests(Map.of()));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }

  @Test
  void ignoresAMemberReachedThroughAnotherModule() {
    final List<ProjectMatch> found =
        new NodeUnusedMemberCheck(MESSAGE, ENUM)
            .project(
                ProjectFixtures.trees(
                    Map.of(
                        "color.ts", ENUM_DECLARATION,
                        "other.ts", "export enum Color { Blue }\n",
                        "index.ts",
                            "import { Color } from './other';\nexport const c = Color.Blue;")),
                ProjectFixtures.manifests(Map.of()));

    assertThat(ProjectFixtures.positions(found)).containsExactly("color.ts:1:21", "color.ts:1:26");
  }

  @Test
  void reportsAnUnusedNamespaceMember() {
    final List<ProjectMatch> found =
        new NodeUnusedMemberCheck(MESSAGE, "namespace")
            .project(
                ProjectFixtures.trees(
                    Map.of(
                        "util.ts",
                            "export namespace Util {\n  export const a = 1;\n  export const b = 2;\n}\n",
                        "index.ts", "import { Util } from './util';\nexport const u = Util.a;")),
                ProjectFixtures.manifests(Map.of()));

    assertThat(ProjectFixtures.positions(found)).containsExactly("util.ts:3:16");
  }
}
