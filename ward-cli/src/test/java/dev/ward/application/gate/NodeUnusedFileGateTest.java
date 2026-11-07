package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.UnusedFileRule;
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

/**
 * The unused-file rule through the ward gate on a workspace: a file that no file imports is
 * reported, while manifest entries (main and exports) and files matching an entry glob are not.
 */
class NodeUnusedFileGateTest {

  private static final Path WORKSPACE =
      Path.of("src/test/resources/fixtures/node-unused-file/workspace");

  @TempDir Path root;

  @Test
  void reportsOnlyTheFileNothingImports() throws IOException {
    copyTree();

    final List<Finding> findings = unusedFindings(List.of("**/*.test.ts"));

    assertThat(findings).extracting(Finding::file).containsExactly("packages/app/src/orphan.ts");
  }

  @Test
  void anEntryGlobKeepsAFileOut() throws IOException {
    copyTree();

    final List<Finding> findings = unusedFindings(List.of("**/*.test.ts", "**/orphan.ts"));

    assertThat(findings).isEmpty();
  }

  private List<Finding> unusedFindings(final List<String> entries) {
    return LayerPorts.ruleFindings(
        new Project(root, Language.TYPESCRIPT, BuildTool.NPM, Framework.NONE),
        List.of(ArchitectureGraphRules.unusedFiles(entries)),
        new ProjectDependencyAdapters(List.of(new NodeDependencyAdapters())),
        UnusedFileRule.ID);
  }

  /** Copies the workspace fixture into the temporary project root. */
  private void copyTree() throws IOException {
    try (Stream<Path> files = Files.walk(WORKSPACE)) {
      for (final Path file : files.filter(Files::isRegularFile).toList()) {
        final Path target = root.resolve(WORKSPACE.relativize(file).toString());
        Files.createDirectories(target.getParent());
        Files.copy(file, target);
      }
    }
  }
}
