package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a dereference of the result of a method of the same class that can return {@code null}: a
 * call followed by a member access, or a local variable assigned from the call whose uses are all
 * member accesses. A method counts when every overload returns a literal {@code null} somewhere and
 * none is annotated non-null.
 */
public final class NullReturnDereferenceCheck implements RuleCheck {

  private static final String RETURNS_NULL = "returnnull;";
  private static final Pattern NON_NULL = Pattern.compile("@(?:Nonnull|NonNull|NotNull)\\b");
  private static final String OPEN = "(";
  private static final String CLOSE = ")";
  private static final String DOT = ".";
  private static final String BLOCK = "block";
  private static final String DECLARATOR = "variableDeclarator";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the method name
   */
  public NullReturnDereferenceCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("classDeclaration"))) {
      final Set<String> nullable = nullableMethods(visit.node());
      if (!nullable.isEmpty()) {
        chained(visit.node(), nullable, matches);
        locals(visit.node(), nullable, matches);
      }
    }
    return List.copyOf(matches);
  }

  private static Set<String> nullableMethods(final SyntaxNode clazz) {
    final Map<String, Boolean> byName = new HashMap<>();
    for (final SyntaxNode member : ClassMembers.callableMembers(clazz)) {
      final SyntaxNode declaration = ClassMembers.declarationOf(member);
      if (MemberDeclarations.METHOD.equals(declaration.kind())) {
        final String text = SyntaxWalk.text(member);
        final boolean nullable = text.contains(RETURNS_NULL) && !NON_NULL.matcher(text).find();
        byName.merge(MemberDeclarations.name(declaration), nullable, Boolean::logicalAnd);
      }
    }
    final Set<String> names = new HashSet<>();
    byName.forEach(
        (name, nullable) -> {
          if (nullable) {
            names.add(name);
          }
        });
    return names;
  }

  private void chained(
      final SyntaxNode clazz, final Set<String> nullable, final List<RuleMatch> matches) {
    final List<SyntaxNode> tokens = NullTokens.tokens(List.of(clazz));
    for (int index = 0; index + 1 < tokens.size(); index++) {
      final String name = SyntaxWalk.text(tokens.get(index));
      final boolean call =
          nullable.contains(name)
              && OPEN.equals(SyntaxWalk.text(tokens.get(index + 1)))
              && (index == 0 || !DOT.equals(SyntaxWalk.text(tokens.get(index - 1))));
      final int close = call ? closing(tokens, index + 1) : -1;
      if (close > 0
          && close + 1 < tokens.size()
          && DOT.equals(SyntaxWalk.text(tokens.get(close + 1)))) {
        matches.add(
            CheckSupport.at(tokens.get(index).span(), CheckSupport.fill(message, name, "")));
      }
    }
  }

  private static int closing(final List<SyntaxNode> tokens, final int open) {
    int depth = 0;
    for (int index = open; index < tokens.size(); index++) {
      final String text = SyntaxWalk.text(tokens.get(index));
      depth += OPEN.equals(text) ? 1 : CLOSE.equals(text) ? -1 : 0;
      if (depth == 0) {
        return index;
      }
    }
    return -1;
  }

  private void locals(
      final SyntaxNode clazz, final Set<String> nullable, final List<RuleMatch> matches) {
    for (final SyntaxNode block : SyntaxWalk.descendants(clazz)) {
      if (!BLOCK.equals(block.kind())) {
        continue;
      }
      final List<SyntaxNode> statements = block.children();
      for (int index = 0; index < statements.size(); index++) {
        for (final SyntaxNode declarator : SyntaxWalk.descendants(statements.get(index))) {
          if (DECLARATOR.equals(declarator.kind())) {
            fromCall(
                declarator, nullable, statements.subList(index + 1, statements.size()), matches);
          }
        }
      }
    }
  }

  private void fromCall(
      final SyntaxNode declarator,
      final Set<String> nullable,
      final List<SyntaxNode> rest,
      final List<RuleMatch> matches) {
    final String text = SyntaxWalk.text(declarator);
    final int equals = text.indexOf('=');
    final int open = text.indexOf('(');
    if (equals < 0 || open < equals || !text.endsWith(CLOSE)) {
      return;
    }
    final String method = text.substring(equals + 1, open);
    final String variable = text.substring(0, equals);
    final List<SyntaxNode> tokens = NullTokens.tokens(rest);
    final List<NullTokens.Occurrence> uses = NullTokens.uses(tokens, variable);
    if (nullable.contains(method)
        && !uses.isEmpty()
        && !NullTokens.assigns(tokens, variable)
        && uses.stream().allMatch(use -> use.use() == NullTokens.Use.RECEIVER)) {
      matches.add(
          CheckSupport.at(uses.get(0).token().span(), CheckSupport.fill(message, method, "")));
    }
  }
}
