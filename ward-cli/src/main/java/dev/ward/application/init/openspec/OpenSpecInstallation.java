package dev.ward.application.init.openspec;

import dev.ward.domain.config.WardException;
import dev.ward.domain.init.InitSelection;
import dev.ward.domain.init.ToolInstaller;
import dev.ward.domain.project.Project;
import java.nio.file.Path;

/** Runs the OpenSpec installation a selection consented to. */
public final class OpenSpecInstallation {
  public static final String TOOL = "openspec";

  private OpenSpecInstallation() {}

  /** Installs OpenSpec when consented and returns its executable, or {@code null} when skipped. */
  public static Path install(
      final ToolInstaller installer, final Project project, final InitSelection selection) {
    if (!selection.installTools().contains(TOOL)) {
      return null;
    }
    if (!selection.tools().contains(TOOL)) {
      throw new WardException("Installation consent requires selecting the corresponding tool.");
    }
    try {
      return installer.installPath(project.root(), TOOL);
    } catch (IllegalStateException e) {
      throw new WardException("OpenSpec installation failed: " + e.getMessage(), e);
    }
  }

  /** The command that runs OpenSpec: the installed executable, or {@code openspec} on PATH. */
  public static String executable(final Path installed) {
    return installed == null ? TOOL : installed.toString();
  }
}
