package dev.ward.infrastructure.init;

import java.util.List;

/**
 * The Ward-owned package that pins SwiftLint for the Swift gates. Init writes it under {@code
 * .ward/swift-tools} and resolves it; the project's own {@code Package.swift} and Xcode project are
 * never edited.
 */
final class SwiftToolsPackage {
  /** The directory Ward owns; a failed resolve removes it. */
  static final String DIRECTORY = ".ward/swift-tools";

  /** The manifest init writes into {@link #DIRECTORY}. */
  static final String MANIFEST = DIRECTORY + "/Package.swift";

  /** The lock file SwiftPM writes beside the manifest, which Ward commits. */
  static final String RESOLVED = DIRECTORY + "/Package.resolved";

  private static final String MARKER = "// managed-by: ward";

  private SwiftToolsPackage() {}

  /** Returns the manifest that pins {@link SwiftToolCatalog#SWIFTLINT_PLUGINS_VERSION} exactly. */
  static String manifest() {
    return "// swift-tools-version: 5.9\n"
        + MARKER
        + "\n"
        + "import PackageDescription\n"
        + "\n"
        + "let package = Package(\n"
        + "    name: \"WardSwiftTools\",\n"
        + "    platforms: [.macOS(.v13)],\n"
        + "    dependencies: [\n"
        + "        .package(\n"
        + "            url: \""
        + SwiftToolCatalog.SWIFTLINT_PLUGINS_URL
        + "\",\n"
        + "            exact: \""
        + SwiftToolCatalog.SWIFTLINT_PLUGINS_VERSION
        + "\"\n"
        + "        ),\n"
        + "    ]\n"
        + ")\n";
  }

  /** Returns the SwiftPM command that resolves the tools package, pinning {@link #RESOLVED}. */
  static List<String> resolveCommand() {
    return List.of("swift", "package", "--package-path", DIRECTORY, "resolve");
  }
}
