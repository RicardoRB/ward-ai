package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.JavaPresetGates;
import dev.ward.application.init.InitService;
import dev.ward.application.init.InitSetupCatalog;
import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.infrastructure.init.InitFixtures;
import dev.ward.infrastructure.init.QualityHookInstaller;
import dev.ward.infrastructure.init.RuleFileTargetsFixtures;
import dev.ward.infrastructure.init.maven.PomToolConfigurator;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class InitWizardLineModeTest {
  // ward-format adds a [checks.<id>.paths] table.
  private static final int STANDARD_PARTIAL_TABLES = 1;

  /** The ward gate and its layer table, which every configuration writes. */
  private static final int WARD_TABLE_SECTIONS = 3;

  @TempDir Path root;

  private final StringWriter output = new StringWriter();

  @BeforeEach
  void createProject() throws IOException {
    Files.writeString(
        root.resolve("pom.xml"),
        "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId>"
            + "<artifactId>demo</artifactId><version>1.0.0</version></project>");
    Files.createDirectories(root.resolve(".git/hooks"));
  }

  @Test
  void terminalWizardAsksOptionalQuestionsBeforeWriting() throws IOException {
    final int exit =
        execute(new TerminalLikeInputStream("3\n1\n1\ny\n"), "--directory", root.toString());

    assertThat(exit).as(output.toString()).isZero();
    assertThat(output.toString()).contains("Install Ward pre-commit and pre-push quality hooks");
    assertThat(Files.readString(root.resolve(".git/hooks/pre-commit")))
        .contains(QualityHookInstaller.START_MARKER);
  }

  @Test
  void endOfInputAfterGatesSelectsOptionalDefaults() throws IOException {
    final List<String> events = new ArrayList<>();
    final Logger logger = Logger.getLogger(LinePrompt.class.getName());
    final Handler handler =
        new Handler() {
          @Override
          public void publish(final LogRecord logRecord) {
            events.add(logRecord.getMessage());
          }

          @Override
          public void flush() {
            // Nothing is buffered.
          }

          @Override
          public void close() {
            // Nothing to release.
          }
        };
    logger.addHandler(handler);
    final int exit;
    try {
      exit = execute(new TerminalLikeInputStream("3\n1\n1\n"), "--directory", root.toString());
    } finally {
      logger.removeHandler(handler);
    }

    assertThat(exit).as(output.toString()).isZero();
    final String configuration = Files.readString(root.resolve("ward.toml"));
    assertThat(configuration).contains("[checks.tests]");
    assertThat(root.resolve(".git/hooks/pre-commit")).doesNotExist();
    assertThat(root.resolve(".git/hooks/pre-push")).doesNotExist();
    assertThat(events)
        .containsExactly("event=init-optional-default question=quality-hooks reason=end-of-input");
  }

  @Test
  void numberOfPresetGateRemovesItFromStandard() throws IOException {
    final int exit =
        execute(new TerminalLikeInputStream("1\n1\n3\n"), "--directory", root.toString());

    assertThat(exit).as(output.toString()).isZero();
    assertThat(output.toString()).contains("> [x] 3) coverage", "or press Enter to accept");
    final String configuration = Files.readString(root.resolve("ward.toml"));
    assertThat(configuration)
        .contains("version = 2", "[checks.tests]")
        .doesNotContain("[checks.coverage]");
  }

  @Test
  void blankGateAnswerAcceptsStandardGates() throws IOException {
    final int exit =
        execute(new TerminalLikeInputStream("1\n1\n\n"), "--directory", root.toString());

    assertThat(exit).as(output.toString()).isZero();
    final String configuration = Files.readString(root.resolve("ward.toml"));
    for (final String gate : JavaPresetGates.STANDARD) {
      assertThat(configuration).contains("[checks." + gate + "]");
    }
    assertThat(configuration.split("\\[checks\\.", -1))
        .hasSize(
            JavaPresetGates.STANDARD.size() + 1 + STANDARD_PARTIAL_TABLES + WARD_TABLE_SECTIONS);
  }

  @Test
  void presetOptionKeepsLayeredArchitectureWithoutPrompting() throws IOException {
    final int exit =
        execute(
            new TerminalLikeInputStream(""),
            "--directory",
            root.toString(),
            "--integration",
            "none",
            "--preset",
            "strict");

    assertThat(exit).as(output.toString()).isZero();
    assertThat(output.toString()).doesNotContain("Select an architecture style");
    assertThat(Files.readString(root.resolve("ward.toml")))
        .contains("[checks.enforcer]", "[checks.mutation]", "[[checks.ward.layers]]");
    assertThat(output.toString()).doesNotContain("Select an architecture style");
  }

  private int execute(final InputStream input, final String... args) {
    final InitService service =
        new InitService(
            new ProjectDetector(new FileSystemProjectInspection()),
            Set.of(),
            new InitSetupCatalog(new FakeEnvironmentInspection(), new QualityHookInstaller()),
            InitFixtures.projectFiles(new PomToolConfigurator(), RuleFileTargetsFixtures.writer()));
    final CommandLine command = new CommandLine(new InitCommand(service, input));
    command.setOut(new PrintWriter(output, true));
    command.setErr(new PrintWriter(output, true));
    return command.execute(args);
  }
}
