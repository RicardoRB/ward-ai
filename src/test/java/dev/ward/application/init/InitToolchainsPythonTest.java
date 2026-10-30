package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Init configures uv, Poetry and pip projects; PDM, Hatch and other toolchains are unknown to it.
 */
class InitToolchainsPythonTest {
  @Test
  void uvPoetryAndPipAreHandledAndPdmIsNot() {
    assertThat(InitToolchains.unknown(python(BuildTool.UV))).isFalse();
    assertThat(InitToolchains.unknown(python(BuildTool.POETRY))).isFalse();
    assertThat(InitToolchains.unknown(python(BuildTool.PIP))).isFalse();
    assertThat(InitToolchains.unknown(python(BuildTool.PDM))).isTrue();
    assertThat(InitToolchains.unknown(python(BuildTool.HATCH))).isTrue();
  }

  @Test
  void theUnsupportedMessageNamesPythonsSupportedManagers() {
    assertThat(InitToolchains.unsupported(python(BuildTool.PDM)))
        .contains("PDM projects are not supported yet")
        .contains("Python (uv, Poetry, pip)");
  }

  private static Project python(final BuildTool manager) {
    return new Project(Path.of("/shop"), Language.PYTHON, manager, Framework.NONE);
  }
}
