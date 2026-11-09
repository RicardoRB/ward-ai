package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports each function of a class body that has the name of an earlier function, when another
 * function was declared between them, so that overloads are declared together, as Checkstyle's
 * OverloadMethodsDeclarationOrder requires.
 */
public final class KotlinOverloadOrderCheck implements UnitCheck {

  private static final String CLASS_BODY = "classBody";
  private static final String MEMBER = "classMemberDeclaration";
  private static final String FUNCTION = "functionDeclaration";
  private static final String NAME = "identifier";
  private static final String IDENTIFIER = "Identifier";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at each function that is not next to its overloads
   */
  public KotlinOverloadOrderCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(CLASS_BODY))) {
      checkBody(visit.node(), matches);
    }
    return List.copyOf(matches);
  }

  private void checkBody(final SyntaxNode body, final List<RuleMatch> matches) {
    final Set<String> seen = new HashSet<>();
    String previous = null;
    for (final SyntaxNode member : KotlinDocs.childrenOf(body, MEMBER)) {
      final Optional<SyntaxNode> function = functionOf(member);
      if (function.isEmpty()) {
        continue;
      }
      final String name = nameOf(function.get());
      if (seen.contains(name) && !name.equals(previous)) {
        matches.add(CheckSupport.at(function.get().span(), message));
      }
      seen.add(name);
      previous = name;
    }
  }

  private static Optional<SyntaxNode> functionOf(final SyntaxNode member) {
    return member.children().stream().filter(child -> FUNCTION.equals(child.kind())).findFirst();
  }

  private static String nameOf(final SyntaxNode function) {
    return KotlinDocs.childrenOf(function, NAME).stream()
        .flatMap(name -> SyntaxWalk.leaves(name).stream())
        .filter(leaf -> IDENTIFIER.equals(leaf.kind()))
        .findFirst()
        .map(KotlinDocs::text)
        .orElse("");
  }
}
