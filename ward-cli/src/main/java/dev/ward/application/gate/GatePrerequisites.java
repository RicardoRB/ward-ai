package dev.ward.application.gate;

import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.GatePrerequisite;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.init.CompatibilityInspection;
import dev.ward.domain.init.GateToolInstallCatalog;
import dev.ward.domain.init.ToolchainSnapshot;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Project;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;
import javax.inject.Named;

/**
 * Explains gates that could not run in this environment: a missing executable, or a JVM build that
 * targets a newer release than the runtime JDK.
 */
public final class GatePrerequisites {
  private static final Logger LOGGER = Logger.getLogger(GatePrerequisites.class.getName());
  private static final ToolchainSnapshot UNKNOWN_TOOLCHAIN =
      new ToolchainSnapshot(Optional.empty(), Optional.empty(), Optional.empty(), Framework.NONE);

  private final Map<BuildTool, CompatibilityInspection> inspections;

  /** Creates the collaborator that explains missing executables only. */
  public GatePrerequisites() {
    this(Map.of());
  }

  /** Creates the collaborator; the inspections read the runtime JDK of each JVM build tool. */
  @Inject
  public GatePrerequisites(
      final CompatibilityInspection maven, @Named("gradle") final CompatibilityInspection gradle) {
    this(Map.of(BuildTool.MAVEN, maven, BuildTool.GRADLE, gradle));
  }

  private GatePrerequisites(final Map<BuildTool, CompatibilityInspection> inspections) {
    this.inspections = inspections;
  }

  /** Starts the checks for one run, which reads the runtime JDK at most once. */
  public RunPrerequisites start() {
    return new RunPrerequisites();
  }

  /** The prerequisite checks of one run. */
  public final class RunPrerequisites {
    private boolean inspected;
    private ToolchainSnapshot toolchain = UNKNOWN_TOOLCHAIN;

    /**
     * Returns {@code result}, unavailable with a prerequisite when the environment is the cause.
     */
    public GateResult attach(final Project project, final GateResult result) {
      final GateResult attached = explain(project, result);
      if (attached.prerequisite() != null) {
        final GatePrerequisite prerequisite = attached.prerequisite();
        LOGGER.log(
            Level.INFO,
            () ->
                "event=gate-prerequisite gate="
                    + attached.name()
                    + " kind="
                    + prerequisite.kind().name().toLowerCase(Locale.ROOT));
      }
      return attached;
    }

    private GateResult explain(final Project project, final GateResult result) {
      if (result.status() == GateStatus.UNAVAILABLE && result.prerequisite() == null) {
        return withExecutable(result);
      }
      if (result.status() == GateStatus.FAILED && runsBuild(project, result)) {
        return withRuntime(project, result);
      }
      return result;
    }

    private GateResult withExecutable(final GateResult result) {
      if (result.command().isEmpty()) {
        return result;
      }
      final String program = result.command().getFirst();
      final String executable =
          program.substring(Math.max(program.lastIndexOf('/'), program.lastIndexOf('\\')) + 1);
      final String hint = GateToolInstallCatalog.installHint(executable);
      final String remediation =
          hint == null
              ? "Install " + executable + " or add it to PATH."
              : "Install " + executable + ": " + hint;
      final String summary =
          "Gate '" + result.name() + "' could not start '" + executable + "'. " + remediation;
      return unavailable(result, summary)
          .withPrerequisite(
              new GatePrerequisite(
                  GatePrerequisite.Kind.EXECUTABLE, executable, null, null, remediation));
    }

    private GateResult withRuntime(final Project project, final GateResult result) {
      final ToolchainSnapshot runtime = runtime(project);
      if (runtime.declaredRelease().isEmpty() || runtime.runtimeJdk().isEmpty()) {
        return result;
      }
      final int required = runtime.declaredRelease().get();
      final int detected = runtime.runtimeJdk().get();
      if (required <= detected) {
        return result;
      }
      final String remediation =
          "Run the build on JDK "
              + required
              + " or newer, for example by setting JAVA_HOME or with actions/setup-java.";
      final String summary =
          "Gate '"
              + result.name()
              + "' needs Java "
              + required
              + " but the build runs on Java "
              + detected
              + ". "
              + remediation;
      return unavailable(result, summary)
          .withPrerequisite(
              new GatePrerequisite(
                  GatePrerequisite.Kind.RUNTIME,
                  null,
                  String.valueOf(required),
                  String.valueOf(detected),
                  remediation));
    }

    private static boolean runsBuild(final Project project, final GateResult result) {
      return !result.command().isEmpty()
          && result.command().getFirst().equals(project.buildExecutable());
    }

    private ToolchainSnapshot runtime(final Project project) {
      if (!inspected) {
        inspected = true;
        toolchain = inspect(project).orElse(UNKNOWN_TOOLCHAIN);
      }
      return toolchain;
    }

    private Optional<ToolchainSnapshot> inspect(final Project project) {
      final CompatibilityInspection inspection = inspections.get(project.buildTool());
      if (inspection == null) {
        return Optional.empty();
      }
      try {
        return Optional.of(inspection.inspect(project).toolchain());
      } catch (WardException exception) {
        LOGGER.log(
            Level.FINE,
            () ->
                "event=gate-prerequisite-inspection-failed error="
                    + exception.getClass().getSimpleName());
        return Optional.empty();
      }
    }
  }

  private static GateResult unavailable(final GateResult result, final String summary) {
    return GateResult.builder(result.name(), GateStatus.UNAVAILABLE)
        .command(result.command())
        .exitCode(result.exitCode())
        .duration(result.duration())
        .stdout(result.stdout())
        .stderr(result.stderr())
        .summary(summary)
        .errors(result.errors())
        .warnings(result.warnings())
        .build();
  }
}
