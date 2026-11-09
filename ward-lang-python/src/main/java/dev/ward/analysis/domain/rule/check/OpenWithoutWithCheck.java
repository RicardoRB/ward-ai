package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a call of the builtin {@code open()} that is not the item of a {@code with} statement,
 * like Pylint's {@code consider-using-with}. A call returned, passed to {@code enter_context}, or
 * made in {@code __enter__} or a {@code contextmanager} function is not reported. Shortcut: only
 * {@code open} is checked, not the other resource-returning calls Pylint lists.
 */
public final class OpenWithoutWithCheck implements RuleCheck {

  private static final Set<String> CALLS = Set.of("atom_expr");
  private static final Pattern OPEN = Pattern.compile("^open\\(.*");
  private static final Set<String> WITH_ITEMS = Set.of("with_item", "with_stmt");
  private static final String RETURN = "return_stmt";
  private static final String FUNCTION = "funcdef";
  private static final String DECORATED = "decorated";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public OpenWithoutWithCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, CALLS)) {
      if (OPEN.matcher(SyntaxWalk.text(visit.node())).matches() && !managed(visit.ancestors())) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  /**
   * Whether the call is in a with item, returned, handed to enter_context, or a context manager.
   */
  private static boolean managed(final List<SyntaxNode> ancestors) {
    for (final SyntaxNode ancestor : ancestors) {
      if (WITH_ITEMS.contains(ancestor.kind()) || RETURN.equals(ancestor.kind())) {
        return true;
      }
      if ((FUNCTION.equals(ancestor.kind()) && contextFunction(ancestor))
          || (DECORATED.equals(ancestor.kind()) && contextDecorated(ancestor))) {
        return true;
      }
    }
    return false;
  }

  /** A decorated definition that is a context manager. */
  private static boolean contextDecorated(final SyntaxNode decorated) {
    final String text = SyntaxWalk.text(decorated);
    return text.contains("@contextmanager") || text.contains("@contextlib.contextmanager");
  }

  /** A function named __enter__ or decorated as a context manager. */
  private static boolean contextFunction(final SyntaxNode function) {
    return function.children().stream()
        .filter(child -> "name".equals(child.kind()) || "NAME".equals(child.kind()))
        .findFirst()
        .map(SyntaxWalk::text)
        .filter("__enter__"::equals)
        .isPresent();
  }
}
