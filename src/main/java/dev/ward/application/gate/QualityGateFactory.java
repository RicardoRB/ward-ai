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
      Set.of(
          SupportedQualityGates.STRYKER,
          SupportedQualityGates.MUTATION,
          SupportedQualityGates.MUTMUT);
  private static final Set<String> SWIFT_GATES =
      Set.of(SupportedQualityGates.XCODEBUILD_TEST, SupportedQualityGates.SWIFT_COVERAGE);
  private static final String STRYKER_REPORT_HINT =
      "Enable Stryker's json reporter in the command, for example with"
          + " --reporters clear-text,progress,json.";
  private static final String MUTMUT_REPORT_HINT =
      "Run `mutmut run` from mutmut 3, which writes its results under mutants/.";
  private static final String PIT_REPORT_HINT =
      "Enable PIT's XML output, for example with -DoutputFormats=XML,HTML for Maven or"
          + " outputFormats = ['XML', 'HTML'] in the Gradle pitest block.";
  private static final Map<String, String> MUTATION_REPORT_HINTS =
      Map.of(
          SupportedQualityGates.STRYKER, STRYKER_REPORT_HINT,
          SupportedQualityGates.MUTMUT, MUTMUT_REPORT_HINT,
          SupportedQualityGates.MUTATION, PIT_REPORT_HINT);

  private final CommandExecutor executor;
  private final EnvironmentInspection environment;
  private final ScenarioInventory scenarioInventory;
  private final TestMappingInventory testMappingInventory;
  private final ReportDirectories reportDirectories;
  private final OpenApiGates openApiGates;
  private final MutationReport strykerReport;
  private final MutationReport pitReport;
  private final MutationReport mutmutReport;
  private final GateReportPorts reports;

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
        new GateReportPorts(reportDirectories, null, null, null),
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
    this.mutmutReport = reports.mutmut();
    this.reports = reports;
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
   * Creates one gate per configured check, in declaration order; disabled checks become skipped
   * gates. Each gate's result carries the check's type.
   *
   * @throws WardException if a check's type is unknown
   */
  public List<QualityGate> create(final WardConfiguration configuration, final Project project) {
    configuration.checks().values().forEach(QualityGateFactory::requireSupportedType);
    return configuration.checks().values().stream().map(check -> typed(check, project)).toList();
  }

  private QualityGate typed(final GateConfiguration check, final Project project) {
    final QualityGate gate =
        check.enabled() ? createGate(check, project) : new SkippedQualityGate(check.name());
    return new TypedQualityGate(check.type(), gate);
  }

  private static void requireSupportedType(final GateConfiguration check) {
    if (!SUPPORTED_GATES.contains(check.type())
        && !SupportedQualityGates.LEGACY_IDS.contains(check.type())) {
      throw new WardException(
          "Quality gate type '"
              + check.type()
              + "' of check '"
              + check.name()
              + "' is unknown. Supported types: "
              + SUPPORTED_GATES);
    }
  }

  private QualityGate createGate(final GateConfiguration check, final Project project) {
    final String name = check.name();
    final String type = check.type();
    if (SupportedQualityGates.specIds().contains(type)) {
      return environment == null
          ? new CommandQualityGate(name, executor, check.command())
          : new OpenSpecQualityGate(name, executor, environment, check.command(), check.root());
    }
    if (MavenTestGate.NAME.equals(type)) {
      return new MavenTestGate(executor, testsCommand(check.command(), project));
    }
    if (OpenSpecJunitTraceabilityGate.NAME.equals(type)) {
      return traceabilityGate();
    }
    if (ZAP.equals(type)) {
      ZapTargetValidation.require(name, check.command());
    }
    if (SupportedQualityGates.openApiIds().contains(type)) {
      return openApiGate(name, check, project);
    }
    if (MUTATION_GATES.contains(type)) {
      return mutationGate(check);
    }
    if (SWIFT_GATES.contains(type)) {
      return SwiftGates.create(check, executor, reports);
    }
    return new CommandQualityGate(name, executor, check.command(), reportDirectories);
  }

  /** Builds a Stryker, PIT or mutmut gate that checks its {@code minimum} against its report. */
  private QualityGate mutationGate(final GateConfiguration check) {
    final QualityGate command =
        new CommandQualityGate(check.name(), executor, check.command(), reportDirectories);
    if (check.minimum() == null) {
      return command;
    }
    final MutationReport report =
        switch (check.type()) {
          case SupportedQualityGates.STRYKER -> strykerReport;
          case SupportedQualityGates.MUTMUT -> mutmutReport;
          default -> pitReport;
        };
    if (report == null) {
      throw new WardException("The '" + check.name() + "' check cannot check its minimum here.");
    }
    return new MutationScoreGate(
        command, report, check.minimum(), MUTATION_REPORT_HINTS.get(check.type()));
  }

  private QualityGate openApiGate(
      final String name, final GateConfiguration check, final Project project) {
    if (openApiGates == null) {
      OpenApiCommandValidation.require(name, check.command());
      throw new WardException("OpenAPI gate dependencies are unavailable.");
    }
    return openApiGates.create(name, check, project);
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
