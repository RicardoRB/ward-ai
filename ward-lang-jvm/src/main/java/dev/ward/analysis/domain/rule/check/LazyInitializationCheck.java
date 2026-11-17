package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports {@code if (x == null) x = new T(...)}, the lazy initialization that two threads can both
 * run. A name that a parameter or local of the enclosing method declares is not a field, so the
 * statement gives a default to that variable and is not reported.
 */
public final class LazyInitializationCheck implements UnitCheck {
  private static final Pattern LAZY = Pattern.compile("^if\\((\\w+)==null\\)\\{?\\1=new[\\w.]+\\(");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message
   */
  public LazyInitializationCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of("statement")).stream()
        .filter(visit -> isLazy(visit.node()) && !declaredHere(visit))
        .map(visit -> CheckSupport.at(visit.node().span(), CheckSupport.fill(message, "", "")))
        .toList();
  }

  private static boolean isLazy(final SyntaxNode node) {
    return !node.children().isEmpty()
        && "'if'".equals(node.children().get(0).kind())
        && LAZY.matcher(SyntaxWalk.text(node)).find();
  }

  private static boolean declaredHere(final SyntaxWalk.Visit visit) {
    final Matcher name = LAZY.matcher(SyntaxWalk.text(visit.node()));
    return name.find() && callableDeclares(name.group(1), visit.ancestors());
  }

  private static boolean callableDeclares(final String name, final List<SyntaxNode> ancestors) {
    for (int index = ancestors.size() - 1; index >= 0; index--) {
      final String kind = ancestors.get(index).kind();
      if (MemberDeclarations.METHOD.equals(kind) || MemberDeclarations.CONSTRUCTOR.equals(kind)) {
        return !DeclaredTypes.of(name, ancestors.subList(index, index + 1)).isEmpty();
      }
    }
    return false;
  }
}
