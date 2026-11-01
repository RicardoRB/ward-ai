package dev.ward.application.init;

import dev.ward.application.init.openspec.OpenSpecDirectories;
import dev.ward.domain.agent.IntegrationResult;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.config.WardConfigurationReader;
import dev.ward.domain.config.WardException;
import dev.ward.domain.doctor.EnvironmentInspection;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.InitToolOption;
import dev.ward.domain.init.OwnedSection;
import dev.ward.domain.init.QualityHookGeneration;
import dev.ward.domain.project.PackageManifests;
import dev.ward.domain.project.Project;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;

/** Stable OpenSpec tool choices and project-aware defaults used by init. */
public final class InitSetupCatalog extends InitGeneralCatalog {
  public static final List<String> OPENSPEC_VALIDATIONS = List.of("openspec", "openspec-archived");
  private final EnvironmentInspection environment;
  private static final Logger LOGGER = Logger.getLogger(InitSetupCatalog.class.getName());

  private final Supplier<QualityHookGeneration> qualityHooks;
  private final WardConfigurationReader configurations;
  private final String wardVersion;
  private final PackageManifests manifests;

  /** Creates a catalog that installs no quality hooks. */
  public InitSetupCatalog(final EnvironmentInspection environment) {
    this(environment, new NoQualityHookGeneration());
  }

  /** Creates a catalog that cannot read an existing configuration. */
  public InitSetupCatalog(
      final EnvironmentInspection environment, final QualityHookGeneration qualityHooks) {
    this(
        environment,
        qualityHooks,
        file -> {
          throw new WardException("Reading the existing configuration is unavailable.");
        });
  }

  /** Creates a catalog for a development build, whose CI workflow installs the latest release. */
  public InitSetupCatalog(
      final EnvironmentInspection environment,
      final QualityHookGeneration qualityHooks,
      final WardConfigurationReader configurations) {
    this(environment, qualityHooks, configurations, CiWorkflowTemplate.DEVELOPMENT_VERSION);
  }

  /** Creates a catalog that reads no {@code package.json}. */
  public InitSetupCatalog(
      final EnvironmentInspection environment,
      final QualityHookGeneration qualityHooks,
      final WardConfigurationReader configurations,
      final String wardVersion) {
    this(InitInputs.withoutManifests(environment), qualityHooks, configurations, wardVersion);
  }

  /**
   * Creates the catalog. {@code node} reads Node packages, {@code qualityHooks} installs Git hooks,
   * {@code configurations} reads the configuration being replaced, and {@code wardVersion} is the
   * release a generated CI workflow installs.
   */
  public InitSetupCatalog(
      final EnvironmentInspection environment,
      final NodeInputs node,
      final QualityHookGeneration qualityHooks,
      final WardConfigurationReader configurations,
      @Named("ward.version") final String wardVersion) {
    this(InitInputs.of(environment, node), qualityHooks, configurations, wardVersion);
  }

  /**
   * Creates the catalog that Dagger injects. {@code ports} also read the project's runtime JDK, so
   * the catalog omits the gates it cannot run.
   */
  @Inject
  public InitSetupCatalog(
      final InitPorts ports,
      final QualityHookGeneration qualityHooks,
      final WardConfigurationReader configurations,
      @Named("ward.version") final String wardVersion) {
    this(InitInputs.of(ports), qualityHooks, configurations, wardVersion);
  }

  private InitSetupCatalog(
      final InitInputs inputs,
      final QualityHookGeneration qualityHooks,
      final WardConfigurationReader configurations,
      final String wardVersion) {
    super(inputs);
    this.environment = inputs.environment();
    this.manifests = inputs.manifests();
    this.qualityHooks = () -> qualityHooks;
    this.configurations = configurations;
    this.wardVersion = wardVersion;
  }

  /**
   * The enabled quality checks offered by init in the project's existing {@code ward.toml}, in
   * declaration order, or empty when it cannot be read.
   */
  public Optional<PreviousChoices> previousChoices(final Project project) {
    final WardConfiguration configuration;
    try {
      configuration = configurations.read(project.root().resolve(WardConfiguration.FILE_NAME));
    } catch (WardException unreadable) {
      LOGGER.log(Level.INFO, "event=init-reconfigure-prefill status=unreadable");
      return Optional.empty();
    }
    final Set<String> offered =
        gates(project, null).stream().map(InitGateOption::id).collect(Collectors.toSet());
    return Optional.of(
        new PreviousChoices(
            configuration.enabledGates().keySet().stream().filter(offered::contains).toList()));
  }

  /** Renders the CI workflow of {@code provider} that installs this Ward release. */
  OwnedSection ciWorkflow(
      final String provider, final Project project, final List<InitGateOption> gates) {
    return CiWorkflowTemplate.render(
        provider,
        project,
        gates,
        wardVersion,
        new CiWorkflowTemplate.ProjectFacts(
            manifests.read(project.root()),
            inputs.runtimeJdk(project),
            gates.stream().anyMatch(gate -> "harmonize".equals(gate.id()))));
  }

  QualityHookGeneration qualityHooks() {
    return qualityHooks.get();
  }

  /** Removes the Ward-owned quality hooks of {@code project}. */
  public IntegrationResult removeQualityHooks(final Project project) {
    return qualityHooks.get().remove(project);
  }

  /** Returns the optional tools init can install. */
  public List<InitToolOption> tools() {
    return List.of(new InitToolOption("openspec", "OpenSpec project validation tool"));
  }

  /** Returns the OpenSpec validation IDs. */
  public List<String> openSpecValidations() {
    return OPENSPEC_VALIDATIONS;
  }

  /** Returns whether a usable OpenSpec executable is on the path of {@code project}. */
  public boolean openSpecAvailable(final Project project) {
    return environment.inspectExecutable(project.root(), "openspec").usable();
  }

  /** Returns where the OpenSpec gates of a project find {@code openspec/}. */
  public OpenSpecDirectories openSpecDirectories() {
    return new OpenSpecDirectories(environment);
  }

  /** Returns whether {@code NVD_API_KEY} is set, which dependency-check needs to update quickly. */
  public boolean nvdApiKeySet(final Project project) {
    return environment.inspect(project.root()).isSet("NVD_API_KEY");
  }

  /**
   * Returns the gate choice for the OpenSpec validation {@code id}, run with {@code executable}.
   *
   * @throws WardException if {@code id} is unknown
   */
  public InitGateOption openSpecValidation(
      final Project project, final String id, final String executable) {
    if (!openSpecValidations().contains(id)) {
      throw new WardException(
          "Unknown OpenSpec validation '"
              + id
              + "'. Supported validations: openspec, openspec-archived");
    }
    final List<String> command =
        "openspec".equals(id)
            ? List.of(executable, "validate", "--all", "--strict", "--no-interactive")
            : List.of(executable, "validate", "--archived", "--no-interactive");
    final boolean available = environment.inspectExecutable(project.root(), executable).usable();
    return new InitGateOption(
        id,
        "OpenSpec " + ("openspec".equals(id) ? "active" : "archived") + " validation.",
        command,
        available,
        available
            ? "OpenSpec executable available."
            : "OpenSpec is unavailable; install it or configure a local executable.");
  }
}
