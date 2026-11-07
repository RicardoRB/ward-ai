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
 * Reports a class or enum that declares {@code equals(Object)} without {@code hashCode()}, or the
 * other way round. Only the methods declared directly in the type's body count; nested types and
 * method bodies are not searched.
 */
public final class EqualsHashCodeCheck implements UnitCheck {

  private static final String EQUALS = "equals";
  private static final String HASH_CODE = "hashCode";
  private static final String IDENTIFIER = "identifier";
  private static final String METHOD = "methodDeclaration";
  private static final Set<String> TYPES =
      Set.of("classDeclaration", "enumDeclaration", "recordDeclaration");
  private static final Set<String> MEMBER_CONTAINERS =
      Set.of("classBody", "classBodyDeclaration", "memberDeclaration", "enumBodyDeclarations");
  private static final Pattern OBJECT_PARAMETER =
      Pattern.compile("\\((?:final)?(?:java\\.lang\\.)?Object[\\w$]+\\)");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message at the method that is declared without its counterpart
   */
  public EqualsHashCodeCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, TYPES)) {
      final List<SyntaxNode> methods = new ArrayList<>();
      members(visit.node(), methods);
      final Optional<SyntaxNode> equals = find(methods, EQUALS, true);
      final Optional<SyntaxNode> hashCode = find(methods, HASH_CODE, false);
      equals
          .filter(m -> hashCode.isEmpty())
          .ifPresent(m -> matches.add(CheckSupport.at(m.span(), message)));
      hashCode
          .filter(m -> equals.isEmpty())
          .ifPresent(m -> matches.add(CheckSupport.at(m.span(), message)));
    }
    return List.copyOf(matches);
  }

  private static void members(final SyntaxNode node, final List<SyntaxNode> methods) {
    for (final SyntaxNode child : node.children()) {
      if (METHOD.equals(child.kind())) {
        methods.add(child);
      } else if (MEMBER_CONTAINERS.contains(child.kind())) {
        members(child, methods);
      }
    }
  }

  private static Optional<SyntaxNode> find(
      final List<SyntaxNode> methods, final String name, final boolean withObjectParameter) {
    return methods.stream()
        .filter(m -> name.equals(methodName(m)))
        .filter(m -> !withObjectParameter || OBJECT_PARAMETER.matcher(parameters(m)).matches())
        .filter(m -> withObjectParameter || "()".equals(parameters(m)))
        .findFirst();
  }

  private static String methodName(final SyntaxNode method) {
    return method.children().stream()
        .filter(c -> IDENTIFIER.equals(c.kind()))
        .map(SyntaxWalk::text)
        .findFirst()
        .orElse("");
  }

  private static String parameters(final SyntaxNode method) {
    return method.children().stream()
        .filter(c -> "formalParameters".equals(c.kind()))
        .map(SyntaxWalk::text)
        .findFirst()
        .orElse("");
  }
}
