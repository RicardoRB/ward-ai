package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a local variable that is set to a class literal and never used: the store has no effect,
 * except that before Java 5 it would have run the static initializer of the class.
 */
public final class UnusedClassLiteralCheck implements UnitCheck {
  private static final Set<String> BODIES = Set.of("methodDeclaration", "constructorDeclaration");
  private static final String DECLARATOR = "variableDeclarator";
  private static final String DECLARATOR_ID = "variableDeclaratorId";
  private static final String IDENTIFIER = "identifier";
  private static final Pattern LITERAL = Pattern.compile("^(?:[\\w.]+\\.)?\\w+\\.class$");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the variable
   */
  public UnusedClassLiteralCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, BODIES)) {
      final List<SyntaxNode> names =
          SyntaxWalk.descendants(visit.node()).stream()
              .filter(node -> IDENTIFIER.equals(node.kind()))
              .toList();
      SyntaxWalk.descendants(visit.node()).stream()
          .filter(node -> DECLARATOR.equals(node.kind()) && storesClassLiteral(node))
          .filter(node -> uses(names, nameOf(node)) == 1)
          .forEach(node -> matches.add(CheckSupport.at(node.span(), message)));
    }
    return List.copyOf(matches);
  }

  private static boolean storesClassLiteral(final SyntaxNode declarator) {
    return declarator.children().stream()
        .filter(child -> "variableInitializer".equals(child.kind()))
        .anyMatch(init -> LITERAL.matcher(SyntaxWalk.text(init)).matches());
  }

  private static String nameOf(final SyntaxNode declarator) {
    return declarator.children().stream()
        .filter(child -> DECLARATOR_ID.equals(child.kind()))
        .findFirst()
        .map(SyntaxWalk::text)
        .orElse("");
  }

  private static long uses(final List<SyntaxNode> names, final String name) {
    return names.stream().filter(node -> name.equals(SyntaxWalk.text(node))).count();
  }
}
