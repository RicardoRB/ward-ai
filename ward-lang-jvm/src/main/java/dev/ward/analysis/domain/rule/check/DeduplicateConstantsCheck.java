package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a string literal that equals the value of a {@code static final String} constant of the
 * same class, as Error Prone's DeduplicateConstants does: the constant should be used instead. The
 * literal inside the constant's own initializer, and one in an annotation, are not reported.
 */
public final class DeduplicateConstantsCheck implements UnitCheck {

  private static final String EXPRESSION = "expression";
  private static final String ANNOTATION = "annotation";
  private static final String QUOTE = "\"";
  private static final int EMPTY_LITERAL = 2;

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the literal; {@code {value}} is the constant name
   */
  public DeduplicateConstantsCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of(MemberDeclarations.CLASS)).stream()
        .map(SyntaxWalk.Visit::node)
        .flatMap(clazz -> reportsOf(clazz).stream())
        .toList();
  }

  private List<RuleMatch> reportsOf(final SyntaxNode clazz) {
    final Map<String, String> constants = new HashMap<>();
    final Map<SyntaxNode, Boolean> initializers = new HashMap<>();
    for (final SyntaxNode member : ClassMembers.members(clazz)) {
      final SyntaxNode declaration = ClassMembers.declarationOf(member);
      if (isConstant(member, declaration)) {
        for (final SyntaxNode declarator : SyntaxWalk.descendants(declaration)) {
          recordConstant(declarator, constants, initializers);
        }
      }
    }
    return SyntaxWalk.visits(clazz, Set.of(EXPRESSION)).stream()
        .filter(visit -> literalOf(visit.node(), constants, initializers))
        .filter(visit -> visit.ancestors().stream().noneMatch(a -> ANNOTATION.equals(a.kind())))
        .map(
            visit ->
                CheckSupport.at(
                    visit.node().span(),
                    CheckSupport.fill(message, constants.get(SyntaxWalk.text(visit.node())), "")))
        .toList();
  }

  private static boolean isConstant(final SyntaxNode member, final SyntaxNode declaration) {
    final List<String> modifiers = MemberDeclarations.modifiers(member);
    return MemberDeclarations.FIELD.equals(declaration.kind())
        && modifiers.contains(MemberDeclarations.STATIC)
        && modifiers.contains(MemberDeclarations.FINAL)
        && ClassMembers.typeText(declaration).filter(type -> type.endsWith("String")).isPresent();
  }

  private static void recordConstant(
      final SyntaxNode declarator,
      final Map<String, String> constants,
      final Map<SyntaxNode, Boolean> initializers) {
    if (!"variableDeclarator".equals(declarator.kind())) {
      return;
    }
    final String text = SyntaxWalk.text(declarator);
    final int equals = text.indexOf('=');
    final String value = equals < 0 ? "" : text.substring(equals + 1);
    if (value.startsWith(QUOTE)
        && value.endsWith(QUOTE)
        && value.length() > EMPTY_LITERAL
        && value.indexOf('"', 1) == value.length() - 1) {
      constants.putIfAbsent(value, text.substring(0, equals));
      SyntaxWalk.descendants(declarator).stream()
          .filter(node -> EXPRESSION.equals(node.kind()))
          .forEach(node -> initializers.put(node, Boolean.TRUE));
    }
  }

  private static boolean literalOf(
      final SyntaxNode node,
      final Map<String, String> constants,
      final Map<SyntaxNode, Boolean> initializers) {
    return constants.containsKey(SyntaxWalk.text(node)) && !initializers.containsKey(node);
  }
}
