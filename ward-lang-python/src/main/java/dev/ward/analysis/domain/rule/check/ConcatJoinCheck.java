package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a loop that builds a string by concatenation, like Pylint's {@code consider-using-join}:
 * {@code result = ''} followed by a loop whose only statement is {@code result += item}, where
 * {@code item} is the loop variable. The finding sits at the augmented assignment. Shortcut: the
 * initial value must be an empty string literal, and the loop must be a plain name loop.
 */
public final class ConcatJoinCheck implements RuleCheck {

  private static final Set<String> BLOCKS = Set.of("block", "file_input");
  private static final Pattern NAME = Pattern.compile("\\w+");
  private static final Pattern EMPTY_STRING = Pattern.compile("''|\"\"");
  private static final String AUGMENTED = "+=";
  private static final String ASSIGN = "=";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public ConcatJoinCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, BLOCKS)) {
      final List<SyntaxNode> statements =
          visit.node().children().stream().filter(child -> "stmt".equals(child.kind())).toList();
      for (int index = 0; index + 1 < statements.size(); index++) {
        final String result = emptyStringAssignment(statements.get(index));
        if (result != null) {
          concatenation(statements.get(index + 1), result).ifPresent(found::add);
        }
      }
    }
    return List.copyOf(found);
  }

  /** The name bound to an empty string literal in the statement, or null. */
  private static String emptyStringAssignment(final SyntaxNode statement) {
    final SyntaxNode assignment = firstExpression(statement);
    if (assignment == null
        || assignment.children().size() != 3
        || !ASSIGN.equals(SyntaxWalk.text(assignment.children().get(1)))
        || !EMPTY_STRING.matcher(SyntaxWalk.text(assignment.children().get(2))).matches()
        || !NAME.matcher(SyntaxWalk.text(assignment.children().get(0))).matches()) {
      return null;
    }
    return SyntaxWalk.text(assignment.children().get(0));
  }

  /** The finding of a for statement that concatenates {@code result} with its loop variable. */
  private java.util.Optional<RuleMatch> concatenation(
      final SyntaxNode statement, final String result) {
    final SyntaxNode loop = firstChild(statement, "for_stmt");
    if (loop == null) {
      return java.util.Optional.empty();
    }
    final String target = SyntaxWalk.text(loop.children().get(1));
    final SyntaxNode body =
        loop.children().stream().filter(c -> "block".equals(c.kind())).findFirst().orElse(null);
    if (body == null
        || body.children().stream().filter(c -> "stmt".equals(c.kind())).count() != 1) {
      return java.util.Optional.empty();
    }
    final SyntaxNode augmented = firstExpression(body);
    if (augmented == null
        || augmented.children().size() != 3
        || !AUGMENTED.equals(SyntaxWalk.text(augmented.children().get(1)))
        || !result.equals(SyntaxWalk.text(augmented.children().get(0)))
        || !target.equals(SyntaxWalk.text(augmented.children().get(2)))) {
      return java.util.Optional.empty();
    }
    return java.util.Optional.of(new RuleMatch(augmented.span(), message));
  }

  /** The first expression statement under a statement node. */
  private static SyntaxNode firstExpression(final SyntaxNode statement) {
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(statement, Set.of("expr_stmt"))) {
      return visit.node();
    }
    return null;
  }

  /** The first direct-or-wrapped child of the given kind under a statement node. */
  private static SyntaxNode firstChild(final SyntaxNode statement, final String kind) {
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(statement, Set.of(kind))) {
      return visit.node();
    }
    return null;
  }
}
