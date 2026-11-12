package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.init.InitPlan;
import dev.ward.domain.init.InitSelection;
import dev.ward.domain.init.ToolInstallationChange;
import dev.ward.domain.init.ToolInstallationResult;
import dev.ward.domain.init.ToolInstaller;
import dev.ward.infrastructure.init.ArchitectureTablesFixtures;
import dev.ward.infrastructure.init.FileConfigurationStorage;
import dev.ward.infrastructure.init.OwnedSectionWriter;
import dev.ward.infrastructure.init.PresetRuleFileWriter;
import dev.ward.infrastructure.init.RuleFileTargetsFixtures;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InitToolLifecycleTest {
  @Test
  @Disabled(
      "Init offers no gate whose tool it can install since the security rules replaced semgrep; the decision on these tests is open")
  void selectedGateWithoutConsentDoesNotInvokeInstaller(@TempDir final Path root) throws Exception {
    PythonInitFixtures.project(root);
    final RecordingInstaller installer = new RecordingInstaller(true);

    service(installer).initialize(root, selection(List.of()));

    assertThat(installer.installCalled).isFalse();
  }

  @Test
  @Disabled(
      "Init offers no gate whose tool it can install since the security rules replaced semgrep; the decision on these tests is open")
  void consentInstallsTheGateTool(@TempDir final Path root) throws Exception {
    PythonInitFixtures.project(root);
    final RecordingInstaller installer = new RecordingInstaller(true);

    service(installer).initialize(root, selection(List.of("semgrep")));

    assertThat(installer.installed).containsExactly("semgrep");
  }

  @Test
  @Disabled(
      "Init offers no gate whose tool it can install since the security rules replaced semgrep; the decision on these tests is open")
  void unavailableInstallerStillWritesTheConfiguration(@TempDir final Path root) throws Exception {
    PythonInitFixtures.project(root);
    final RecordingInstaller installer = new RecordingInstaller(false);

    service(installer).initialize(root, selection(List.of("semgrep")));

    assertThat(installer.installCalled).isTrue();
    assertThat(Files.readString(root.resolve("ward.toml"))).contains("[checks.semgrep]");
  }

  @Test
  @Disabled(
      "Init offers no gate whose tool it can install since the security rules replaced semgrep; the decision on these tests is open")
  void aMissingGateExecutableIsDescribedWithoutRunningAnInstaller(@TempDir final Path root)
      throws Exception {
    PythonInitFixtures.project(root);
    final RecordingInstaller installer = new RecordingInstaller(true);

    final InitPlan plan = service(installer).plan(root, selection(List.of()), false);

    assertThat(plan.gateInstallations()).extracting("tool").containsExactly("semgrep");
    assertThat(installer.installCalled).isFalse();
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
            PythonInitFixtures.configurator(),
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
