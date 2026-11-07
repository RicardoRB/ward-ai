package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports each class that declares more methods than the limit. A getter, setter or is-method with
 * at most one statement is not counted, as PMD's too-many-methods does.
 */
public final class MethodLimitCheck implements UnitCheck {

  private static final String BLOCK = "block";
  private static final String BLOCK_STATEMENT = "blockStatement";
  private static final List<String> ACCESSOR_PREFIXES = List.of("get", "set", "is");
  private static final int TRIVIAL_STATEMENTS = 1;

  private final int max;
  private final String message;

  /**
   * Creates the check.
   *
   * @param max the largest number of counted methods a class may declare
   * @param message the message; {@code {value}} and {@code {max}} are filled in
   */
  public MethodLimitCheck(final int max, final String message) {
    this.max = CheckSupport.nonNegative(max, "max");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of(MemberDeclarations.CLASS)).stream()
        .map(SyntaxWalk.Visit::node)
        .flatMap(clazz -> reportOf(clazz).stream())
        .toList();
  }

  private List<RuleMatch> reportOf(final SyntaxNode clazz) {
    final long count =
        MemberDeclarations.body(clazz).stream()
            .flatMap(body -> MemberDeclarations.members(body).stream())
            .map(MemberDeclarations::declaration)
            .flatMap(Optional::stream)
            .filter(declaration -> MemberDeclarations.METHOD.equals(declaration.kind()))
            .filter(method -> !isAccessor(method))
            .count();
    if (count <= max) {
      return List.of();
    }
    return List.of(
        CheckSupport.at(
            clazz.span(), CheckSupport.fill(message, String.valueOf(count), String.valueOf(max))));
  }

  private static boolean isAccessor(final SyntaxNode method) {
    final String name = MemberDeclarations.name(method);
    return ACCESSOR_PREFIXES.stream().anyMatch(name::startsWith)
        && statementCount(method) <= TRIVIAL_STATEMENTS;
  }

  private static long statementCount(final SyntaxNode method) {
    return SyntaxWalk.firstDescendant(method, BLOCK, Set.of()).stream()
        .flatMap(block -> block.children().stream())
        .filter(child -> BLOCK_STATEMENT.equals(child.kind()))
        .count();
  }
}
