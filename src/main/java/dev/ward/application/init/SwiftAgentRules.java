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
          "- Swift build: `{command}` must succeed.",
          SwiftTools.SWIFT_TEST,
          "- Swift tests: `{command}`. New code needs tests that assert behavior.",
          SwiftTools.XCODEBUILD_TEST,
          "- Xcode tests: `{command}` must pass on the platform in `ward.toml`.",
          SwiftTools.SWIFT_COVERAGE,
          "- Coverage: `{command}` must meet the "
              + SwiftGates.COVERAGE_MINIMUM
              + "% minimum of line coverage; do not lower it.");

  private SwiftAgentRules() {}

  /** Returns one line per selected Swift gate, naming its command, then the suppression rule. */
  static List<String> lines(final List<InitGateOption> gates) {
    final List<String> lines =
        gates.stream()
            .filter(gate -> ROWS.containsKey(gate.id()))
            .map(gate -> ROWS.get(gate.id()).replace("{command}", String.join(" ", gate.command())))
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
