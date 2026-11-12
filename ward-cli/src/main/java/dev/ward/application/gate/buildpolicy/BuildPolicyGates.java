package dev.ward.application.gate.buildpolicy;

import dev.ward.application.buildpolicy.BuildPolicyService;
import dev.ward.application.gate.AnalysisRuleSource;
import dev.ward.domain.gate.QualityGate;
import javax.inject.Inject;

/** Builds the {@code build-policy} gate. */
public final class BuildPolicyGates {
  private final BuildPolicyService service;
  private final AnalysisRuleSource rules;

  /** Creates the builder over the use case and the reader of {@code ward-rules.toml}. */
  @Inject
  public BuildPolicyGates(final BuildPolicyService service, final AnalysisRuleSource rules) {
    this.service = service;
    this.rules = rules;
  }

  /** Builds the gate named {@code name}; {@code rulesFile} is the {@code rules-file} path. */
  public QualityGate create(final String name, final String rulesFile) {
    return new BuildPolicyGate(name, rulesFile, service, rules);
  }
}
