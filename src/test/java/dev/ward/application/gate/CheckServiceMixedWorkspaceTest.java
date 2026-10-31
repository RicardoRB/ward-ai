package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.gate.CheckReport;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.infrastructure.config.TomlConfigurationReader;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import dev.ward.infrastructure.project.NodeWorkspaceMembers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A Turborepo root runs its own gates through turbo and each Maven member's gates in the member's
 * directory, and an edit runs only the gates of the project that holds it.
 */
class CheckServiceMixedWorkspaceTest {
  private static final String ROOT_CONFIGURATION =
      """
      version = 2
      [checks.lint]
      uses = "eslint"
      profiles = ["default", "edit"]
      command = "turbo run lint"
      """;
  private static final String API_CONFIGURATION =
      """
      version = 2
      [checks.checkstyle]
      uses = "checkstyle"
      profiles = ["default", "edit"]
      command = "mvn checkstyle:check"
      """;
  private static final String INGEST_CONFIGURATION =
      """
      version = 2
      [checks.ruff]
      uses = "ruff"
      profiles = ["default", "edit"]
      command = "uv run ruff check ."
      """;
  private static final List<String> TURBO_LINT = List.of("turbo", "run", "lint");
  private static final List<String> CHECKSTYLE = List.of("mvn", "checkstyle:check");
  private static final List<String> RUFF = List.of("uv", "run", "ruff", "check", ".");
  private static final String API_FILE = "apps/api/src/main/java/dev/codeja/Api.java";
  private static final String WEB_FILE = "apps/web/app/page.ts";
  private static final String INGEST_FILE = "services/ingest/app/main.py";

  @TempDir Path dir;

  private FakeCommandExecutor executor;

  @BeforeEach
  void project() throws IOException {
    Files.createDirectories(dir.resolve(".git"));
    write("package.json", "{\"name\": \"repo\", \"workspaces\": [\"apps/*\", \"services/*\"]}");
    write("turbo.json", "{\"tasks\": {}}");
    write("ward.toml", ROOT_CONFIGURATION);
    write("apps/web/package.json", "{\"name\": \"web\"}");
    write(WEB_FILE, "export {};\n");
    write("apps/api/package.json", "{\"name\": \"api\"}");
    write("apps/api/pom.xml", "<project><artifactId>api</artifactId></project>");
    write("apps/api/ward.toml", API_CONFIGURATION);
    write(API_FILE, "class Api {}\n");
    write("services/ingest/package.json", "{\"name\": \"ingest\"}");
    write("services/ingest/pyproject.toml", "[project]\nname = \"ingest\"\n");
    write("services/ingest/ward.toml", INGEST_CONFIGURATION);
    write(INGEST_FILE, "def ingest() -> None:\n    pass\n");
    executor = new FakeCommandExecutor(0, "", "");
  }

  @Test
  void fullCheckRunsEachMemberGateInItsOwnDirectoryAndNamesItsProject() {
    final CheckReport report = check();

    assertThat(executor.commands).containsExactly(TURBO_LINT, CHECKSTYLE, RUFF);
    assertThat(executor.workingDirectories)
        .containsExactly(dir, dir.resolve("apps/api"), dir.resolve("services/ingest"));
    assertThat(report.results())
        .extracting(GateResult::name, GateResult::status)
        .containsExactly(
            tuple("lint", GateStatus.PASSED),
            tuple("apps/api: checkstyle", GateStatus.PASSED),
            tuple("services/ingest: ruff", GateStatus.PASSED));
  }

  @Test
  void editInMavenMemberRunsOnlyThatMembersGates() {
    checkPartial(API_FILE);

    assertThat(executor.commands).containsExactly(CHECKSTYLE);
    assertThat(executor.workingDirectories).containsExactly(dir.resolve("apps/api"));
  }

  @Test
  void editInPythonMemberRunsOnlyThatMembersGates() {
    checkPartial(INGEST_FILE);

    assertThat(executor.commands).containsExactly(RUFF);
    assertThat(executor.workingDirectories).containsExactly(dir.resolve("services/ingest"));
  }

  @Test
  void editInNodeMemberRunsOnlyTheRootGates() {
    checkPartial(WEB_FILE);

    assertThat(executor.commands).containsExactly(TURBO_LINT);
  }

  private CheckReport check() {
    return service().check(dir, List.of(), CheckProgressListener.NO_OP, false, Set.of());
  }

  private void checkPartial(final String path) {
    service()
        .checkPartial(
            dir,
            List.of(dir.resolve(path)),
            new CheckRunOptions(List.of(), CheckProgressListener.NO_OP, false, Set.of()));
  }

  private CheckService service() {
    return new CheckService(
        new ProjectDetector(new FileSystemProjectInspection()),
        new TomlConfigurationReader(),
        new QualityGateFactory(executor),
        new QualityGateRunner(),
        new PartialPorts(Files::exists, new NodeWorkspaceMembers()));
  }

  private void write(final String relative, final String content) throws IOException {
    final Path file = dir.resolve(relative);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }
}
