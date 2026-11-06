package dev.ward.application.doctor;

import dev.ward.application.doctor.DoctorService.Finding;
import dev.ward.application.doctor.DoctorService.Status;
import dev.ward.application.gate.AnalysisRuleSource;
import dev.ward.application.gate.RuleConfigurationCheck;
import dev.ward.application.gate.RuleSet;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.config.WardConfigurationReader;
import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.SupportedQualityGates;
import java.nio.file.Path;
import java.util.List;

/** Reports whether the rule configuration file of each enabled {@code ward} check is usable. */
final class RuleConfigurationFindings {
  private static final String NAME = "rule configuration";

  private RuleConfigurationFindings() {}

  /** Adds one finding per problem of each enabled {@code ward} check that has a rules file. */
  static void diagnose(
      final WardConfigurationReader reader,
      final AnalysisRuleSource rules,
      final Path root,
      final List<Finding> findings) {
    if (reader == null || rules == null) {
      return;
    }
    try {
      reader.read(root.resolve(WardConfiguration.FILE_NAME)).checks().values().stream()
          .filter(check -> check.enabled() && SupportedQualityGates.ANALYSIS.equals(check.type()))
          .forEach(
              check ->
                  RuleConfigurationCheck.resolve(check, rules, root)
                      .ifPresent(set -> findings.addAll(of(set))));
    } catch (WardException ignored) {
      // The configuration finding already reports malformed configuration.
    }
  }

  private static List<Finding> of(final RuleSet set) {
    if (set.problems().isEmpty()) {
      return List.of(
          new Finding(NAME, Status.OK, set.rules().size() + " rule(s) selected and valid."));
    }
    return set.problems().stream()
        .map(problem -> new Finding(NAME, Status.ERROR, problem))
        .toList();
  }
}
