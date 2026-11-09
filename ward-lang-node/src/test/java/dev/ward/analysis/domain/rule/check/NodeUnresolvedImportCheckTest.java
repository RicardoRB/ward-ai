package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.ProjectMatch;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The unresolved-import check: relative specifiers only, against the project's files. */
final class NodeUnresolvedImportCheckTest {

  private static final String MESSAGE = "unresolved {value}";

  @Test
  void reportsARelativeImportThatNamesNoFile() {
    final List<ProjectMatch> found =
        new NodeUnresolvedImportCheck(MESSAGE)
            .project(
                ProjectFixtures.trees(
                    Map.of("index.ts", "import { gone } from './gone';\nexport const a = gone;")));

    assertThat(ProjectFixtures.positions(found)).containsExactly("index.ts:1:22");
    assertThat(ProjectFixtures.messages(found)).containsExactly("unresolved ./gone");
  }

  @Test
  void resolvesAnImportToAFileWithAnExtensionOrIndex() {
    final List<ProjectMatch> found =
        new NodeUnresolvedImportCheck(MESSAGE)
            .project(
                ProjectFixtures.trees(
                    Map.of(
                        "index.ts",
                            "import { a } from './lib';\nimport { b } from './util/index';\nexport const c = a;",
                        "lib.ts", "export const a = 1;",
                        "util/index.ts", "export const b = 2;")));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }

  @Test
  void neverJudgesABareSpecifier() {
    final List<ProjectMatch> found =
        new NodeUnresolvedImportCheck(MESSAGE)
            .project(
                ProjectFixtures.trees(
                    Map.of("index.ts", "import React from 'react';\nexport const a = React;")));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }
}
