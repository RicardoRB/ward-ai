package dev.ward.infrastructure.rule.catalog;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * The comparison of one Ward rule with one rule of a tool.
 *
 * @param rule the fixture name of the Ward rule
 * @param tool the tool name, or {@code -} for a rule without a counterpart
 * @param id the tool's rule id
 * @param outcome how the tool's positions compare with the fixture's {@code expected.txt}
 * @param note the reason, for every outcome but an agreement
 */
record ParityRow(String rule, String tool, String id, String outcome, String note) {

  static final String AGREE = "agree";
  static final String AGREE_LINES = "agree-lines";
  static final String APPROXIMATION = "approximation";
  static final String TOOL_SILENT = "tool-silent";
  static final String TOOL_EXTRA = "tool-extra";
  static final String POSITION = "position";
  static final String ANCHOR = "anchor-differs";
  static final String NOT_COMPARABLE = "not-comparable";
  static final String NO_COUNTERPART = "no-counterpart";
  private static final String SILENT =
      "the tool reports nothing on the fixture, which may need its configuration, resolved types or"
          + " a bytecode shape";
  private static final String VIOLATING = "/violating";
  private static final String PASSING = "/passing";

  /** The row of {@code target} against {@code id} of {@code tool}, given what the tool reported. */
  static ParityRow judge(
      final ParityTarget target,
      final ParityTool tool,
      final String id,
      final ParityRun run,
      final Map<String, String> approximations) {
    final String violatingFixture = target.name() + VIOLATING;
    final String passingFixture = target.name() + PASSING;
    final Optional<String> reason =
        run.reason(violatingFixture, id).or(() -> run.reason(passingFixture, id));
    if (reason.isPresent()) {
      return new ParityRow(target.name(), tool.name(), id, NOT_COMPARABLE, reason.get());
    }
    final Set<String> violating = run.positions(violatingFixture, id);
    final Set<String> passing = run.positions(passingFixture, id);
    final Set<String> expected = new TreeSet<>(target.expected());
    final boolean exact = tool.reportsColumns() && violating.equals(expected);
    if (passing.isEmpty() && exact) {
      return new ParityRow(target.name(), tool.name(), id, AGREE, "");
    }
    if (passing.isEmpty() && lines(violating).equals(lines(expected))) {
      return new ParityRow(target.name(), tool.name(), id, AGREE_LINES, "");
    }
    final String outcome = classify(violating, passing, lines(expected));
    final String documented = approximations.get(String.join("\t", target.name(), tool.name(), id));
    final String detail =
        "tool lines "
            + lines(violating)
            + ", on passing "
            + lines(passing)
            + ", fixture "
            + lines(expected);
    if (documented != null && !TOOL_SILENT.equals(outcome)) {
      return new ParityRow(target.name(), tool.name(), id, APPROXIMATION, documented);
    }
    return new ParityRow(
        target.name(), tool.name(), id, outcome, TOOL_SILENT.equals(outcome) ? SILENT : detail);
  }

  private static String classify(
      final Set<String> violating, final Set<String> passing, final Set<String> expectedLines) {
    if (violating.isEmpty() && passing.isEmpty()) {
      return TOOL_SILENT;
    }
    if (passing.isEmpty() && lines(violating).size() == expectedLines.size()) {
      return ANCHOR;
    }
    return lines(violating).equals(expectedLines) ? TOOL_EXTRA : POSITION;
  }

  private static Set<String> lines(final Set<String> positions) {
    final Set<String> lines = new TreeSet<>();
    for (final String position : positions) {
      lines.add(position.substring(0, position.indexOf(':')));
    }
    return lines;
  }

  /** The tab-separated line of the committed report. */
  String line() {
    return String.join("\t", rule, tool, id, outcome, note);
  }
}
