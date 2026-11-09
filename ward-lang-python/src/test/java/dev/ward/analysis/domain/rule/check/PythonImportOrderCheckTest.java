package dev.ward.analysis.domain.rule.check;

import static dev.ward.analysis.domain.rule.check.PythonSources.tree;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.ProjectMatch;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * The import order check reports an import of an earlier group after a later one, across the whole
 * project, and reads first-party packages from the project's file paths.
 */
final class PythonImportOrderCheckTest {

  private static final PythonImportOrderCheck CHECK = new PythonImportOrderCheck("out of order");

  @Test
  void standardImportAfterThirdPartyIsReported() {
    final List<ProjectMatch> found =
        CHECK.project(sorted(Map.of("m.py", "import requests\nimport os\n")));
    assertThat(found).hasSize(1);
    assertThat(found.get(0).match().span().startLine()).isEqualTo(2);
  }

  @Test
  void futureImportAfterThirdPartyIsReported() {
    final List<ProjectMatch> found =
        CHECK.project(
            sorted(Map.of("m.py", "import requests\nfrom __future__ import annotations\n")));
    assertThat(found).hasSize(1);
    assertThat(found.get(0).match().span().startLine()).isEqualTo(2);
  }

  @Test
  void firstPartyPackageFromAnotherFileIsRecognized() {
    final Map<String, String> ordered =
        Map.of("app/models.py", "", "main.py", "import requests\nimport app\n");
    assertThat(CHECK.project(sorted(ordered))).isEmpty();
    final Map<String, String> misordered =
        Map.of("app/models.py", "", "main.py", "import app\nimport requests\n");
    assertThat(CHECK.project(sorted(misordered))).hasSize(1);
  }

  @Test
  void relativeImportBeforeStandardImportIsReported() {
    final List<ProjectMatch> found =
        CHECK.project(sorted(Map.of("m.py", "from . import x\nimport os\n")));
    assertThat(found).hasSize(1);
  }

  @Test
  void importsInsideFunctionsAreIgnored() {
    final List<ProjectMatch> found =
        CHECK.project(sorted(Map.of("m.py", "def f():\n    import os\nimport requests\n")));
    assertThat(found).isEmpty();
  }

  private static SortedMap<String, dev.ward.analysis.domain.ir.SyntaxNode> sorted(
      final Map<String, String> files) {
    final SortedMap<String, dev.ward.analysis.domain.ir.SyntaxNode> trees = new TreeMap<>();
    files.forEach((path, source) -> trees.put(path, tree(source)));
    return trees;
  }
}
