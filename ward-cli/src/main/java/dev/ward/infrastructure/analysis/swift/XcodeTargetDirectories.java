package dev.ward.infrastructure.analysis.swift;

import java.util.List;

/**
 * The folder of a classic Xcode target, read from the files its build phase lists. Unlisted files
 * in that folder belong to the target too, so this folder is what places them.
 */
final class XcodeTargetDirectories {

  private static final String SEPARATOR = "/";
  private static final String EMPTY = "";

  private XcodeTargetDirectories() {}

  /**
   * The deepest directory that holds every listed file. Empty when no directory holds them all.
   *
   * @param files the project-relative paths of the files the target lists
   */
  static List<String> of(final List<String> files) {
    if (files.isEmpty()) {
      return List.of();
    }
    String common = directoryOf(files.get(0));
    for (final String file : files) {
      while (!common.isEmpty() && !file.startsWith(common + SEPARATOR)) {
        common = directoryOf(common);
      }
    }
    return common.isEmpty() ? List.of() : List.of(common);
  }

  private static String directoryOf(final String path) {
    final int separator = path.lastIndexOf(SEPARATOR);
    return separator < 0 ? EMPTY : path.substring(0, separator);
  }
}
