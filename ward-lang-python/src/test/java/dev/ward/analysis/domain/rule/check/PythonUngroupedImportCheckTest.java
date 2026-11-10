package dev.ward.analysis.domain.rule.check;

import static dev.ward.analysis.domain.rule.check.PythonSources.positions;
import static dev.ward.analysis.domain.rule.check.PythonSources.tree;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The ungrouped-imports check reports an import whose package was imported before, not just before.
 */
final class PythonUngroupedImportCheckTest {

  private static java.util.List<String> run(final String source) {
    return positions(new PythonUngroupedImportCheck("ungrouped").matches(tree(source)));
  }

  @Test
  void importOfAPackageSplitByAnotherImportIsReported() {
    assertThat(run("import os\nimport json\nimport os.path\n")).containsExactly("3:1");
  }

  @Test
  void groupedImportsAreNotReported() {
    assertThat(run("import os\nimport os.path\nimport json\n")).isEmpty();
  }

  @Test
  void fromImportsCountTowardTheirPackage() {
    assertThat(run("from os import path\nimport json\nfrom os import sep\n"))
        .containsExactly("3:1");
  }

  @Test
  void relativeImportsAndFunctionLevelImportsAreIgnored() {
    assertThat(run("import os\nfrom . import x\nimport os.path\n")).isEmpty();
    assertThat(run("import os\nimport json\ndef f():\n    import os\n")).isEmpty();
  }
}
