package dev.ward.application.init;

import dev.ward.domain.init.InitGateOption;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The agent rule lines of a Swift project's gates, part of {@link AgentRulesTemplate}. The rules
 * name the suppressions agents must not add, and the coverage minimum the tests must meet.
 */
final class SwiftAgentRules {
  private static final Map<String, String> ROWS =
      Map.of(
          SwiftTools.SWIFT_FORMAT,
          "- swift-format: `{command}` must pass; format edited files, do not add"
              + " `// swift-format-ignore` comments.",
          SwiftTools.SWIFTLINT,
          "- SwiftLint: `{command}` must report no violations; fix the code, do not add"
              + " `// swiftlint:disable` comments or disable rules.",
          SwiftTools.SWIFT_BUILD,
          "- Swift build: `{command}` must succeed{warnings}.",
          SwiftTools.SWIFT_TEST,
          "- Swift tests: `{command}`{warnings}. New code needs tests that assert behavior.",
          SwiftTools.XCODEBUILD_TEST,
          "- Xcode tests: `{command}` must pass on the platform in `ward.toml`{warnings}.",
          SwiftTools.SWIFT_COVERAGE,
          "- Coverage: `{command}` must meet the {minimum}% minimum of line coverage; do not lower"
              + " it.",
          SwiftTools.HARMONIZE,
          "- Architecture rules (Harmonize): `{command}` must pass. Fix the import or type that"
              + " crosses a layer; do not edit `.ward/swift-architecture` or `.harmonize.yaml` to"
              + " weaken a rule.");

  private SwiftAgentRules() {}

  /** Fills a row's command, minimum and warnings from the gate it describes. */
  private static String row(final String template, final InitGateOption gate) {
    return template
        .replace("{command}", String.join(" ", gate.command()))
        .replace("{minimum}", gate.settings().getOrDefault("minimum", ""))
        .replace("{warnings}", warnings(gate.command()));
  }

  /** Names warnings as errors when the gate's command makes them so, which strict does. */
  private static String warnings(final List<String> command) {
    return command.contains("-warnings-as-errors")
            || command.contains("SWIFT_TREAT_WARNINGS_AS_ERRORS=YES")
        ? "; warnings are errors"
        : "";
  }

  /** Returns one line per selected Swift gate, naming its command, then the suppression rule. */
  static List<String> lines(final List<InitGateOption> gates) {
    final List<String> lines =
        gates.stream()
            .filter(gate -> ROWS.containsKey(gate.id()))
            .map(gate -> row(ROWS.get(gate.id()), gate))
            .toList();
    return lines.isEmpty()
        ? List.of()
        : Stream.concat(
                lines.stream(),
                Stream.of(
                    "- Never add `// swiftlint:disable` or `// swift-format-ignore`: suppressions"
                        + " are forbidden in Ward projects."))
            .toList();
  }
}
