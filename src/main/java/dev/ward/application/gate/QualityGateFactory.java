package dev.ward.application.gate;

import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.config.WardException;
import dev.ward.domain.doctor.EnvironmentInspection;
import dev.ward.domain.gate.MutationReport;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.gate.ReportDirectories;
import dev.ward.domain.gate.SkippedQualityGate;
import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Project;
import dev.ward.domain.traceability.ScenarioInventory;
import dev.ward.domain.traceability.TestMappingInventory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;

/** Resolves the typed built-in gate registry in stable configuration order. */
public class QualityGateFactory {
  private static final String ZAP = "zap";

  public static final List<String> SUPPORTED_GATES = SupportedQualityGates.IDS;
  private static final Set<String> MUTATION_GATES =
      Set.of(SupportedQualityGates.STRYKER, SupportedQualityGates.MUTATION);
  private static final String STRYKER_REPORT_HINT =
      "Enable Stryker's json reporter in the command, for example with"
          + " --reporters clear-text,progress,json.";
  private static final String PIT_REPORT_HINT =
      "Enable PIT's XML output, for example with -DoutputFormats=XML,HTML for Maven or"
          + " outputFormats = ['XML', 'HTML'] in the Gradle pitest block.";

  private final CommandExecutor executor;
  private final EnvironmentInspection environment;
  private final ScenarioInventory scenarioInventory;
  private final TestMappingInventory testMappingInventory;
  private final ReportDirectories reportDirectories;
  private final OpenApiGates openApiGates;
  private final MutationReport strykerReport;
  private final MutationReport pitReport;

  /** Creates a factory for command gates only. */
  public QualityGateFactory(final CommandExecutor executor) {
    this(executor, null, null, null);
  }

  /** Creates a factory that can check executable availability. */
  public QualityGateFactory(
      final CommandExecutor executor, final EnvironmentInspection environment) {
    this(executor, environment, null, null);
  }

  /** Creates a factory that can also build the traceability gate. */
  public QualityGateFactory(
      final CommandExecutor executor,
      final EnvironmentInspection environment,
      final ScenarioInventory scenarioInventory,
      final TestMappingInventory testMappingInventory) {
    this(executor, environment, scenarioInventory, testMappingInventory, ReportDirectories.NONE);
  }

  /**
   * Creates a factory without OpenAPI gates. {@code reportDirectories} tells command gates where
   * their tools write reports.
   */
  public QualityGateFactory(
      final CommandExecutor executor,
      final EnvironmentInspection environment,
      final ScenarioInventory scenarioInventory,
      final TestMappingInventory testMappingInventory,
      final ReportDirectories reportDirectories) {
    this(
        executor,
        environment,
        new TraceabilitySources(scenarioInventory, testMappingInventory),
        new GateReportPorts(reportDirectories, null, null),
        null);
  }

  /**
   * Creates the factory. {@code reports} tells command gates where their tools write reports and
   * reads mutation reports, and {@code openApiGates} builds the OpenAPI gates.
   */
  @Inject
  public QualityGateFactory(
      final CommandExecutor executor,
      final EnvironmentInspection environment,
      final TraceabilitySources traceability,
      final GateReportPorts reports,
      final OpenApiGates openApiGates) {
    this.reportDirectories = reports.directories();
    this.strykerReport = reports.stryker();
    this.pitReport = reports.pit();
    this.executor = executor;
    this.environment = environment;
    this.scenarioInventory = traceability.scenarios();
    this.testMappingInventory = traceability.mappings();
    this.openApiGates = openApiGates;
  }

  /** Creates one gate per configured ID, without project-specific commands. */
  public List<QualityGate> create(final WardConfiguration configuration) {
    return create(configuration, null);
  }

  /**
   * Creates one gate per configured ID; disabled gates become skipped gates.
   *
   * @throws WardException if a gate ID is unknown
   */
  public List<QualityGate> create(final WardConfiguration configuration, final Project project) {
    final List<QualityGate> gates = new ArrayList<>();
    configuration
        .gates()
        .keySet()
        .forEach(
            id -> {
              if (!SUPPORTED_GATES.contains(id) && !SupportedQualityGates.LEGACY_IDS.contains(id)) {
                throw new WardException(
                    "Quality gate '" + id + "' is unknown. Supported gates: " + SUPPORTED_GATES);
              }
            });
    for (final Map.Entry<String, GateConfiguration> entry : configuration.gates().entrySet()) {
      final String id = entry.getKey();
      if (!entry.getValue().enabled()) {
        gates.add(new SkippedQualityGate(id));
        continue;
      }
      gates.add(createGate(id, entry.getValue(), project));
    }
    return gates;
  }

  private QualityGate createGate(
      final String id, final GateConfiguration configuration, final Project project) {
    if (OpenSpecQualityGate.ACTIVE.equals(id) || OpenSpecQualityGate.ARCHIVED.equals(id)) {
      return environment == null
          ? new CommandQualityGate(id, executor, configuration.command())
          : new OpenSpecQualityGate(
              id, executor, environment, configuration.command(), configuration.root());
    }
    if (MavenTestGate.NAME.equals(id)) {
      return new MavenTestGate(executor, testsCommand(configuration.command(), project));
    }
    if (OpenSpecJunitTraceabilityGate.NAME.equals(id)) {
      return traceabilityGate();
    }
    if (ZAP.equals(id)) {
      ZapTargetValidation.require(id, configuration.command());
    }
    if (SupportedQualityGates.openApiIds().contains(id)) {
      return openApiGate(id, configuration, project);
    }
    if (MUTATION_GATES.contains(id)) {
      return mutationGate(id, configuration);
    }
    return new CommandQualityGate(id, executor, configuration.command(), reportDirectories);
  }

  /** Builds a Stryker or PIT gate that checks its {@code minimum} against the tool's report. */
  private QualityGate mutationGate(final String id, final GateConfiguration configuration) {
    final QualityGate command =
        new CommandQualityGate(id, executor, configuration.command(), reportDirectories);
    if (configuration.minimum() == null) {
      return command;
    }
    final boolean stryker = SupportedQualityGates.STRYKER.equals(id);
    final MutationReport report = stryker ? strykerReport : pitReport;
    if (report == null) {
      throw new WardException("The '" + id + "' gate cannot check its minimum here.");
    }
    return new MutationScoreGate(
        command, report, configuration.minimum(), stryker ? STRYKER_REPORT_HINT : PIT_REPORT_HINT);
  }

  private QualityGate openApiGate(
      final String id, final GateConfiguration configuration, final Project project) {
    if (openApiGates == null) {
      OpenApiCommandValidation.require(id, configuration.command());
      throw new WardException("OpenAPI gate dependencies are unavailable.");
    }
    return openApiGates.create(id, configuration, project);
  }

  private QualityGate traceabilityGate() {
    if (scenarioInventory == null || testMappingInventory == null) {
      throw new WardException("Traceability gate dependencies are unavailable.");
    }
    return new OpenSpecJunitTraceabilityGate(scenarioInventory, testMappingInventory);
  }

  private static List<String> testsCommand(final List<String> configured, final Project project) {
    if (project != null
        && !project.wrapperAvailable()
        && !configured.isEmpty()
        && ("./mvnw".equals(configured.getFirst())
            || "mvnw".equals(configured.getFirst())
            || "./gradlew".equals(configured.getFirst())
            || "gradlew".equals(configured.getFirst()))) {
      final List<String> fallback = new ArrayList<>(configured);
      fallback.set(0, project.buildTool() == BuildTool.GRADLE ? "gradle" : "mvn");
      return fallback;
    }
    return configured;
  }
}
