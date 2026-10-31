package dev.ward.domain.project;

import java.util.Optional;

/** Reads the Swift tool chain installed on this machine. */
public interface SwiftToolchainInspection {

  /** Returns the selected Xcode version, such as {@code 26.6}, or empty when it cannot be read. */
  Optional<String> xcodeVersion();

  /** Returns whether this machine runs macOS, the only system Ward verifies Swift projects on. */
  boolean macOs();
}
