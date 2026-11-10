package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a constructor of a class that extends another class and neither calls {@code super(...)}
 * nor {@code this(...)}, as PMD's CallSuperInConstructor does.
 */
public final class ConstructorSuperCheck implements RuleCheck {

  private static final String CONSTRUCTOR = "constructorDeclaration";
  private static final String EXTENDS = "'extends'";
  private static final String INVOCATION = "^\\{(?:this|super)\\(.*";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public ConstructorSuperCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("classDeclaration"))) {
      if (JavaNodes.kid(visit.node(), EXTENDS).isPresent()) {
        JavaNodes.members(visit.node()).stream()
            .map(JavaNodes.Member::node)
            .filter(node -> CONSTRUCTOR.equals(node.kind()) && !invokes(node))
            .forEach(node -> matches.add(CheckSupport.at(node.span(), message)));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean invokes(final SyntaxNode constructor) {
    return JavaNodes.kid(constructor, "block")
        .map(block -> SyntaxWalk.text(block).matches(INVOCATION))
        .orElse(false);
  }
}
