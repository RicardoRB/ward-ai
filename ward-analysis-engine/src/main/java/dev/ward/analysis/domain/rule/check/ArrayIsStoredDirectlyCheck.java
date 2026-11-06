package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reports an array parameter stored directly into a field, so the caller keeps write access to the
 * field's array. A field is named bare or as {@code this.name}.
 */
public final class ArrayIsStoredDirectlyCheck implements UnitCheck {

  private static final String MEMBER_DECLARATION = "memberDeclaration";
  private static final String METHOD = "methodDeclaration";
  private static final String CONSTRUCTOR = "constructorDeclaration";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the array parameter
   */
  public ArrayIsStoredDirectlyCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("classBody"))) {
      final Set<String> fields = ClassFields.names(visit.node());
      for (final SyntaxNode member : JUnitTests.members(visit.node())) {
        for (final SyntaxNode body : callables(member)) {
          checkCallable(body, fields, matches);
        }
      }
    }
    return List.copyOf(matches);
  }

  private static List<SyntaxNode> callables(final SyntaxNode member) {
    return member.children().stream()
        .filter(child -> MEMBER_DECLARATION.equals(child.kind()))
        .flatMap(child -> child.children().stream())
        .filter(child -> METHOD.equals(child.kind()) || CONSTRUCTOR.equals(child.kind()))
        .toList();
  }

  private void checkCallable(
      final SyntaxNode callable, final Set<String> fields, final List<RuleMatch> matches) {
    final Set<String> arrays = arrayParameters(callable);
    for (final SyntaxNode assignment : Assignments.in(callable)) {
      final String value = Assignments.value(assignment);
      if (arrays.contains(value) && isField(Assignments.target(assignment), fields)) {
        matches.add(CheckSupport.at(assignment.span(), CheckSupport.fill(message, value, "")));
      }
    }
  }

  private static Set<String> arrayParameters(final SyntaxNode callable) {
    return SyntaxWalk.descendants(callable).stream()
        .filter(
            node -> "formalParameter".equals(node.kind()) && SyntaxWalk.text(node).contains("[]"))
        .flatMap(node -> node.children().stream())
        .filter(child -> "variableDeclaratorId".equals(child.kind()))
        .map(id -> SyntaxWalk.text(id.children().get(0)))
        .collect(Collectors.toUnmodifiableSet());
  }

  private static boolean isField(final String target, final Set<String> fields) {
    final String bare = target.startsWith("this.") ? target.substring("this.".length()) : target;
    return fields.contains(bare);
  }
}
