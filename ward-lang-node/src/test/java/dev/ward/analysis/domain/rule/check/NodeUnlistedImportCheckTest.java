package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.ProjectMatch;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The unlisted-import check: bare packages the nearest manifest does not list. */
final class NodeUnlistedImportCheckTest {

  private static final String MESSAGE = "unlisted {value}";
  private static final String MANIFEST = "{\"dependencies\":{\"react\":\"^18\"}}";

  @Test
  void reportsAPackageTheNearestManifestDoesNotList() {
    final List<ProjectMatch> found =
        new NodeUnlistedImportCheck(MESSAGE)
            .project(
                ProjectFixtures.trees(
                    Map.of("index.ts", "import _ from 'lodash';\nexport const a = _;")),
                ProjectFixtures.manifests(Map.of("package.json", MANIFEST)));

    assertThat(ProjectFixtures.positions(found)).containsExactly("index.ts:1:15");
    assertThat(ProjectFixtures.messages(found)).containsExactly("unlisted lodash");
  }

  @Test
  void keepsAListedPackageAndSubpath() {
    final List<ProjectMatch> found =
        new NodeUnlistedImportCheck(MESSAGE)
            .project(
                ProjectFixtures.trees(
                    Map.of(
                        "index.ts",
                        "import R from 'react';\nimport j from 'react/jsx-runtime';\nexport const a = R;")),
                ProjectFixtures.manifests(Map.of("package.json", MANIFEST)));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }

  @Test
  void neverReportsBuiltInsRelativeOrSubpathImports() {
    final List<ProjectMatch> found =
        new NodeUnlistedImportCheck(MESSAGE)
            .project(
                ProjectFixtures.trees(
                    Map.of(
                        "index.ts",
                        "import fs from 'node:fs';\nimport p from 'path';\nimport l from './lib';\nimport x from '#internal';\nexport const a = [fs, p, l, x];",
                        "lib.ts",
                        "export const b = 1;")),
                ProjectFixtures.manifests(Map.of("package.json", MANIFEST)));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }

  @Test
  void reportsNothingWithoutAManifestAbove() {
    final List<ProjectMatch> found =
        new NodeUnlistedImportCheck(MESSAGE)
            .project(
                ProjectFixtures.trees(
                    Map.of("index.ts", "import _ from 'lodash';\nexport const a = _;")),
                ProjectFixtures.manifests(Map.of()));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }
}
