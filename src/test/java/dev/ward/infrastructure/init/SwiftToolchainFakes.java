package dev.ward.infrastructure.init;

import dev.ward.domain.project.SwiftToolchainInspection;
import java.util.Optional;

/** Fake Swift toolchains for the tests: a Mac with an Xcode version, or a machine that is not. */
final class SwiftToolchainFakes {
  private SwiftToolchainFakes() {}

  /** A macOS machine whose selected Xcode is {@code version}; {@code null} means unreadable. */
  static SwiftToolchainInspection mac(final String version) {
    return new SwiftToolchainInspection() {
      @Override
      public Optional<String> xcodeVersion() {
        return Optional.ofNullable(version);
      }

      @Override
      public boolean macOs() {
        return true;
      }
    };
  }

  /** A Linux machine, which Ward does not set up Swift projects on. */
  static SwiftToolchainInspection notMac() {
    return new SwiftToolchainInspection() {
      @Override
      public Optional<String> xcodeVersion() {
        return Optional.empty();
      }

      @Override
      public boolean macOs() {
        return false;
      }
    };
  }
}
