package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports a {@code nonlocal} statement that names a variable the function also declares {@code
 * global}, or whose name no enclosing function binds, like the Ruff rules PLE0115 and PLE0117 and
 * pylint {@code nonlocal-and-global} and {@code nonlocal-without-binding}. A binding is an
 * assignment, a parameter or a definition of the name in the enclosing function's text; that text
 * test is an approximation of the scope analysis.
 */
public final class NonlocalCheck implements RuleCheck {

  private static final Set<String> NONLOCAL = Set.of("nonlocal_stmt");
  private static final Set<String> FUNCTIONS = Set.of("funcdef", "async_funcdef");
  private static final Pattern NAMES = Pattern.compile("^(?:nonlocal|global)(.*)$", Pattern.DOTALL);

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public NonlocalCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, NONLOCAL)) {
      final SyntaxNode enclosing = enclosingFunction(visit.ancestors());
      final List<String> names = names(SyntaxWalk.text(visit.node()));
      if (!names.isEmpty() && unbound(visit.ancestors(), enclosing, names)) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  private static boolean unbound(
      final List<SyntaxNode> ancestors, final SyntaxNode enclosing, final List<String> names) {
    if (enclosing == null) {
      return true;
    }
    final SyntaxNode outer = enclosingFunctionAbove(ancestors, enclosing);
    if (outer == null) {
      return true;
    }
    final String scope = SyntaxWalk.text(outer);
    return names.stream().anyMatch(name -> !binds(scope, name));
  }

  /** The nearest function above {@code inner} in the ancestor list, or null. */
  private static SyntaxNode enclosingFunctionAbove(
      final List<SyntaxNode> ancestors, final SyntaxNode inner) {
    for (int index = ancestors.indexOf(inner) - 1; index >= 0; index--) {
      if (FUNCTIONS.contains(ancestors.get(index).kind())) {
        return ancestors.get(index);
      }
    }
    return null;
  }

  private static boolean binds(final String scope, final String name) {
    final String n = Pattern.quote(name);
    return Pattern.compile("(?<![\\w.])" + n + "=(?!=)").matcher(scope).find()
        || Pattern.compile("def" + n + "\\(").matcher(scope).find()
        || Pattern.compile("[(,]" + n + "[,:)=]").matcher(scope).find()
        || Pattern.compile("for" + n + "in").matcher(scope).find();
  }

  private static SyntaxNode enclosingFunction(final List<SyntaxNode> ancestors) {
    for (int index = ancestors.size() - 1; index >= 0; index--) {
      if (FUNCTIONS.contains(ancestors.get(index).kind())) {
        return ancestors.get(index);
      }
    }
    return null;
  }

  private static List<String> names(final String statement) {
    final Matcher matcher = NAMES.matcher(statement);
    final List<String> names = new ArrayList<>();
    if (matcher.matches()) {
      for (final String name : matcher.group(1).split(",")) {
        if (name.matches("\\w+")) {
          names.add(name);
        }
      }
    }
    return List.copyOf(names);
  }
}
