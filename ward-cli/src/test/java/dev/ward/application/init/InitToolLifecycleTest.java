package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.init.InitSelection;
import dev.ward.domain.init.ToolInstallationChange;
import dev.ward.domain.init.ToolInstallationResult;
import dev.ward.domain.init.ToolInstaller;
import dev.ward.infrastructure.init.ArchitectureTablesFixtures;
import dev.ward.infrastructure.init.FileConfigurationStorage;
import dev.ward.infrastructure.init.OwnedSectionWriter;
import dev.ward.infrastructure.init.PresetRuleFileWriter;
import dev.ward.infrastructure.init.RuleFileTargetsFixtures;
import dev.ward.infrastructure.init.python.PythonBuildToolConfigurator;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InitToolLifecycleTest {
  @Test
  void selectedGateWithoutConsentDoesNotInvokeInstaller(@TempDir final Path root) throws Exception {
    Files.createDirectories(root.resolve(".venv"));
    Files.createDirectories(root.resolve("src"));
    Files.writeString(root.resolve("src/app.py"), "x = 1\n");
    Files.writeString(
        root.resolve("pyproject.toml"), "[project]\nname = \"demo\"\nversion = \"1\"\n");
    final RecordingInstaller installer = new RecordingInstaller(true);

    service(installer).initialize(root, selection(List.of()));

    assertThat(installer.installCalled).isFalse();
  }

  @Test
  void consentInstallsTheGateTool(@TempDir final Path root) throws Exception {
    Files.createDirectories(root.resolve(".venv"));
    Files.createDirectories(root.resolve("src"));
    Files.writeString(root.resolve("src/app.py"), "x = 1\n");
    Files.writeString(
        root.resolve("pyproject.toml"), "[project]\nname = \"demo\"\nversion = \"1\"\n");
    final RecordingInstaller installer = new RecordingInstaller(true);

    service(installer).initialize(root, selection(List.of("semgrep")));

    assertThat(installer.installed).containsExactly("semgrep");
  }

  @Test
  void unavailableInstallerStillWritesTheConfiguration(@TempDir final Path root) throws Exception {
    Files.createDirectories(root.resolve(".venv"));
    Files.createDirectories(root.resolve("src"));
    Files.writeString(root.resolve("src/app.py"), "x = 1\n");
    Files.writeString(
        root.resolve("pyproject.toml"), "[project]\nname = \"demo\"\nversion = \"1\"\n");
    final RecordingInstaller installer = new RecordingInstaller(false);

    service(installer).initialize(root, selection(List.of("semgrep")));

    assertThat(installer.installCalled).isTrue();
    assertThat(Files.readString(root.resolve("ward.toml"))).contains("[checks.semgrep]");
  }

  private static InitSelection selection(final List<String> installTools) {
    return new InitSelection(
        List.of("none"), List.of("semgrep"), null, null, installTools, false, null, false, null);
  }

  private static InitService service(final ToolInstaller installer) {
    return new InitService(
        new ProjectDetector(new FileSystemProjectInspection()),
        Set.of(),
        new InitSetupCatalog(new FakeEnvironmentInspection()),
        new InitProjectFiles(
            new PythonBuildToolConfigurator(new FakeCommandExecutor(0, "", "")),
            new PresetRuleFileWriter(RuleFileTargetsFixtures.defaults()),
            ArchitectureTablesFixtures.create(),
            new FileConfigurationStorage(),
            new OwnedSectionWriter()),
        installer);
  }

  private static final class RecordingInstaller implements ToolInstaller {
    private final boolean succeeds;
    private final List<String> installed = new ArrayList<>();
    private boolean installCalled;

    private RecordingInstaller(final boolean succeeds) {
      this.succeeds = succeeds;
    }

    @Override
    public ToolInstallationResult install(final Path projectRoot, final String tool) {
      installCalled = true;
      installed.add(tool);
      if (!succeeds) {
        return new ToolInstallationResult(tool, false, null, "fake installer unavailable");
      }
      return new ToolInstallationResult(
          tool,
          true,
          Path.of(tool),
          "installed",
          new ToolInstallationChange(null, projectRoot.resolve(".ward/tools/" + tool), true));
    }
  }
}
