package dev.ward.domain.project;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Reads the Xcode workspace, shared schemes and target platforms of an Xcode project. */
public interface XcodeSchemes {

  /**
   * Returns the name of the {@code .xcworkspace} in {@code root}, or empty when there is none; the
   * workspace is the container the Xcode test command uses in preference to the project.
   */
  Optional<String> workspace(Path root);

  /**
   * Returns the shared schemes of the workspace or project in {@code root}, in listing order.
   *
   * @throws dev.ward.domain.config.WardException if the schemes cannot be listed
   */
  List<String> schemes(Path root);

  /** Returns whether every target of the project in {@code root} builds for macOS only. */
  boolean macOsOnly(Path root);
}
