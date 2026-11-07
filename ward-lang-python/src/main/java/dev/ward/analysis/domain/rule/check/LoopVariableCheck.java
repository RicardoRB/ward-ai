package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a {@code for} loop whose single-name target is unused in the body (pyflakes-style {@code
 * B007}), reused from its iterable ({@code B020}), or that shadows a module-level import ({@code
 * F402}). A name starting with {@code _} is not reported as unused.
 */
public final class LoopVariableCheck implements RuleCheck {

  /** The mistakes the check tells apart, one per rule. */
  public enum Problem {
    /** The loop variable is never used in the body. */
    UNUSED,
    /** The loop variable is also used in the iterable. */
    OVERRIDES_ITERATOR,
    /** The loop variable has the name of a module-level import. */
    SHADOWS_IMPORT
  }

  private static final Set<String> LOOPS = Set.of("for_stmt");
  private static final int SINGLE = 1;
  private static final String IN = "in";
  private static final Pattern NAME = Pattern.compile("[A-Za-z_]\\w*");
  private static final String UNDERSCORE = "_";
  private static final Set<String> BINDERS = Set.of("import", ",", "as");
  private static final Set<String> IMPORTS = Set.of("import_name", "import_from");

  private final Problem problem;
  private final String message;

  /**
   * Creates the check.
   *
   * @param problem the mistake to report
   * @param message the message of each finding
   */
  public LoopVariableCheck(final Problem problem, final String message) {
    this.problem = Objects.requireNonNull(problem, "problem");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<String> imported = importedNames(root);
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, LOOPS)) {
      final SyntaxNode loop = visit.node();
      final SyntaxNode target = loop.children().get(1);
      final String name = singleName(target);
      if (name != null && reported(name, loop, imported)) {
        found.add(new RuleMatch(target.span(), message));
      }
    }
    return List.copyOf(found);
  }

  private boolean reported(final String name, final SyntaxNode loop, final List<String> imported) {
    return switch (problem) {
      case UNUSED -> !name.startsWith(UNDERSCORE) && !uses(name, body(loop));
      case OVERRIDES_ITERATOR -> uses(name, iterable(loop));
      case SHADOWS_IMPORT -> imported.contains(name);
    };
  }

  /** The loop's target when it is one plain name, or {@code null}. */
  private static String singleName(final SyntaxNode target) {
    final List<SyntaxNode> leaves = SyntaxWalk.leaves(target);
    if (leaves.size() != SINGLE) {
      return null;
    }
    final String text = SyntaxWalk.text(leaves.get(0));
    return NAME.matcher(text).matches() ? text : null;
  }

  private static boolean uses(final String name, final List<SyntaxNode> nodes) {
    return nodes.stream()
        .flatMap(node -> SyntaxWalk.leaves(node).stream())
        .anyMatch(leaf -> name.equals(SyntaxWalk.text(leaf)));
  }

  /** The first block of the loop, which holds the body. */
  private static List<SyntaxNode> body(final SyntaxNode loop) {
    return loop.children().stream().filter(child -> "block".equals(child.kind())).limit(1).toList();
  }

  /** The expression after {@code in}. */
  private static List<SyntaxNode> iterable(final SyntaxNode loop) {
    final List<SyntaxNode> children = loop.children();
    for (int index = 2; index < children.size(); index++) {
      if (IN.equals(SyntaxWalk.text(children.get(index - 1)))) {
        return List.of(children.get(index));
      }
    }
    return List.of();
  }

  /**
   * The names that module-level imports bind: the first name after {@code import}, and any {@code
   * as} alias.
   */
  private static List<String> importedNames(final SyntaxNode root) {
    final List<String> names = new ArrayList<>();
    for (final SyntaxNode statement : root.children()) {
      if (SyntaxWalk.descendants(statement).stream()
          .anyMatch(node -> IMPORTS.contains(node.kind()))) {
        names.addAll(bound(statement));
      }
    }
    return names;
  }

  /** The names an import statement binds: each name after {@code import}, a comma or {@code as}. */
  private static List<String> bound(final SyntaxNode statement) {
    final List<String> names = new ArrayList<>();
    String previous = "";
    for (final SyntaxNode leaf : SyntaxWalk.leaves(statement)) {
      final String text = SyntaxWalk.text(leaf);
      if (NAME.matcher(text).matches() && BINDERS.contains(previous)) {
        names.add(text);
      }
      previous = text;
    }
    return names;
  }
}
