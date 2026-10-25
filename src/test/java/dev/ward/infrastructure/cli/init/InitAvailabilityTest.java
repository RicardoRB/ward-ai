package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.init.InitSetupCatalog;
import dev.ward.application.init.JdkCatalogs;
import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.init.BuildFileChange;
import dev.ward.domain.init.BuildToolConfiguration;
import dev.ward.domain.init.GateOmission;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.init.RawTerminal.RawSession;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.cli.ExitCodes;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unavailable gates are marked before selection and reported with their remediation. */
class InitAvailabilityTest {
  @TempDir Path root;

  @Test
  void numberedGateListMarksUnavailableGates() throws IOException {
    final InitCommandFixture init = new InitCommandFixture(root);

    init.run("3\n1\n1\n");

    assertThat(init.output()).contains("semgrep - ", "[unavailable]").doesNotContain("[available]");
  }

  @Test
  void rawGateMenuMarksUnavailableGates() {
    final StringWriter output = new StringWriter();
    final InitWizard wizard =
        new InitWizard(
            new LinePrompt(new BufferedReader(new StringReader(""))),
            () -> Optional.of(new ScriptedSession(' ', '\n')),
            new PrintWriter(output, true),
            true);

    final List<String> selected =
        wizard.selectGates(
            new InitSetupCatalog(new FakeEnvironmentInspection()),
            new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE),
            List.of());

    assertThat(selected).containsExactly("tests");
    assertThat(output.toString()).contains("semgrep - ").contains("[unavailable]");
  }

  @Test
  void interactiveGateListOnAnOlderJdkShowsTheGateUnavailableAndUnselectable() {
    final StringWriter output = new StringWriter();
    final InitWizard wizard =
        new InitWizard(
            new LinePrompt(new BufferedReader(new StringReader("\n"))),
            Optional::empty,
            new PrintWriter(output, true),
            false);
    final InitSetupCatalog catalog = JdkCatalogs.reading(new FormatNeedsJdk11());

    final List<String> selected =
        wizard.selectGates(
            catalog,
            new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE),
            List.of("tests", "format"));

    assertThat(output.toString())
        .contains(
            "Unavailable on this JDK: format omitted: spotless-maven-plugin needs JDK 11+;"
                + " this project builds on JDK 8")
        .doesNotContain("format - ");
    assertThat(selected).containsExactly("tests");
  }

  @Test
  void selectedUnavailableGateShowsItsRemediation() throws IOException {
    final InitCommandFixture init = new InitCommandFixture(root);
    final String remediation =
        new InitSetupCatalog(new FakeEnvironmentInspection())
            .gate(new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE), "semgrep")
            .availabilityMessage();

    final int exit = init.run("", "--integration", "none", "--gate", "semgrep");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(init.output())
        .contains("Selected quality gate 'semgrep': UNAVAILABLE - " + remediation);
  }

  @Test
  void selectingDependencyCheckAndTrivyWarnsAboutTheOverlap() throws IOException {
    final InitCommandFixture init = new InitCommandFixture(root);

    init.run(
        "", "--dry-run", "--integration", "none", "--gate", "dependency-check", "--gate", "trivy");

    assertThat(init.output()).contains("Warning: trivy already scans dependencies");
  }

  @Test
  void selectingOnlyTrivyDoesNotWarnAboutTheOverlap() throws IOException {
    final InitCommandFixture init = new InitCommandFixture(root);

    init.run("", "--dry-run", "--integration", "none", "--gate", "trivy");

    assertThat(init.output()).doesNotContain("trivy already scans dependencies");
  }

  /** A build-tool port whose runtime JDK cannot run the format gate; it writes nothing. */
  private static final class FormatNeedsJdk11 implements BuildToolConfiguration {
    @Override
    public BuildFileChange apply(final Project project, final List<InitGateOption> gates) {
      throw new UnsupportedOperationException("not written in this test");
    }

    @Override
    public PlannedFileChange plan(
        final Project project,
        final List<InitGateOption> gates,
        final QualityPreset preset,
        final boolean approved) {
      throw new UnsupportedOperationException("not planned in this test");
    }

    @Override
    public List<GateOmission> omissions(final Project project, final List<String> gateIds) {
      return gateIds.contains("format")
          ? List.of(
              new GateOmission(
                  "format", "spotless-maven-plugin needs JDK 11+; this project builds on JDK 8"))
          : List.of();
    }

    @Override
    public void rollback(final BuildFileChange change) {
      // Nothing was written.
    }
  }

  /** Replays the given keys, then reports end of input. */
  private static final class ScriptedSession implements RawSession {
    private final Iterator<Integer> keys;

    ScriptedSession(final int... keys) {
      this.keys = Arrays.stream(keys).iterator();
    }

    @Override
    public int read() {
      return keys.hasNext() ? keys.next() : -1;
    }

    @Override
    public void close() {
      // Nothing to restore for a scripted session.
    }
  }
}
