package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a local variable that is initialized where it is declared, or declared after another
 * statement of its block, as PMD's LocalVariableDeclarationShouldBeAtStartOfBlock does. A variable
 * whose type is {@code var} must be initialized and is skipped.
 */
public final class LocalDeclarationOrderCheck implements RuleCheck {

  private static final String VAR = "'var'";
  private static final String INITIALIZER = "'='";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public LocalDeclarationOrderCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("block"))) {
      check(JavaNodes.statements(visit.node()), matches);
    }
    return List.copyOf(matches);
  }

  private void check(final List<SyntaxNode> statements, final List<RuleMatch> matches) {
    boolean otherSeen = false;
    for (final SyntaxNode statement : statements) {
      final SyntaxNode declaration = declaration(statement);
      if (declaration == null) {
        otherSeen = true;
      } else if (!typeIsVar(declaration) && (otherSeen || initialized(declaration))) {
        matches.add(CheckSupport.at(declaration.span(), message));
      }
    }
  }

  private static SyntaxNode declaration(final SyntaxNode statement) {
    return JavaNodes.kid(statement, JavaNodes.LOCAL_VARIABLE).orElse(null);
  }

  private static boolean typeIsVar(final SyntaxNode declaration) {
    return JavaNodes.kid(declaration, VAR).isPresent();
  }

  private static boolean initialized(final SyntaxNode declaration) {
    return SyntaxWalk.descendants(declaration).stream()
        .anyMatch(node -> INITIALIZER.equals(node.kind()));
  }
}
