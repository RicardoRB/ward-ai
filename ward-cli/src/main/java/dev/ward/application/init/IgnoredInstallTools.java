package dev.ward.application.init;

import dev.ward.domain.init.InitSelection;
import java.util.logging.Level;
import java.util.logging.Logger;

/** {@code --install-tool} is accepted for old command lines; init installs no gate tool. */
final class IgnoredInstallTools {
  private static final Logger LOGGER = Logger.getLogger(IgnoredInstallTools.class.getName());

  private IgnoredInstallTools() {}

  /** Logs that the values of the option had no effect. */
  static void warn(final InitSelection selection) {
    if (!selection.installTools().isEmpty()) {
      LOGGER.log(
          Level.WARNING,
          () -> "event=init-install-tool-ignored count=" + selection.installTools().size());
    }
  }
}
