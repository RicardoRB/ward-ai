package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports each {@code var} whose first use comes more than the allowed number of statements after
 * its declaration in the same block, as Checkstyle's VariableDeclarationUsageDistance does with its
 * defaults. A {@code val} is final and therefore not checked. A first use that is only inside a
 * nested block is not reported, as with Checkstyle's {@code validateBetweenScopes=false}.
 */
public final class KotlinUsageDistanceCheck implements UnitCheck {

  /** The statements a variable may be away from its first use, as Checkstyle's default. */
  static final int ALLOWED_DISTANCE = 3;

  private static final Set<String> NESTED = Set.of("block", "classBody", "lambdaLiteral");
  private static final String STATEMENTS = "statements";
  private static final String STATEMENT = "statement";
  private static final String DECLARATION = "declaration";
  private static final String PROPERTY = "propertyDeclaration";
  private static final String VARIABLE = "variableDeclaration";
  private static final String VAR = "var";
  private static final String IDENTIFIER = "Identifier";
  private static final String VALUE = "{value}";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding; {@code {value}} stands for the variable name
   */
  public KotlinUsageDistanceCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(STATEMENTS))) {
      final List<SyntaxNode> statements = KotlinDocs.childrenOf(visit.node(), STATEMENT);
      for (int index = 0; index < statements.size(); index++) {
        checkDeclaration(statements, index, matches);
      }
    }
    return List.copyOf(matches);
  }

  private void checkDeclaration(
      final List<SyntaxNode> statements, final int index, final List<RuleMatch> matches) {
    variableOf(statements.get(index))
        .ifPresent(
            variable -> {
              final String name = KotlinDocs.text(variable);
              if (distanceToUse(statements, index, name) > ALLOWED_DISTANCE) {
                matches.add(CheckSupport.at(variable.span(), message.replace(VALUE, name)));
              }
            });
  }

  /** Returns the name leaf of a {@code var} that the statement declares, if it declares one. */
  private static Optional<SyntaxNode> variableOf(final SyntaxNode statement) {
    final Optional<SyntaxNode> property =
        KotlinDocs.childrenOf(statement, DECLARATION).stream()
            .flatMap(declaration -> KotlinDocs.childrenOf(declaration, PROPERTY).stream())
            .findFirst();
    if (property.isEmpty() || !isVar(property.get())) {
      return Optional.empty();
    }
    return KotlinDocs.childrenOf(property.get(), VARIABLE).stream()
        .flatMap(variable -> SyntaxWalk.leaves(variable).stream())
        .filter(leaf -> IDENTIFIER.equals(leaf.kind()))
        .findFirst();
  }

  private static boolean isVar(final SyntaxNode property) {
    return property.children().stream().anyMatch(child -> VAR.equals(KotlinDocs.text(child)));
  }

  private static int distanceToUse(
      final List<SyntaxNode> statements, final int declared, final String name) {
    for (int index = declared + 1; index < statements.size(); index++) {
      final SyntaxNode statement = statements.get(index);
      if (usesOutsideNested(statement, name)) {
        return index - declared - 1;
      }
      if (uses(statement, name)) {
        return 0;
      }
    }
    return 0;
  }

  private static boolean uses(final SyntaxNode statement, final String name) {
    return SyntaxWalk.leaves(statement).stream()
        .anyMatch(leaf -> name.equals(KotlinDocs.text(leaf)));
  }

  private static boolean usesOutsideNested(final SyntaxNode node, final String name) {
    if (NESTED.contains(node.kind())) {
      return false;
    }
    if (node.children().isEmpty()) {
      return name.equals(KotlinDocs.text(node));
    }
    return node.children().stream().anyMatch(child -> usesOutsideNested(child, name));
  }
}
