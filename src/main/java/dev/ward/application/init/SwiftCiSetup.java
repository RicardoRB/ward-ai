package dev.ward.application.init;

import dev.ward.domain.project.Project;

/**
 * The CI steps for Swift projects: resolve Ward's SwiftLint tools package, cached by its resolved
 * versions. Swift jobs run on macOS and set up no JDK, Node or Python.
 */
final class SwiftCiSetup {
  /** The macOS image that ships Xcode 26 or newer, which Swift projects need. */
  static final String RUNNER = "macos-26";

  private static final String TOOLS_BUILD = ".ward/swift-tools/.build";
  private static final String RESOLVED = ".ward/swift-tools/Package.resolved";

  private SwiftCiSetup() {}

  /** Returns the cache and resolve steps for {@code project}'s Swift tools. */
  static String steps(final Project project) {
    return CiWorkflowTemplate.STEP
        + "Cache Ward's SwiftLint build\n"
        + "        uses: actions/cache@v4\n"
        + "        with:\n"
        + "          path: "
        + TOOLS_BUILD
        + "\n"
        + "          key: ${{ runner.os }}-swift-tools-${{ hashFiles('"
        + RESOLVED
        + "') }}\n"
        + CiWorkflowTemplate.STEP
        + "Resolve Ward's SwiftLint tools\n"
        + "        run: swift package --package-path .ward/swift-tools resolve\n";
  }
}
