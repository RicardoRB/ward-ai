package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a local variable declared with a side-effect-free initializer when a statement that may
 * return or throw sits between the declaration and the first use, as PMD's PrematureDeclaration
 * does. An initializer with a call or a creation counts as impure and is skipped.
 */
public final class PrematureDeclarationCheck implements RuleCheck {

  private static final String DECLARATOR = "variableDeclarator";
  private static final Pattern EXIT = Pattern.compile("(?:^|[^\\w$])(?:return|throw)(?![\\w$])");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the variable name
   */
  public PrematureDeclarationCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("block"))) {
      final List<SyntaxNode> statements = JavaNodes.statements(visit.node());
      for (int index = 0; index < statements.size(); index++) {
        check(statements, index, matches);
      }
    }
    return List.copyOf(matches);
  }

  private void check(
      final List<SyntaxNode> statements, final int index, final List<RuleMatch> matches) {
    final SyntaxNode declaration =
        JavaNodes.kid(statements.get(index), JavaNodes.LOCAL_VARIABLE).orElse(null);
    final List<SyntaxNode> declarators =
        declaration == null
            ? List.of()
            : SyntaxWalk.descendants(declaration).stream()
                .filter(node -> DECLARATOR.equals(node.kind()))
                .toList();
    if (declarators.size() == 1 && pure(declarators.get(0))) {
      final String name = JavaNodes.name(SyntaxWalk.descendants(declarators.get(0)).get(0));
      if (exitBeforeUse(statements.subList(index + 1, statements.size()), name)) {
        matches.add(CheckSupport.at(declaration.span(), CheckSupport.fill(message, name, "")));
      }
    }
  }

  private static boolean pure(final SyntaxNode declarator) {
    return SyntaxWalk.descendants(declarator).stream()
        .noneMatch(node -> "arguments".equals(node.kind()) || "'new'".equals(node.kind()));
  }

  private static boolean exitBeforeUse(final List<SyntaxNode> following, final String name) {
    final Pattern use = Pattern.compile("(?<![\\w$.])" + Pattern.quote(name) + "(?![\\w$])");
    boolean exit = false;
    for (final SyntaxNode statement : following) {
      final String text = tokens(statement);
      if (use.matcher(text).find()) {
        return exit;
      }
      exit = exit || EXIT.matcher(text).find();
    }
    return false;
  }

  private static String tokens(final SyntaxNode statement) {
    final StringBuilder out = new StringBuilder();
    SyntaxWalk.leaves(statement).forEach(leaf -> out.append(' ').append(leaf.fields().get("text")));
    return out.toString();
  }
}
