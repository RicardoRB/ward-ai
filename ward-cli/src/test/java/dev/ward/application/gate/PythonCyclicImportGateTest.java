package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.rule.CyclicImportRule;
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

/** The cyclic-import rule through the ward gate on a project with no architecture configuration. */
class PythonCyclicImportGateTest {

  @TempDir Path root;

  @Test
  void aTwoModuleCycleIsReportedOnceAtTheFirstImportOfTheFirstModule() throws IOException {
    write("app/x.py", "from app import y\n\n\ndef x():\n    return y.y()\n");
    write("app/y.py", "from app import x\n\n\ndef y():\n    return x.x()\n");

    final List<Finding> findings = findings();

    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).file()).isEqualTo("app/x.py");
    assertThat(findings.get(0).message()).isEqualTo("Cyclic import (app.x -> app.y)");
  }

  @Test
  void anImportUnderTypeCheckingIsNotAnEdge() throws IOException {
    write("app/a.py", "from app import b\n\n\ndef a():\n    return b.b()\n");
    write(
        "app/b.py",
        "from typing import TYPE_CHECKING\n\nif TYPE_CHECKING:\n    from app import a\n\n\n"
            + "def b():\n    return 1\n");

    assertThat(findings()).isEmpty();
  }

  private List<Finding> findings() {
    final RuleDefinition rule =
        new RuleDefinition(
            CyclicImportRule.ID,
            Set.of(AnalysisLanguage.PYTHON),
            RuleCapability.SYMBOLS,
            AnalysisSeverity.ERROR,
            "check = \"cyclic-import\"");
    return LayerPorts.ruleFindings(
        new Project(root, Language.PYTHON, BuildTool.PIP, Framework.NONE),
        List.of(rule),
        new ProjectDependencyAdapters(List.of(new PythonDependencyAdapters())),
        CyclicImportRule.ID);
  }

  private void write(final String path, final String text) throws IOException {
    final Path file = root.resolve(path);
    Files.createDirectories(file.getParent());
    Files.writeString(file, text);
  }
}
