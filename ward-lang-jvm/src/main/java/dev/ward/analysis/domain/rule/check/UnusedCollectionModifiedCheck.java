package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports a local collection that is only ever modified in place and never read: every use of the
 * variable is a statement that calls a mutating method on it. The collection is built for nothing.
 */
public final class UnusedCollectionModifiedCheck implements RuleCheck {

  private static final String LOCAL = "localVariableDeclaration";
  private static final String BLOCK_OWNER = "methodDeclaration";
  private static final String IDENTIFIER = "identifier";
  private static final String DECLARATOR_ID = "variableDeclaratorId";
  private static final String EXPRESSION = "expression";
  private static final String STATEMENT = "statement";
  private static final Pattern COLLECTION =
      Pattern.compile(
          "^(?:final)?(?:java\\.util\\.)?(?:List|ArrayList|LinkedList|Set|HashSet|LinkedHashSet|"
              + "TreeSet|Map|HashMap|LinkedHashMap|TreeMap|Collection|Deque|ArrayDeque|Queue)"
              + "(?:<.*>)?\\w+=new");
  private static final int NAME_AND_CALL = 2;
  private static final String MUTATORS =
      "add|addAll|put|putAll|remove|removeAll|clear|sort|removeIf|push|offer|addFirst|addLast";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the declaration
   */
  public UnusedCollectionModifiedCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit method : SyntaxWalk.visits(root, Set.of(BLOCK_OWNER))) {
      for (final SyntaxWalk.Visit local : SyntaxWalk.visits(method.node(), Set.of(LOCAL))) {
        final Matcher declared = COLLECTION.matcher(SyntaxWalk.text(local.node()));
        final String name = nameOf(local.node());
        if (declared.find() && !name.isEmpty() && onlyMutated(method.node(), name)) {
          matches.add(CheckSupport.at(local.node().span(), message));
        }
      }
    }
    return List.copyOf(matches);
  }

  private static String nameOf(final SyntaxNode local) {
    final List<SyntaxNode> ids =
        SyntaxWalk.descendants(local).stream()
            .filter(node -> DECLARATOR_ID.equals(node.kind()))
            .toList();
    return ids.size() == 1 ? SyntaxWalk.text(ids.get(0)) : "";
  }

  private static boolean onlyMutated(final SyntaxNode method, final String name) {
    final Pattern call = Pattern.compile("^" + Pattern.quote(name) + "\\.(?:" + MUTATORS + ")\\(");
    int mutations = 0;
    for (final SyntaxWalk.Visit use : SyntaxWalk.visits(method, Set.of(IDENTIFIER))) {
      if (!name.equals(SyntaxWalk.text(use.node())) || isDeclaration(use)) {
        continue;
      }
      if (!isMutationStatement(use.ancestors(), call)) {
        return false;
      }
      mutations++;
    }
    return mutations > 0;
  }

  private static boolean isDeclaration(final SyntaxWalk.Visit use) {
    final List<SyntaxNode> up = use.ancestors();
    return !up.isEmpty() && DECLARATOR_ID.equals(up.get(up.size() - 1).kind());
  }

  private static boolean isMutationStatement(final List<SyntaxNode> up, final Pattern call) {
    final List<SyntaxNode> expressions =
        up.stream().filter(node -> EXPRESSION.equals(node.kind())).toList();
    if (expressions.size() < NAME_AND_CALL) {
      return false;
    }
    final SyntaxNode invocation = expressions.get(expressions.size() - NAME_AND_CALL);
    final int at = up.indexOf(invocation);
    return call.matcher(SyntaxWalk.text(invocation)).find()
        && at > 0
        && STATEMENT.equals(up.get(at - 1).kind());
  }
}
