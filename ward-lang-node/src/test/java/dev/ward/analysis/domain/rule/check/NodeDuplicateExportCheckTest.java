package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.ProjectMatch;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The duplicate export check: one local binding under two export names in one module. */
final class NodeDuplicateExportCheckTest {

  @Test
  void reportsALocalBindingExportedUnderASecondName() {
    final List<ProjectMatch> found =
        new NodeDuplicateExportCheck("'{value}' is exported twice")
            .project(
                ProjectFixtures.trees(Map.of("index.ts", "const a = 1;\nexport { a, a as b };\n")),
                ProjectFixtures.manifests(Map.of()));

    assertThat(ProjectFixtures.positions(found)).containsExactly("index.ts:2:18");
    assertThat(ProjectFixtures.messages(found)).containsExactly("'a' is exported twice");
  }

  @Test
  void reportsADefaultExportBesideANamedExportOfTheSameBinding() {
    final List<ProjectMatch> found =
        new NodeDuplicateExportCheck("'{value}' is exported twice")
            .project(
                ProjectFixtures.trees(
                    Map.of("index.ts", "const a = 1;\nexport default a;\nexport { a };\n")),
                ProjectFixtures.manifests(Map.of()));

    assertThat(ProjectFixtures.positions(found)).containsExactly("index.ts:3:10");
  }

  @Test
  void keepsAReexportFromAnotherModule() {
    final List<ProjectMatch> found =
        new NodeDuplicateExportCheck("'{value}' is exported twice")
            .project(
                ProjectFixtures.trees(Map.of("index.ts", "export { a, a as b } from './lib';\n")),
                ProjectFixtures.manifests(Map.of()));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }
}
