package dev.ward.application.gate;

import dev.ward.application.gate.jvm.MavenTestGate;
import dev.ward.application.gate.openapi.OpenApiCommandValidation;
import dev.ward.application.gate.openspec.OpenSpecQualityGate;
import dev.ward.application.gate.swift.SwiftGates;
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
  private static final Set<String> IN_PROCESS_GATES =
      Set.of(
          SupportedQualityGates.ANALYSIS, SupportedQualityGates.FORMAT, SupportedQualityGates.CRAP);

  public static final List<String> SUPPORTED_GATES = SupportedQualityGates.IDS;
  private static final Set<String> PLATFORM_GATES =
      Set.of(
          SupportedQualityGates.STRYKER,
          SupportedQualityGates.MUTATION,
          SupportedQualityGates.MUTMUT,
          SupportedQualityGates.XCODEBUILD_TEST,
          SupportedQualityGates.SWIFT_COVERAGE,
          SupportedQualityGates.ANDROID_COVERAGE);
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
  private final TraceabilitySources traceability;
  private final ReportDirectories reportDirectories;
  private final MutationReport strykerReport;
  private final MutationReport pitReport;
  private final MutationReport mutmutReport;
  private final GateReportPorts reports;
  private final GateExtensions extensions;

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
   * reads mutation reports, and {@code extensions} builds the OpenAPI gates and the {@code ward}
   * gate.
   */
  @Inject
  public QualityGateFactory(
      final CommandExecutor executor,
      final EnvironmentInspection environment,
      final TraceabilitySources traceability,
      final GateReportPorts reports,
      final GateExtensions extensions) {
    this.reportDirectories = reports.directories();
    this.strykerReport = reports.stryker();
    this.pitReport = reports.pit();
    this.mutmutReport = reports.mutmut();
    this.reports = reports;
    this.executor = executor;
    this.environment = environment;
    this.traceability = traceability;
    this.extensions = extensions == null ? GateExtensions.NONE : extensions;
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
    return create(configuration, project, Map.of());
  }

  /**
   * Creates the gates like {@link #create(WardConfiguration, Project)}, and narrows each {@code
   * ward} gate named in {@code focus} to its root-relative changed paths. A gate not named in it
   * runs in full.
   *
   * @throws WardException if a check's type is unknown
   */
  public List<QualityGate> create(
      final WardConfiguration configuration,
      final Project project,
      final Map<String, List<String>> focus) {
    configuration.checks().values().forEach(QualityGateFactory::requireSupportedType);
    return configuration.checks().values().stream()
        .<QualityGate>map(
            check ->
                new TypedQualityGate(
                    check.type(),
                    check.enabled()
                        ? createGate(check, project, focus.getOrDefault(check.name(), List.of()))
                        : new SkippedQualityGate(check.name())))
        .toList();
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

  private QualityGate createGate(
      final GateConfiguration check, final Project project, final List<String> focus) {
    final String name = check.name();
    final String type = check.type();
    if (IN_PROCESS_GATES.contains(type)) {
      return inProcessGate(check, focus);
    }
    if (SupportedQualityGates.specIds().contains(type)) {
      return environment == null
          ? new CommandQualityGate(name, executor, check.command())
          : new OpenSpecQualityGate(name, executor, environment, check.command(), check.root());
    }
    if (MavenTestGate.NAME.equals(type)) {
      return new MavenTestGate(executor, testsCommand(check.command(), project));
    }
    if (SupportedQualityGates.TRACEABILITY.equals(type)) {
      return traceability.gate();
    }
    if (ZAP.equals(type)) {
      ZapTargetValidation.require(name, check.command());
    }
    if (SupportedQualityGates.openApiIds().contains(type)) {
      return openApiGate(name, check, project);
    }
    if (PLATFORM_GATES.contains(type)) {
      return platformGate(check);
    }
    return new CommandQualityGate(name, executor, check.command(), reportDirectories);
  }

  /** Builds a mutation, Swift or Android gate, each of which reads its own tool's report. */
  private QualityGate platformGate(final GateConfiguration check) {
    if (SupportedQualityGates.ANDROID_COVERAGE.equals(check.type())) {
      return CoverageGates.minimum(check, executor, reports, reports.swift().android());
    }
    if (SupportedQualityGates.STRYKER.equals(check.type())
        || SupportedQualityGates.MUTATION.equals(check.type())
        || SupportedQualityGates.MUTMUT.equals(check.type())) {
      return mutationGate(check);
    }
    return SwiftGates.create(check, executor, reports);
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

  /**
   * Builds the gates that run inside Ward: the {@code ward} analysis and the {@code ward-format}.
   */
  private QualityGate inProcessGate(final GateConfiguration check, final List<String> focus) {
    if (SupportedQualityGates.CRAP.equals(check.type())) {
      return CrapGates.create(check, extensions);
    }
    if (!SupportedQualityGates.FORMAT.equals(check.type())) {
      return AnalysisGates.create(check, extensions.analysis(), focus);
    }
    if (extensions.format() == null) {
      throw new WardException("Format gate dependencies are unavailable.");
    }
    return extensions.format().create(check.name(), focus);
  }

  private QualityGate openApiGate(
      final String name, final GateConfiguration check, final Project project) {
    if (extensions.openApi() == null) {
      OpenApiCommandValidation.require(name, check.command());
      throw new WardException("OpenAPI gate dependencies are unavailable.");
    }
    return extensions.openApi().create(name, check, project);
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
