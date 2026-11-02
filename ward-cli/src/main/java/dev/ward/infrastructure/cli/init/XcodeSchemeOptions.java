package dev.ward.infrastructure.cli.init;

import picocli.CommandLine.Option;

/** The option that names the Xcode scheme of a Swift project's tests. */
public class XcodeSchemeOptions {
  // Picocli assigns annotated fields reflectively, so they cannot be final.
  @Option(
      names = "--scheme",
      paramLabel = "<name>",
      description = "Xcode scheme that the tests run, when the project has several.")
  private String scheme;

  /** Returns the scheme the user named, or {@code null} when none was given. */
  String scheme() {
    return scheme;
  }
}
