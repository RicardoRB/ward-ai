package dev.ward.application.init.python;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.init.PartialCheckDefaults;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The edit hooks skip mypy and the tests, which are never narrowed to the edited files. */
class PythonPartialChecksTest {
  private static final Project UV =
      new Project(Path.of("/shop"), Language.PYTHON, BuildTool.UV, Framework.NONE);

  @Test
  void mypyAndTestsAreNotPartialChecks() {
    assertThat(PartialCheckDefaults.forGate("mypy", UV, QualityPreset.STANDARD, List.of()))
        .isEmpty();
    assertThat(PartialCheckDefaults.forGate("pytest", UV, QualityPreset.STANDARD, List.of()))
        .isEmpty();
  }
}
