package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a property of an {@code object} or a companion object whose name does not match the
 * pattern of its kind, as detekt's {@code object-property-naming} does: constants ({@code const
 * val}), private properties and other properties each have their own pattern.
 */
public final class KotlinObjectPropertyCheck implements RuleCheck {

  private static final Set<String> OWNERS = Set.of("objectDeclaration", "companionObject");
  private static final String CLASS_BODY = "classBody";
  private static final String MEMBER = "classMemberDeclaration";
  private static final String PROPERTY = "propertyDeclaration";
  private static final String VARIABLE = "variableDeclaration";
  private static final String SIMPLE = "simpleIdentifier";
  private static final String MODIFIERS = "modifierList";
  private static final String CONST = "const";
  private static final String PRIVATE = "private";

  private final Pattern constant;
  private final Pattern propertyPattern;
  private final Pattern privatePattern;
  private final String message;

  /**
   * Creates the check.
   *
   * @param constant the pattern of constant names
   * @param propertyPattern the pattern of public and internal property names
   * @param privatePattern the pattern of private property names
   * @param message the message; {@code {value}} is the name
   */
  public KotlinObjectPropertyCheck(
      final String constant,
      final String propertyPattern,
      final String privatePattern,
      final String message) {
    this.constant = Pattern.compile(Objects.requireNonNull(constant, "constant"));
    this.propertyPattern =
        Pattern.compile(Objects.requireNonNull(propertyPattern, "propertyPattern"));
    this.privatePattern = Pattern.compile(Objects.requireNonNull(privatePattern, "privatePattern"));
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, OWNERS)) {
      for (final SyntaxNode member : members(visit.node())) {
        checkProperty(member, matches);
      }
    }
    return List.copyOf(matches);
  }

  private void checkProperty(final SyntaxNode member, final List<RuleMatch> out) {
    final Optional<SyntaxNode> property = child(member, PROPERTY);
    if (property.isEmpty()) {
      return;
    }
    final Optional<SyntaxNode> name =
        child(property.get(), VARIABLE).flatMap(v -> child(v, SIMPLE));
    final Optional<SyntaxNode> modifiers = child(property.get(), MODIFIERS);
    if (name.isEmpty()) {
      return;
    }
    final String text = SyntaxWalk.text(name.get());
    if (!patternFor(modifiers).matcher(text).matches()) {
      out.add(CheckSupport.at(name.get().span(), CheckSupport.fill(message, text, "")));
    }
  }

  private Pattern patternFor(final Optional<SyntaxNode> modifiers) {
    final List<String> words = modifiers.map(KotlinObjectPropertyCheck::keywords).orElse(List.of());
    if (words.contains(CONST)) {
      return constant;
    }
    return words.contains(PRIVATE) ? privatePattern : propertyPattern;
  }

  private static List<String> keywords(final SyntaxNode modifiers) {
    return SyntaxWalk.leaves(modifiers).stream().map(leaf -> leaf.fields().get("text")).toList();
  }

  private static List<SyntaxNode> members(final SyntaxNode owner) {
    return child(owner, CLASS_BODY)
        .map(body -> body.children().stream().filter(c -> MEMBER.equals(c.kind())).toList())
        .orElse(List.of());
  }

  private static Optional<SyntaxNode> child(final SyntaxNode node, final String kind) {
    return node.children().stream().filter(c -> kind.equals(c.kind())).findFirst();
  }
}
