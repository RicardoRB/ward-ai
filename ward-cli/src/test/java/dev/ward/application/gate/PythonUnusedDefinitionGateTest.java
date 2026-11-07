package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.UnusedDefinitionFindings;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.analysis.ProjectDependencyAdapters;
import dev.ward.infrastructure.analysis.python.PythonDependencyAdapters;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The unused-function rule through the ward gate on two files: another file's use keeps it. */
class PythonUnusedDefinitionGateTest {

  @TempDir Path root;

  @Test
  void aFunctionUsedOnlyByAnotherFileIsKeptAndAnUnusedOneIsReported() throws IOException {
    write("app/lib.py", "def shared():\n    return 1\n\n\ndef lonely():\n    return 2\n");
    write("app/main_app.py", "from app.lib import shared\n\nvalue = shared()\n");

    final List<Finding> findings = findings();

    assertThat(findings).extracting(Finding::file).containsExactly("app/lib.py");
    assertThat(findings.get(0).message()).contains("lonely");
  }

  private List<Finding> findings() {
    final RuleDefinition rule =
        new RuleDefinition(
            UnusedDefinitionFindings.FUNCTION_ID,
            Set.of(AnalysisLanguage.PYTHON),
            RuleCapability.SYMBOLS,
            AnalysisSeverity.ERROR,
            "check = \"unused-definition\"");
    return LayerPorts.ruleFindings(
        new Project(root, Language.PYTHON, BuildTool.PIP, Framework.NONE),
        List.of(rule),
        new ProjectDependencyAdapters(List.of(new PythonDependencyAdapters())),
        UnusedDefinitionFindings.FUNCTION_ID);
  }

  private void write(final String path, final String text) throws IOException {
    final Path file = root.resolve(path);
    Files.createDirectories(file.getParent());
    Files.writeString(file, text);
  }
}
