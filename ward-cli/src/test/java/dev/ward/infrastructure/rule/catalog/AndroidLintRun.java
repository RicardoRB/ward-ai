package dev.ward.infrastructure.rule.catalog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Element;

/** Runs the real Android Lint on one fixture and reads the lines of its findings. */
final class AndroidLintRun {

  private static final int SDK_LEVELS_UP = 4;
  private static final Map<String, String> LINT_IDS =
      Map.of(
          "exported-service",
          "ExportedService",
          "exported-receiver",
          "ExportedReceiver",
          "exported-content-provider",
          "ExportedContentProvider",
          "debuggable-application",
          "HardcodedDebugMode",
          "cleartext-transport",
          "InsecureBaseConfiguration,UsesCleartextTraffic",
          "allow-backup",
          "AllowBackup",
          "hardcoded-text",
          "HardcodedText",
          "missing-content-description",
          "ContentDescription",
          AndroidLintProject.UNUSED_RESOURCES,
          "UnusedResources");

  private AndroidLintRun() {}

  /** The rules that follow an Android Lint check, by name. */
  static List<String> rules() {
    return LINT_IDS.keySet().stream().sorted().toList();
  }

  /**
   * Builds the project of {@code fixture}, runs Lint on it and returns the sorted lines it reports
   * in the fixture file.
   */
  static List<Integer> lines(
      final Path lint, final String rule, final Path directory, final Path fixture)
      throws IOException {
    final Path project = Files.createTempDirectory("ward-lint");
    final String target = AndroidLintProject.place(rule, directory, fixture, project);
    final Path report = project.resolve("lint-report.xml");
    final ProcessBuilder builder =
        new ProcessBuilder(
            lint.toString(),
            "--xml",
            report.toString(),
            "--check",
            LINT_IDS.get(rule),
            "--quiet",
            project.toString());
    builder.environment().put("ANDROID_HOME", sdk(lint).toString());
    try {
      builder
          .redirectErrorStream(true)
          .redirectOutput(ProcessBuilder.Redirect.DISCARD)
          .start()
          .waitFor();
    } catch (final InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
    return Files.exists(report) ? reported(report, target) : List.of();
  }

  private static Path sdk(final Path lint) {
    Path sdk = lint;
    for (int level = 0; level < SDK_LEVELS_UP; level++) {
      sdk = sdk.getParent();
    }
    return sdk;
  }

  private static List<Integer> reported(final Path report, final String target) throws IOException {
    final List<Integer> lines = new ArrayList<>();
    final String name = target.substring(target.lastIndexOf('/') + 1);
    final Element root = ParityXml.parse(report).getDocumentElement();
    for (final Element issue : ParityXml.elements(root, "issue")) {
      final Element[] locations = ParityXml.elements(issue, "location");
      if (locations.length > 0 && locations[0].getAttribute("file").endsWith(name)) {
        lines.add(ParityXml.number(locations[0], "line"));
      }
    }
    lines.sort(Integer::compare);
    return lines;
  }
}
