package dev.ward.domain.project;

import java.nio.file.Path;
import java.util.List;

/** Lists the directories of a Swift project that hold Swift sources, for the formatter. */
@FunctionalInterface
public interface SwiftSourceDirectories {
  /**
   * Returns the top-level directories of {@code root} that hold a {@code .swift} file, sorted, plus
   * {@code Package.swift} when it exists. Hidden directories and dependency or build output ({@code
   * .build}, {@code Pods}, {@code Carthage}, {@code DerivedData}) are left out.
   */
  List<String> of(Path root);
}
