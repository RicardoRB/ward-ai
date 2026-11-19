package dev.ward.application.init.rust;

import dev.ward.domain.init.InitGateOption;
import java.util.List;
import java.util.Map;

/** The agent rule lines of a Rust project's gates, part of {@code AgentRulesTemplate}. */
final class RustAgentRules {
  private static final Map<String, String> ROWS =
      Map.of(
          RustTools.CARGO_CLIPPY,
          "- Clippy: `{command}` must report no warnings. Fix the code; do not add `#[allow]`"
              + " attributes.",
          RustTools.CARGO_TEST,
          "- Tests: `{command}`. New code needs tests that assert behavior.",
          RustTools.COVERAGE,
          "- Coverage: `{command}` writes the lcov report that Ward checks against the minimum in"
              + " `ward.toml`. Add tests for new code; do not lower the minimum.",
          RustTools.CARGO_MUTANTS,
          "- Mutation testing: `{command}` must leave no surviving mutants above the allowed share."
              + " Make the tests assert results, not only run the code."
      );

  private RustAgentRules() {}

  /** Returns one line per selected Rust gate, naming its command. */
  static List<String> lines(final List<InitGateOption> gates) {
    return gates.stream()
        .filter(gate -> ROWS.containsKey(gate.id()))
        .map(gate -> ROWS.get(gate.id()).replace("{command}", String.join(" ", gate.command())))
        .toList();
  }
}
