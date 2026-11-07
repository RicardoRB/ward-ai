package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a local variable that is declared with an initializer and returned, thrown or yielded by
 * the next statement, as PMD's VariableCanBeInlined does.
 */
public final class VariableCanBeInlinedCheck implements RuleCheck {

  private static final String DECLARATOR = "variableDeclarator";
  private static final Set<String> EXITS = Set.of("return", "throw", "yield");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the variable name
   */
  public VariableCanBeInlinedCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("block"))) {
      final List<SyntaxNode> statements = JavaNodes.statements(visit.node());
      for (int index = 0; index + 1 < statements.size(); index++) {
        final String name = declared(statements.get(index));
        if (!name.isEmpty() && exits(statements.get(index + 1), name)) {
          matches.add(
              CheckSupport.at(statements.get(index).span(), CheckSupport.fill(message, name, "")));
        }
      }
    }
    return List.copyOf(matches);
  }

  private static String declared(final SyntaxNode statement) {
    final List<SyntaxNode> declarators =
        JavaNodes.kid(statement, JavaNodes.LOCAL_VARIABLE).stream()
            .flatMap(declaration -> SyntaxWalk.descendants(declaration).stream())
            .filter(node -> DECLARATOR.equals(node.kind()))
            .toList();
    final boolean single =
        declarators.size() == 1
            && declarators.get(0).children().stream().anyMatch(child -> "'='".equals(child.kind()));
    return single ? SyntaxWalk.text(declarators.get(0).children().get(0)) : "";
  }

  private static boolean exits(final SyntaxNode statement, final String name) {
    final String text = SyntaxWalk.text(statement);
    return EXITS.stream().anyMatch(word -> text.equals(word + name + ";"));
  }
}
