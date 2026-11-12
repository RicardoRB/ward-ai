package dev.ward.domain.init;

import java.nio.file.Path;

/** Outbound port for an explicitly approved project-local tool installation. */
@FunctionalInterface
public interface ToolInstaller {
  /** Installs {@code tool} under {@code projectRoot} and reports the outcome. */
  ToolInstallationResult install(Path projectRoot, String tool);
}
