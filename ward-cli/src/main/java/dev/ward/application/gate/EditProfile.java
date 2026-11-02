package dev.ward.application.gate;

import dev.ward.domain.config.PartialCheckPlan;
import dev.ward.domain.config.WardConfiguration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Decides what the {@code edit} profile runs when no edited paths narrow it. */
final class EditProfile {
  private static final Logger LOGGER = Logger.getLogger(EditProfile.class.getName());
  static final String NAME = "edit";

  private EditProfile() {}

  /**
   * Plans an {@code edit} run without edited paths: checks with a {@code paths} table are skipped,
   * the others run in full. Warns, because the hook or caller did not say what changed.
   */
  static PartialCheckPlan plan(final WardConfiguration selected) {
    LOGGER.log(
        Level.WARNING,
        "event=edit-without-paths warning=The edit profile expects edited paths; checks with a"
            + " paths table are skipped and the others run in full.");
    return PartialCheckPlan.withoutPaths(selected.checks());
  }

  /**
   * Drops {@code edit} when no check belongs to it, so an agent edit on a configuration without
   * edit checks passes with a warning instead of failing on an unknown profile.
   */
  static List<String> withoutEmptyEdit(
      final WardConfiguration configuration, final List<String> requested) {
    if (requested == null) {
      return List.of();
    }
    if (!requested.contains(NAME) || configuration.profileNames().contains(NAME)) {
      return requested;
    }
    LOGGER.log(
        Level.WARNING,
        "event=edit-profile-empty warning=No check belongs to the edit profile; no check ran.");
    return requested.stream().filter(profile -> !NAME.equals(profile)).toList();
  }
}
