package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.ProjectMatch;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The unused-export and unused-type checks: imports by name, namespaces, entries and kinds. */
final class NodeUnusedExportCheckTest {

  private static final String MESSAGE = "unused {value}";

  @Test
  void reportsAnExportNoFileImports() {
    final List<ProjectMatch> found =
        new NodeUnusedExportCheck(MESSAGE, false)
            .project(
                ProjectFixtures.trees(
                    Map.of(
                        "index.ts", "export const app = 1;",
                        "lib.ts", "export const used = 1;\nexport const unused = 2;")),
                ProjectFixtures.manifests(Map.of()));

    assertThat(ProjectFixtures.positions(found)).containsExactly("lib.ts:1:14", "lib.ts:2:14");
    assertThat(ProjectFixtures.messages(found)).containsExactly("unused used", "unused unused");
  }

  @Test
  void keepsAnExportImportedByName() {
    final List<ProjectMatch> found =
        new NodeUnusedExportCheck(MESSAGE, false)
            .project(
                ProjectFixtures.trees(
                    Map.of(
                        "index.ts", "import { used } from './lib';\nexport const app = used;",
                        "lib.ts", "export const used = 1;")),
                ProjectFixtures.manifests(Map.of()));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }

  @Test
  void treatsEveryExportAsUsedWhenANamespaceIsImported() {
    final List<ProjectMatch> found =
        new NodeUnusedExportCheck(MESSAGE, false)
            .project(
                ProjectFixtures.trees(
                    Map.of(
                        "index.ts", "import * as lib from './lib';\nexport const app = lib;",
                        "lib.ts", "export const unused = 1;")),
                ProjectFixtures.manifests(Map.of()));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }

  @Test
  void reportsUnusedTypesOnlyInTypesMode() {
    final Map<String, String> sources =
        Map.of(
            "index.ts", "export const app = 1;",
            "lib.ts", "export type Shape = number;\nexport const value = 2;");

    final List<ProjectMatch> types =
        new NodeUnusedExportCheck(MESSAGE, true)
            .project(ProjectFixtures.trees(sources), ProjectFixtures.manifests(Map.of()));
    final List<ProjectMatch> values =
        new NodeUnusedExportCheck(MESSAGE, false)
            .project(ProjectFixtures.trees(sources), ProjectFixtures.manifests(Map.of()));

    assertThat(ProjectFixtures.positions(types)).containsExactly("lib.ts:1:13");
    assertThat(ProjectFixtures.positions(values)).containsExactly("lib.ts:2:14");
  }

  @Test
  void neverReportsExportsOfAnEntryFile() {
    final List<ProjectMatch> found =
        new NodeUnusedExportCheck(MESSAGE, false)
            .project(
                ProjectFixtures.trees(Map.of("index.ts", "export const app = 1;")),
                ProjectFixtures.manifests(Map.of()));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }

  @Test
  void keepsATypeTheFileItselfRefersTo() {
    final List<ProjectMatch> found =
        new NodeUnusedExportCheck(MESSAGE, true)
            .project(
                ProjectFixtures.trees(
                    Map.of(
                        "lib.ts",
                        "export interface Order { id: string }\nexport function save(order: Order): void {}")),
                ProjectFixtures.manifests(Map.of()));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }

  @Test
  void stillReportsAValueOnlyItsOwnFileCalls() {
    final List<ProjectMatch> found =
        new NodeUnusedExportCheck(MESSAGE, false)
            .project(
                ProjectFixtures.trees(
                    Map.of(
                        "lib.ts",
                        "export interface Order { id: string }\nexport function save(order: Order): void {}\nsave({ id: '' });")),
                ProjectFixtures.manifests(Map.of()));

    assertThat(ProjectFixtures.positions(found)).containsExactly("lib.ts:2:17");
  }
}
