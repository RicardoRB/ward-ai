package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a private field that holds a lambda or a method reference and is read at most once in the
 * file. Such a constant only hides the lambda from the one place that uses it; the lambda belongs
 * there. A field that is read more often, or that is not private, is left alone.
 */
public final class PrivateLambdaFieldCheck implements RuleCheck {

  private static final String FIELD = "fieldDeclaration";
  private static final String MEMBER = "classBodyDeclaration";
  private static final String IDENTIFIER = "identifier";
  private static final String DECLARATOR = "variableDeclarator";
  private static final int DECLARATION_AND_ONE_USE = 2;
  private static final Pattern LAMBDA =
      Pattern.compile("^(?:\\w+|\\([^()]*\\))->|^[\\w.<>]+::\\w+$");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the field
   */
  public PrivateLambdaFieldCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(FIELD))) {
      final boolean isPrivate =
          visit.ancestors().stream()
              .filter(node -> MEMBER.equals(node.kind()))
              .reduce((first, second) -> second)
              .filter(member -> MemberDeclarations.modifiers(member).contains("private"))
              .isPresent();
      final List<SyntaxNode> declarators =
          SyntaxWalk.descendants(visit.node()).stream()
              .filter(node -> DECLARATOR.equals(node.kind()))
              .toList();
      if (isPrivate
          && declarators.size() == 1
          && holdsLambda(declarators.get(0))
          && occurrences(root, nameOf(declarators.get(0))) <= DECLARATION_AND_ONE_USE) {
        matches.add(CheckSupport.at(visit.node().span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean holdsLambda(final SyntaxNode declarator) {
    final String text = SyntaxWalk.text(declarator);
    final int equals = text.indexOf('=');
    return equals >= 0 && LAMBDA.matcher(text.substring(equals + 1)).find();
  }

  private static String nameOf(final SyntaxNode declarator) {
    return SyntaxWalk.descendants(declarator).stream()
        .filter(node -> IDENTIFIER.equals(node.kind()))
        .findFirst()
        .map(SyntaxWalk::text)
        .orElse("");
  }

  private static long occurrences(final SyntaxNode root, final String name) {
    return SyntaxWalk.visits(root, Set.of(IDENTIFIER)).stream()
        .filter(visit -> name.equals(SyntaxWalk.text(visit.node())))
        .count();
  }
}
