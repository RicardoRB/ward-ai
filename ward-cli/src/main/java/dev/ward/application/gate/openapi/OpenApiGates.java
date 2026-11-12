package dev.ward.application.gate.openapi;

import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.OpenApiGateOptions;
import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.ProjectFiles;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.gate.RevisionFiles;
import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.project.Project;
import java.time.Clock;
import javax.inject.Inject;

/** Builds the {@code openapi-lint}, {@code openapi-diff}, and {@code openapi-compat} gates. */
public final class OpenApiGates {
  private final Ports ports;
  private final Clock clock;

  /** Creates the builder from the ports the OpenAPI gates use. */
  @Inject
  public OpenApiGates(
      final CommandExecutor executor, final RevisionFiles revisions, final ProjectFiles files) {
    this(executor, revisions, files, Clock.systemUTC());
  }

  /** Creates the builder with the clock that dates sunsets, for tests. */
  OpenApiGates(
      final CommandExecutor executor,
      final RevisionFiles revisions,
      final ProjectFiles files,
      final Clock clock) {
    this.ports = new Ports(executor, revisions, files);
    this.clock = clock;
  }

  /**
   * Creates the OpenAPI gate {@code id}. With a {@code project}, an {@code openapi-compat} base ref
   * that does not resolve is a configuration error.
   *
   * @throws WardException if the command or options are invalid
   */
  public QualityGate create(
      final String id, final GateConfiguration configuration, final Project project) {
    final String type = configuration.type();
    final OpenApiGateOptions options = configuration.openApi();
    if (options == null) {
      throw new WardException("'checks." + id + ".spec' is required for an enabled OpenAPI gate");
    }
    if (SupportedQualityGates.OPENAPI_LINT.equals(type)) {
      OpenApiCommandValidation.require(id, configuration.command());
      return new OpenApiLintGate(
          id, ports.executor(), ports.files(), configuration.command(), options.spec());
    }
    return comparison(id, configuration, options, project);
  }

  private QualityGate comparison(
      final String id,
      final GateConfiguration configuration,
      final OpenApiGateOptions options,
      final Project project) {
    if (!configuration.command().isEmpty()) {
      throw new WardException(
          "Quality gate '"
              + id
              + "' does not take a 'command': it compares the spec natively. Remove 'command'.");
    }
    if (SupportedQualityGates.OPENAPI_DIFF.equals(configuration.type())) {
      return new OpenApiDiffGate(
          id, ports, options.spec(), OpenApiBaselines.lastCommit(ports.revisions()), clock);
    }
    if (project != null && ports.revisions().resolve(project.root(), options.base()).isEmpty()) {
      throw new WardException(
          "Quality gate '"
              + id
              + "' cannot resolve base ref '"
              + options.base()
              + "'; set 'base' in ward.toml or fetch it.");
    }
    return new OpenApiDiffGate(
        id,
        ports,
        options.spec(),
        OpenApiBaselines.baseBranch(ports.revisions(), options.base()),
        clock);
  }

  /** The ports shared by the OpenAPI gates. */
  record Ports(CommandExecutor executor, RevisionFiles revisions, ProjectFiles files) {}
}
