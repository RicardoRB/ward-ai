package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a type written as {@code T.Name} where {@code T} is a type parameter declared in the
 * file. A type parameter cannot qualify a nested type; the name is meant to be written alone.
 */
public final class TypeParameterQualifierCheck implements RuleCheck {

  private static final String TYPE_PARAMETER = "typeParameter";
  private static final String CLASS_TYPE = "classOrInterfaceType";
  private static final String IDENTIFIER = "identifier";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the type
   */
  public TypeParameterQualifierCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<String> names =
        SyntaxWalk.visits(root, Set.of(TYPE_PARAMETER)).stream()
            .map(
                visit ->
                    visit.node().children().stream()
                        .filter(child -> IDENTIFIER.equals(child.kind()))
                        .findFirst()
                        .map(SyntaxWalk::text)
                        .orElse(""))
            .filter(name -> !name.isEmpty())
            .distinct()
            .map(Pattern::quote)
            .toList();
    if (names.isEmpty()) {
      return List.of();
    }
    final Pattern qualified = Pattern.compile("^(?:" + String.join("|", names) + ")\\.\\w");
    return SyntaxWalk.visits(root, Set.of(CLASS_TYPE)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(node -> qualified.matcher(SyntaxWalk.text(node)).find())
        .map(node -> CheckSupport.at(node.span(), message))
        .toList();
  }
}
