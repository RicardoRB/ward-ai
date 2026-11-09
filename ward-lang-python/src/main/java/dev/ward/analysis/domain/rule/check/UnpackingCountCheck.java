package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports an assignment of a literal sequence or dictionary to a tuple of names whose counts differ:
 * {@code a, b = (1, 2, 3)} for a tuple or list display, and the number of keys for a dictionary
 * display. A starred target or value makes the count unknown, so such an assignment is not checked.
 */
public final class UnpackingCountCheck implements RuleCheck {

  /** The display that the assignment unpacks. */
  public enum Problem {
    /** A tuple or list display. */
    TUPLE,
    /** A dictionary display. */
    DICT
  }

  private static final String STARRED = "*";
  private static final String EQUALS = "=";
  private static final String DICT_OPEN = "{";
  private static final String SINGLE_ASSIGNMENT_CHARS = "!<>";
  private static final int MIN_TARGETS = 2;
  private static final String DISPLAY = "^[\\(\\[\\{][^\\(\\[\\{\\]\\)\\}]*[\\)\\]\\}]$";

  private final Problem problem;
  private final String message;

  /** The two sides of an assignment: the targets and the value. */
  private record Sides(String targets, String value) {}

  /**
   * Creates the check.
   *
   * @param problem the display to compare
   * @param message the message of each finding
   */
  public UnpackingCountCheck(final Problem problem, final String message) {
    this.problem = Objects.requireNonNull(problem, "problem");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("expr_stmt"))) {
      final String text = SyntaxWalk.text(visit.node()).strip();
      final Optional<Sides> sides = plainAssignment(text);
      if (sides.isPresent() && mismatch(sides.get().targets(), sides.get().value())) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  /** The targets and the value of an assignment with one plain equals sign and no star. */
  private static Optional<Sides> plainAssignment(final String text) {
    final int split = text.indexOf(EQUALS);
    if (split <= 0 || text.contains(STARRED) || text.indexOf(EQUALS, split + 1) >= 0
        || SINGLE_ASSIGNMENT_CHARS.indexOf(text.charAt(split - 1)) >= 0) {
      return Optional.empty();
    }
    return Optional.of(new Sides(text.substring(0, split), text.substring(split + 1)));
  }

  private boolean mismatch(final String targets, final String value) {
    final String trimmed = value.strip();
    final boolean dictionary = trimmed.startsWith(DICT_OPEN);
    final boolean wantsDictionary = problem == Problem.DICT;
    if (wantsDictionary != dictionary || !trimmed.matches(DISPLAY)) {
      return false;
    }
    final List<String> names = Elements.of(targets);
    final List<String> items = Elements.of(trimmed);
    return names.size() >= MIN_TARGETS && !items.isEmpty() && names.size() != items.size();
  }
}
