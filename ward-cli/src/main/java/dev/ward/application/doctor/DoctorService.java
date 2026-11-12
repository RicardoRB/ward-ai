package dev.ward.application.doctor;

import dev.ward.application.gate.AnalysisRuleSource;
import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.agent.AgentIntegration;
import dev.ward.domain.agent.IntegrationCapability;
import dev.ward.domain.agent.IntegrationHealth;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.config.WardConfigurationReader;
import dev.ward.domain.config.WardException;
import dev.ward.domain.doctor.EnvironmentFacts;
import dev.ward.domain.doctor.EnvironmentInspection;
import dev.ward.domain.init.BuildToolConfiguration;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.swift.SwiftToolchainInspection;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;

/** Diagnoses whether a project and its environment are ready for Ward. */
public final class DoctorService {
  private final ProjectDetector detector;
  private final EnvironmentInspection environment;
  private final WardConfigurationReader configurationReader;
  private final Set<AgentIntegration> integrations;
  private final Optional<BuildToolConfiguration> buildTools;
  private final Optional<SwiftToolchainInspection> swift;
  private final AnalysisRuleSource rules;

  /** How serious a diagnostic finding is. */
  public enum Status {
    OK,
    WARNING,
    ERROR
  }

  /**
   * One diagnostic result.
   *
   * @param name what was checked
   * @param status how serious the result is
   * @param message what was found and how to fix it
   */
  public record Finding(String name, Status status, String message) {}

  /** Creates the service; {@code integrations} are checked for installation health. */
  public DoctorService(
      final ProjectDetector detector,
      final EnvironmentInspection environment,
      final WardConfigurationReader configurationReader,
      final Set<AgentIntegration> integrations) {
    this(
        detector,
        environment,
        new DoctorPorts(configurationReader, null, null, null),
        integrations);
  }

  /**
   * Creates the service that Dagger injects; {@code ports} also read the project's runtime JDK, the
   * tool versions its build file pins, and the Xcode version.
   */
  @Inject
  public DoctorService(
      final ProjectDetector detector,
      final EnvironmentInspection environment,
      final DoctorPorts ports,
      final Set<AgentIntegration> integrations) {
    this.detector = detector;
    this.environment = environment;
    this.configurationReader = ports.configurationReader();
    this.integrations = Set.copyOf(integrations);
    this.buildTools = Optional.ofNullable(ports.buildTools());
    this.swift = Optional.ofNullable(ports.swift());
    this.rules = ports.rules();
  }

  /** Creates a service that checks only the project and environment. */
  public DoctorService(final ProjectDetector detector, final EnvironmentInspection environment) {
    this(detector, environment, (WardConfigurationReader) null, Set.of());
  }

  /** Creates a service that also checks {@code ward.toml}. */
  public DoctorService(
      final ProjectDetector detector,
      final EnvironmentInspection environment,
      final WardConfigurationReader configurationReader) {
    this(detector, environment, configurationReader, Set.of());
  }

  /** Returns the diagnostic findings for the project containing {@code start}. */
  public List<Finding> diagnose(final Path start) {
    final List<Finding> findings = new ArrayList<>();
    final Optional<Project> project = detector.detect(start);
    if (project.isEmpty()) {
      findings.add(new Finding("project", Status.ERROR, "No supported project detected."));
      return findings;
    }
    final Path root = project.get().root();
    final EnvironmentFacts facts = environment.inspect(root);
    final Project detected = project.get();
    final boolean openCode = flag(facts, "openCodeCommandPresent");
    findings.add(new Finding("project", Status.OK, root.toString()));
    findings.addAll(
        BuildToolFindings.forToolchain(detected.toolchain())
            .findings(
                detected,
                facts,
                ToolchainContext.read(
                    buildTools,
                    swift,
                    configurationReader,
                    root,
                    flag(facts, "configurationPresent"))));
    findings.add(
        check(
            "configuration",
            flag(facts, "configurationPresent"),
            Status.ERROR,
            "Configuration is present.",
            "Run 'ward init'."));
    diagnoseIntegrations(project.get(), openCode, findings);
    findings.add(
        check(
            "native",
            flag(facts, "nativeExecutablePresent"),
            Status.WARNING,
            "Native executable present.",
            "Build with the native profile."));
    diagnoseConfiguration(root, facts, findings);
    CheckTypeFindings.diagnose(configurationReader, root, facts, findings);
    if (flag(facts, "configurationPresent")) {
      RuleConfigurationFindings.diagnose(configurationReader, rules, root, findings);
    }
    return findings;
  }

  private void diagnoseIntegrations(
      final Project project, final boolean legacyOpenCode, final List<Finding> findings) {
    final List<AgentIntegration> configured =
        integrations.stream()
            .filter(
                adapter -> health(project, adapter).status() != IntegrationHealth.Status.MISSING)
            .toList();
    if (configured.isEmpty()) {
      findings.add(
          new Finding(
              "integration",
              legacyOpenCode ? Status.OK : Status.WARNING,
              legacyOpenCode
                  ? "OpenCode integration present."
                  : "No supported agent integration configured."));
      return;
    }
    final String summary =
        configured.stream()
            .map(
                adapter ->
                    adapter.id()
                        + "="
                        + health(project, adapter).status().name().toLowerCase(Locale.ROOT)
                        + " ["
                        + adapter.capabilities().stream()
                            .map(IntegrationCapability::name)
                            .sorted()
                            .toList()
                        + "]")
            .sorted()
            .reduce((left, right) -> left + ", " + right)
            .orElse("none");
    final boolean healthy =
        configured.stream()
            .allMatch(
                adapter -> health(project, adapter).status() == IntegrationHealth.Status.INSTALLED);
    findings.add(
        new Finding(
            "integration",
            healthy ? Status.OK : Status.WARNING,
            "Configured integrations: "
                + summary
                + (healthy ? "." : "; re-run integrate to repair.")));
  }

  /** Nested projects find integrations that install at the repository root there. */
  private static IntegrationHealth health(final Project project, final AgentIntegration adapter) {
    final boolean shared = adapter.installsAtRepositoryRoot();
    return adapter.health(project.installRoot(shared), project.installPath(shared));
  }

  private void diagnoseConfiguration(
      final Path root, final EnvironmentFacts facts, final List<Finding> findings) {
    if (configurationReader == null || !flag(facts, "configurationPresent")) {
      return;
    }
    try {
      configurationReader.read(root.resolve(WardConfiguration.FILE_NAME));
    } catch (WardException e) {
      findings.add(
          new Finding(
              "configuration",
              Status.ERROR,
              "Configuration could not be read. Fix ward.toml and run doctor again."));
    }
  }

  static boolean flag(final EnvironmentFacts facts, final String name) {
    return Boolean.parseBoolean(facts.values().get(name));
  }

  private static Finding check(
      final String name,
      final boolean passed,
      final Status failure,
      final String passedMessage,
      final String failedMessage) {
    return passed
        ? new Finding(name, Status.OK, passedMessage)
        : new Finding(name, failure, failedMessage);
  }
}
