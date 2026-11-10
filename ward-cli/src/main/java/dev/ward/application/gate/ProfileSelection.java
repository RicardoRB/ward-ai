package dev.ward.application.gate;

import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.config.WardConfigurationReader;
import dev.ward.domain.config.WardException;
import dev.ward.domain.project.Project;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Chooses the gates of a project's configuration for a full or partial check. */
final class ProfileSelection {
  private static final Logger LOGGER = Logger.getLogger(ProfileSelection.class.getName());

  private ProfileSelection() {}

  /**
   * Selects the profiles' gates except {@code excludedGates}. When the exclusion alone leaves
   * nothing enabled, the result is empty rather than an error, because the excluded gates run
   * elsewhere.
   */
  static Optional<WardConfiguration> select(
      final WardConfigurationReader configurationReader,
      final Project project,
      final List<String> requestedProfiles,
      final Set<String> excludedGates) {
    final WardConfiguration configuration =
        configurationReader.read(project.root().resolve(WardConfiguration.FILE_NAME));
    final List<String> profiles = EditProfile.withoutEmptyEdit(configuration, requestedProfiles);
    if (profiles.isEmpty() && requestedProfiles != null && !requestedProfiles.isEmpty()) {
      return Optional.empty();
    }
    final WardConfiguration profiled = configuration.forProfiles(profiles);
    final WardConfiguration selected = profiled.withoutTypes(excludedGates);
    if (!profiled.enabledGates().isEmpty() && selected.enabledGates().isEmpty()) {
      LOGGER.log(Level.INFO, "event=configuration-selected gates=0 reason=all-excluded");
      return Optional.empty();
    }
    if (selected.enabledGates().isEmpty()) {
      throw new WardException(
          "No quality gates are enabled in "
              + WardConfiguration.FILE_NAME
              + "; refusing to report a pass without checking anything.");
    }
    return Optional.of(selected);
  }
}
