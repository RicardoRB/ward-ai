package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each throw of a new exception inside a try block whose own catch clause catches the same
 * type, which uses an exception as a goto. Matching is by simple type name.
 */
public final class ExceptionFlowCheck implements UnitCheck {

  private static final String STATEMENT = "statement";
  private static final String TRY_PREFIX = "try";
  private static final String BLOCK = "block";
  private static final String CATCH = "catchClause";
  private static final String CATCH_TYPE = "catchType";
  private static final String THROW_PREFIX = "thrownew";
  private static final String CREATED_NAME = "createdName";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the throw statement
   */
  public ExceptionFlowCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of(STATEMENT)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(node -> SyntaxWalk.text(node).startsWith(TRY_PREFIX))
        .flatMap(node -> reportOf(node).stream())
        .toList();
  }

  private List<RuleMatch> reportOf(final SyntaxNode tryStatement) {
    final Set<String> caught = caughtTypes(tryStatement);
    if (caught.isEmpty()) {
      return List.of();
    }
    return tryBlock(tryStatement).stream()
        .flatMap(block -> SyntaxWalk.visits(block, Set.of(STATEMENT)).stream())
        .map(SyntaxWalk.Visit::node)
        .filter(node -> SyntaxWalk.text(node).startsWith(THROW_PREFIX))
        .filter(node -> caught.contains(thrownType(node)))
        .map(node -> CheckSupport.at(node.span(), message))
        .toList();
  }

  private static java.util.Optional<SyntaxNode> tryBlock(final SyntaxNode tryStatement) {
    return tryStatement.children().stream().filter(c -> BLOCK.equals(c.kind())).findFirst();
  }

  private static Set<String> caughtTypes(final SyntaxNode tryStatement) {
    return tryStatement.children().stream()
        .filter(child -> CATCH.equals(child.kind()))
        .flatMap(clause -> clause.children().stream())
        .filter(child -> CATCH_TYPE.equals(child.kind()))
        .flatMap(type -> List.of(SyntaxWalk.text(type).split("\\|")).stream())
        .map(ExceptionFlowCheck::simpleName)
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  private static String thrownType(final SyntaxNode throwStatement) {
    return SyntaxWalk.firstDescendant(throwStatement, CREATED_NAME, Set.of())
        .map(name -> simpleName(SyntaxWalk.text(name)))
        .orElse("");
  }

  private static String simpleName(final String qualified) {
    final String trimmed = qualified.strip();
    return trimmed.substring(trimmed.lastIndexOf('.') + 1);
  }
}
