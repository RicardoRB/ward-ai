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
 * Reports each parameter and local property of a function that has the name of a property of its
 * class, as Checkstyle's HiddenField does. Constructor parameters and setter parameters are not
 * checked, as {@code config/checkstyle.xml} configures the rule.
 */
public final class KotlinHiddenFieldCheck implements UnitCheck {

  private static final String CLASS_BODY = "classBody";
  private static final String MEMBER = "classMemberDeclaration";
  private static final String FUNCTION = "functionDeclaration";
  private static final String PROPERTY = "propertyDeclaration";
  private static final String VARIABLE = "variableDeclaration";
  private static final String PARAMETERS = "functionValueParameters";
  private static final String PARAMETER = "functionValueParameter";
  private static final String IDENTIFIER = "Identifier";
  private static final String VALUE = "{value}";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding; {@code {value}} stands for the name
   */
  public KotlinHiddenFieldCheck(final String message) {
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
    final Set<String> properties = properties(body);
    if (properties.isEmpty()) {
      return;
    }
    for (final SyntaxNode member : KotlinDocs.childrenOf(body, MEMBER)) {
      for (final SyntaxNode function : KotlinDocs.childrenOf(member, FUNCTION)) {
        for (final SyntaxNode name : namesDeclaredIn(function)) {
          if (properties.contains(KotlinDocs.text(name))) {
            matches.add(
                CheckSupport.at(name.span(), message.replace(VALUE, KotlinDocs.text(name))));
          }
        }
      }
    }
  }

  /** Returns the names of the properties that the members of {@code body} declare. */
  private static Set<String> properties(final SyntaxNode body) {
    final Set<String> names = new HashSet<>();
    for (final SyntaxNode member : KotlinDocs.childrenOf(body, MEMBER)) {
      for (final SyntaxNode property : KotlinDocs.childrenOf(member, PROPERTY)) {
        nameOf(property).ifPresent(names::add);
      }
    }
    return names;
  }

  /** Returns the parameter names and the local property names of a function, as leaves. */
  private static List<SyntaxNode> namesDeclaredIn(final SyntaxNode function) {
    final List<SyntaxNode> names = new ArrayList<>();
    for (final SyntaxNode list : KotlinDocs.childrenOf(function, PARAMETERS)) {
      for (final SyntaxNode parameter : KotlinDocs.childrenOf(list, PARAMETER)) {
        firstIdentifier(parameter).ifPresent(names::add);
      }
    }
    for (final SyntaxNode node : SyntaxWalk.descendants(function)) {
      if (PROPERTY.equals(node.kind())) {
        firstIdentifier(node).ifPresent(names::add);
      }
    }
    return names;
  }

  private static Optional<String> nameOf(final SyntaxNode property) {
    return KotlinDocs.childrenOf(property, VARIABLE).stream()
        .flatMap(variable -> SyntaxWalk.leaves(variable).stream())
        .filter(leaf -> IDENTIFIER.equals(leaf.kind()))
        .findFirst()
        .map(KotlinDocs::text);
  }

  private static Optional<SyntaxNode> firstIdentifier(final SyntaxNode node) {
    return SyntaxWalk.leaves(node).stream()
        .filter(leaf -> IDENTIFIER.equals(leaf.kind()))
        .findFirst();
  }
}
