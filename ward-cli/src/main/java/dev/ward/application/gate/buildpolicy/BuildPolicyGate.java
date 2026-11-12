package dev.ward.application.gate.buildpolicy;

import dev.ward.application.buildpolicy.BuildPolicyService;
import dev.ward.application.buildpolicy.BuildPolicySetting;
import dev.ward.application.gate.AnalysisRuleSource;
import dev.ward.application.gate.RuleConfigFile;
import dev.ward.domain.buildpolicy.BuildFileException;
import dev.ward.domain.dependency.DependencyInventoryException;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.FindingSeverity;
import dev.ward.domain.gate.GateFindings;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.project.Project;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/** The {@code build-policy} gate: runs the selected build rules, starting no process. */
final class BuildPolicyGate implements QualityGate {
  private static final Logger LOGGER = Logger.getLogger(BuildPolicyGate.class.getName());

  private final String name;
  private final String rulesFile;
  private final BuildPolicyService service;
  private final AnalysisRuleSource rules;

  BuildPolicyGate(
      final String name,
      final String rulesFile,
      final BuildPolicyService service,
      final AnalysisRuleSource rules) {
    this.name = name;
    this.rulesFile = rulesFile;
    this.service = service;
    this.rules = rules;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public GateResult execute(final Project project) {
    final long start = System.nanoTime();
    final Optional<RuleConfigFile> file = rules.configuration(project.root(), rulesFile);
    final List<BuildPolicySetting> settings;
    if (file.isPresent()) {
      final BuildPolicyConfig.Resolved resolved = BuildPolicyConfig.resolve(file.get());
      if (!resolved.problems().isEmpty()) {
        LOGGER.log(Level.FINE, "event=build-policy-config-problems");
        return error(
            resolved.problems().size() + " rule configuration error(s)",
            String.join("\n", resolved.problems()));
      }
      settings = resolved.settings();
    } else {
      settings = BuildPolicyConfig.defaults();
    }
    try {
      return result(service.run(project, name, settings), settings.size(), start);
    } catch (final BuildFileException | DependencyInventoryException exception) {
      LOGGER.log(Level.FINE, "event=build-policy-error");
      return error("Build policy could not run", exception.getMessage());
    }
  }

  private GateResult result(final List<Finding> findings, final int ruleCount, final long start) {
    final long errors =
        findings.stream().filter(finding -> finding.severity() == FindingSeverity.ERROR).count();
    return GateResult.builder(name, errors > 0 ? GateStatus.FAILED : GateStatus.PASSED)
        .duration(Duration.ofNanos(System.nanoTime() - start))
        .summary(ruleCount + " rule(s), " + findings.size() + " finding(s)")
        .errors(Math.toIntExact(errors))
        .warnings(Math.toIntExact(findings.size() - errors))
        .build()
        .withFindings(GateFindings.of(findings));
  }

  private GateResult error(final String summary, final String details) {
    return GateResult.builder(name, GateStatus.EXECUTION_ERROR)
        .summary(summary)
        .stderr(details)
        .build();
  }
}
