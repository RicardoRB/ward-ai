package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.BuildFileChange;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The ward group is written, installed with the manager, and restored when the install fails. */
class PythonBuildToolConfiguratorTest {
  private static final String PYPROJECT =
      "[project]\nname = \"shop\"\nversion = \"0.1.0\"\nrequires-python = \">=3.11\"\n";
  private static final String PYPROJECT_FILE = "pyproject.toml";
  private static final String LOCK = "version = 1\n";
  private static final List<InitGateOption> GATES =
      List.of(new InitGateOption("ruff", List.of(), true, ""));

  @TempDir Path root;

  @Test
  void failedInstallRestoresPyprojectAndLockByteForByte() throws IOException {
    Files.writeString(root.resolve(PYPROJECT_FILE), PYPROJECT);
    Files.writeString(root.resolve("uv.lock"), LOCK);
    final FakeCommandExecutor executor = new FakeCommandExecutor(1, "", "");

    assertThatThrownBy(() -> new PythonBuildToolConfigurator(executor).apply(uv(), GATES))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("failed with exit code 1");

    assertThat(Files.readString(root.resolve(PYPROJECT_FILE))).isEqualTo(PYPROJECT);
    assertThat(Files.readString(root.resolve("uv.lock"))).isEqualTo(LOCK);
  }

  @Test
  void successfulInstallWritesGroupAndSyncsWithIt() throws IOException {
    Files.writeString(root.resolve(PYPROJECT_FILE), PYPROJECT);
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");

    final BuildFileChange change = new PythonBuildToolConfigurator(executor).apply(uv(), GATES);

    assertThat(change.changed()).isTrue();
    assertThat(Files.readString(root.resolve(PYPROJECT_FILE))).contains("ward = [", "ruff==0.17.1");
    assertThat(executor.commands).containsExactly(List.of("uv", "sync", "--group", "ward"));
  }

  @Test
  void poetryBelowTwoTwoIsRefusedBeforeAnyWrite() throws IOException {
    Files.writeString(root.resolve(PYPROJECT_FILE), PYPROJECT);
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "Poetry (version 2.1.0)", "");
    final Project poetry = new Project(root, Language.PYTHON, BuildTool.POETRY, Framework.NONE);

    assertThatThrownBy(() -> new PythonBuildToolConfigurator(executor).apply(poetry, GATES))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("Poetry 2.1 is too old");
    assertThat(Files.readString(root.resolve(PYPROJECT_FILE))).isEqualTo(PYPROJECT);
  }

  @Test
  void requirementsOnlyProjectGetsRequirementsWardFileAndPipInstall() throws IOException {
    Files.writeString(root.resolve("requirements.txt"), "django==5.2.8\n");
    Files.createDirectories(root.resolve(".venv"));
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");
    final Project pip = new Project(root, Language.PYTHON, BuildTool.PIP, Framework.NONE);

    final BuildFileChange change = new PythonBuildToolConfigurator(executor).apply(pip, GATES);

    assertThat(Files.readString(root.resolve("requirements-ward.txt")))
        .contains("# managed-by: ward", "ruff==0.17.1");
    assertThat(executor.commands)
        .containsExactly(
            List.of(".venv/bin/python", "-m", "pip", "install", "-r", "requirements-ward.txt"));
    new PythonBuildToolConfigurator(executor).rollback(change);
    assertThat(root.resolve("requirements-ward.txt")).doesNotExist();
  }

  @Test
  void pipBelowTwentyFivePointOneIsRefusedBeforeAnyWrite() throws IOException {
    Files.writeString(root.resolve(PYPROJECT_FILE), PYPROJECT);
    Files.createDirectories(root.resolve(".venv"));
    final FakeCommandExecutor executor =
        new FakeCommandExecutor(0, "pip 24.0 from /shop/.venv/lib/pip (python 3.12)", "");
    final Project pip = new Project(root, Language.PYTHON, BuildTool.PIP, Framework.NONE);

    assertThatThrownBy(() -> new PythonBuildToolConfigurator(executor).apply(pip, GATES))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("pip 24.0 is too old");
    assertThat(Files.readString(root.resolve(PYPROJECT_FILE))).isEqualTo(PYPROJECT);
  }

  private Project uv() {
    return new Project(root, Language.PYTHON, BuildTool.UV, Framework.NONE);
  }
}
