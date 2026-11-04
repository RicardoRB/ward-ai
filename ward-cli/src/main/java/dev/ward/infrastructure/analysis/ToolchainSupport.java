package dev.ward.infrastructure.analysis;

import dev.ward.domain.project.Toolchain;
import java.util.EnumSet;
import java.util.Set;

/**
 * Classifies every toolchain for dependency resolution: one with a layout reader contributes an
 * adapter, one without support contributes no edges. Every toolchain is in exactly one set, so a
 * new toolchain must be classified before the build passes.
 */
final class ToolchainSupport {

  /** The toolchains whose layout reader builds a dependency adapter. */
  static final Set<Toolchain> WITH_LAYOUT_READER =
      EnumSet.of(Toolchain.JVM, Toolchain.NODE, Toolchain.SWIFT);

  /** The toolchains that have no dependency adapter yet, so they contribute no edges. */
  static final Set<Toolchain> WITHOUT_SUPPORT =
      EnumSet.of(Toolchain.PYTHON, Toolchain.GO, Toolchain.RUST, Toolchain.DOTNET);

  private ToolchainSupport() {}
}
