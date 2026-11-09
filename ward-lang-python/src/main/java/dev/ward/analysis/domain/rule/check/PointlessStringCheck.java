package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a string statement that is not a docstring, like pylint {@code
 * pointless-string-statement}. The first statement of a module, class or function is its docstring.
 * A string right after an assignment is an attribute docstring in a module, a class or an {@code
 * __init__}, and is not reported there.
 */
public final class PointlessStringCheck implements RuleCheck {

  private static final Set<String> BLOCKS = Set.of("block");
  private static final Pattern STRING =
      Pattern.compile(
          "(?s)^(?:[rRbBuUfF]{0,2}(?:'''[\\s\\S]*?'''|\"\"\"[\\s\\S]*?\"\"\"|'[^'\\n]*'|\"[^\"\\n]*\"))+$");
  private static final Pattern ASSIGNMENT =
      Pattern.compile("(?s)^[\\w.\\[\\]]+(?::[^=]*)?=(?!=)[\\s\\S]*$");
  private static final String DEF = "def";
  private static final String INIT = "__init__";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public PointlessStringCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, BLOCKS)) {
      check(StatementShapes.statements(visit.node()), owner(visit.ancestors()), found);
    }
    check(StatementShapes.statements(root), "module", found);
    return List.copyOf(found);
  }

  private static void check(
      final List<SyntaxNode> statements, final String owner, final List<RuleMatch> found) {
    for (int index = 1; index < statements.size(); index++) {
      final SyntaxNode statement = statements.get(index);
      if (isString(SyntaxWalk.text(statement)) && !afterAssignmentIn(statements, index, owner)) {
        found.add(new RuleMatch(statement.span(), "pointless string statement"));
      }
    }
  }

  private static boolean afterAssignmentIn(
      final List<SyntaxNode> statements, final int index, final String owner) {
    final boolean attributeScope =
        "module".equals(owner) || "classdef".equals(owner) || INIT.equals(owner);
    return attributeScope
        && ASSIGNMENT.matcher(SyntaxWalk.text(statements.get(index - 1))).matches();
  }

  /** The owner of a block: "module", "classdef", or the name of the nearest function. */
  private static String owner(final List<SyntaxNode> ancestors) {
    for (int index = ancestors.size() - 1; index >= 0; index--) {
      final SyntaxNode node = ancestors.get(index);
      if ("classdef".equals(node.kind())) {
        return "classdef";
      }
      if ("funcdef".equals(node.kind())) {
        return functionName(node);
      }
    }
    return "module";
  }

  private static String functionName(final SyntaxNode function) {
    final List<SyntaxNode> children = function.children();
    for (int index = 0; index + 1 < children.size(); index++) {
      if (DEF.equals(SyntaxWalk.text(children.get(index)))) {
        return SyntaxWalk.text(children.get(index + 1));
      }
    }
    return "";
  }

  private static boolean isString(final String text) {
    return STRING.matcher(text.strip()).matches();
  }
}
