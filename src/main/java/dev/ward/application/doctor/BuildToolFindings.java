package dev.ward.application.doctor;

import dev.ward.domain.project.Toolchain;

/** Chooses the build tool check for a project's toolchain. */
final class BuildToolFindings {
  private BuildToolFindings() {}

  /** Returns the check for {@code toolchain}; toolchains Ward does not check yet get a warning. */
  static ToolchainFinding forToolchain(final Toolchain toolchain) {
    return switch (toolchain) {
      case JVM -> new JvmToolchainFinding();
      case NODE -> new NodeToolchainFinding();
      case PYTHON -> new PythonToolchainFinding();
      default -> new UncheckedToolchainFinding();
    };
  }
}
