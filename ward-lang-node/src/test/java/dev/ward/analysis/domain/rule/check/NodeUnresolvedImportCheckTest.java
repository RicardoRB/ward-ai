package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.ProjectFiles;
import dev.ward.analysis.domain.rule.ProjectMatch;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

  @Test
  void resolvesAnImportOfAnExistingFileOfAnyLanguage() {
    final List<ProjectMatch> found =
        new NodeUnresolvedImportCheck(MESSAGE)
            .project(
                ProjectFixtures.trees(
                    Map.of(
                        "src/main.ts",
                        "import App from './App.vue';\nimport './assets/main.css';\nexport const a = App;")),
                new ProjectFiles(Map.of(), Set.of("src/App.vue", "src/assets/main.css")));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }

  @Test
  void reportsAnImportOfAMissingFileOfAnyLanguage() {
    final List<ProjectMatch> found =
        new NodeUnresolvedImportCheck(MESSAGE)
            .project(
                ProjectFixtures.trees(
                    Map.of("src/main.ts", "import App from './Gone.vue';\nexport const a = App;")),
                new ProjectFiles(Map.of(), Set.of("src/App.vue")));

    assertThat(ProjectFixtures.positions(found)).containsExactly("src/main.ts:1:17");
    assertThat(ProjectFixtures.messages(found)).containsExactly("unresolved ./Gone.vue");
  }

  @Test
  void neverJudgesAFolderThatIsHiddenOrBuildOutput() {
    final List<ProjectMatch> found =
        new NodeUnresolvedImportCheck(MESSAGE)
            .project(
                ProjectFixtures.trees(
                    Map.of(
                        "src/main.ts",
                        "import a from './.storybook/a';\nimport b from '../dist/b.js';\nexport const c = [a, b];")),
                new ProjectFiles(Map.of(), Set.of()));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }
}
