package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.SliceCycleRule;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.analysis.ProjectDependencyAdapters;
import dev.ward.infrastructure.analysis.node.NodeDependencyAdapters;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The cycle rule through the ward gate on the TypeScript and JavaScript fixtures: a cycle between
 * two slices is reported, and a cycle inside one folder is not, as the design documents.
 */
class NodeSliceCycleGateTest {

  private static final Path FIXTURES = Path.of("src/test/resources/fixtures/node-slice-cycles");
  private static final Path SAME_FOLDER =
      Path.of("src/test/resources/fixtures/node-architecture/typescript/violations/cycle");

  @TempDir Path root;

  @ParameterizedTest
  @CsvSource({"typescript, ts", "javascript, js"})
  void reportsACycleBetweenTwoSlicesOnce(final String dialect, final String extension)
      throws IOException {
    copyTree(FIXTURES.resolve(dialect), FIXTURES.resolve(dialect));

    final List<Finding> findings = cycleFindings();

    assertThat(findings).hasSize(1);
    assertThat(findings.getFirst().message())
        .isEqualTo("slices form a cycle: billing -> orders -> billing");
    assertThat(findings.getFirst().file()).isEqualTo("src/billing/invoice." + extension);
  }

  @Test
  void reportsNothingForAFileCycleInsideOneFolder() throws IOException {
    copyTree(SAME_FOLDER, SAME_FOLDER);

    assertThat(cycleFindings()).isEmpty();
  }

  private List<Finding> cycleFindings() {
    return LayerPorts.ruleFindings(
        new Project(root, Language.TYPESCRIPT, BuildTool.NPM, Framework.NONE),
        List.of(ArchitectureGraphRules.cycles(List.of("src"))),
        new ProjectDependencyAdapters(List.of(new NodeDependencyAdapters())),
        SliceCycleRule.ID);
  }

  /** Copies the files under {@code folder} to their paths relative to {@code base}, in the root. */
  private void copyTree(final Path base, final Path folder) throws IOException {
    try (Stream<Path> files = Files.walk(folder)) {
      for (final Path file : files.filter(Files::isRegularFile).toList()) {
        final Path target = root.resolve(base.relativize(file).toString());
        Files.createDirectories(target.getParent());
        Files.copy(file, target);
      }
    }
  }
}
