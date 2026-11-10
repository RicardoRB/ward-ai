package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a function declaration, or with {@code both} a {@code var} declaration, outside the root
 * of a program or function body, like ESLint {@code no-inner-declarations}. In strict code a
 * block-scoped function declaration is allowed unless {@code blockScopedFunctions} is disallowed.
 */
public final class NodeInnerDeclarationsCheck implements RuleCheck {

  private static final String PROGRAM = "program";
  private static final String FUNCTION_BODY = "functionBody";
  private static final String SOURCE_ELEMENTS = "sourceElements";
  private static final String FUNCTION = "functionDeclaration";
  private static final String VARIABLE_LIST = "variableDeclarationList";
  private static final String VARIABLE_STATEMENT = "variableStatement";
  private static final String VAR_MODIFIER = "varModifier";
  private static final String VAR = "var";
  private static final Set<String> ROOT_OWNERS = Set.of(PROGRAM, FUNCTION_BODY);
  private static final Set<String> ROOT_WRAPPERS =
      Set.of("sourceElement", "statement", "exportStatement", "declaration", VARIABLE_STATEMENT);

  private final boolean both;
  private final boolean allowBlockScopedFunctions;
  private final String message;

  /**
   * Creates the check.
   *
   * @param both whether {@code var} declarations are checked too
   * @param allowBlockScopedFunctions whether a function declaration in a block is allowed
   * @param message the message of each finding
   */
  public NodeInnerDeclarationsCheck(
      final boolean both, final boolean allowBlockScopedFunctions, final String message) {
    this.both = both;
    this.allowBlockScopedFunctions = allowBlockScopedFunctions;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    walk(root, "", false, matches);
    return List.copyOf(matches);
  }

  /** Walks the tree; {@code root} tells whether a node is at the root of its statement list. */
  private void walk(
      final SyntaxNode node,
      final String parentKind,
      final boolean root,
      final List<RuleMatch> matches) {
    if (SOURCE_ELEMENTS.equals(node.kind())) {
      final boolean childRoot = ROOT_OWNERS.contains(parentKind);
      node.children().forEach(child -> walk(child, node.kind(), childRoot, matches));
      return;
    }
    if (reported(node, parentKind, root)) {
      matches.add(new RuleMatch(node.span(), message));
    }
    final boolean childRoot = root && ROOT_WRAPPERS.contains(node.kind());
    node.children().forEach(child -> walk(child, node.kind(), childRoot, matches));
  }

  /**
   * A declaration outside the root. A {@code var} statement holds its modifier directly in one
   * grammar and in its list in another, so a list inside a statement is not checked again.
   */
  private boolean reported(final SyntaxNode node, final String parentKind, final boolean root) {
    if (root) {
      return false;
    }
    if (FUNCTION.equals(node.kind())) {
      return !allowBlockScopedFunctions;
    }
    if (!both) {
      return false;
    }
    if (VARIABLE_STATEMENT.equals(node.kind())) {
      return isVar(node);
    }
    return VARIABLE_LIST.equals(node.kind())
        && !VARIABLE_STATEMENT.equals(parentKind)
        && isVar(node);
  }

  /** Whether a statement or list is a {@code var} one: its modifier is its own or its list's. */
  private static boolean isVar(final SyntaxNode node) {
    return hasVarModifier(node)
        || node.children().stream()
            .anyMatch(child -> VARIABLE_LIST.equals(child.kind()) && hasVarModifier(child));
  }

  private static boolean hasVarModifier(final SyntaxNode node) {
    return node.children().stream()
        .anyMatch(child -> VAR_MODIFIER.equals(child.kind()) && VAR.equals(LeafText.of(child)));
  }
}
