package dev.ward.application.doctor;

import dev.ward.application.doctor.DoctorService.Finding;
import dev.ward.application.doctor.DoctorService.Status;
import dev.ward.domain.config.CommandTypeInference;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.config.WardConfigurationReader;
import dev.ward.domain.config.WardException;
import dev.ward.domain.doctor.EnvironmentFacts;
import java.nio.file.Path;
import java.util.List;

/** Reports the built-in tool type of one configured check. */
final class CheckTypeFindings {
  private CheckTypeFindings() {}

  /** The finding for one check: its built-in type, or {@code command} when not recognized. */
  static Finding of(final GateConfiguration check) {
    final String name = "check " + check.name();
    return CommandTypeInference.COMMAND.equals(check.type())
        ? new Finding(
            name,
            Status.OK,
            "type command (not a recognized tool; set uses to attach a built-in type)")
        : new Finding(name, Status.OK, "type " + check.type());
  }

  /** Whether a check of built-in {@code type} is enabled. */
  static boolean enabledOfType(final WardConfiguration configuration, final String type) {
    return configuration.checks().values().stream()
        .anyMatch(check -> check.enabled() && type.equals(check.type()));
  }

  /** Reports the built-in type of each configured check; {@code command} means not recognized. */
  static void diagnose(
      final WardConfigurationReader configurationReader,
      final Path root,
      final EnvironmentFacts facts,
      final List<Finding> findings) {
    if (configurationReader == null || !DoctorService.flag(facts, "configurationPresent")) {
      return;
    }
    try {
      configurationReader
          .read(root.resolve(WardConfiguration.FILE_NAME))
          .checks()
          .values()
          .forEach(check -> findings.add(of(check)));
    } catch (WardException ignored) {
      // The configuration finding already reports malformed configuration.
    }
  }
}
