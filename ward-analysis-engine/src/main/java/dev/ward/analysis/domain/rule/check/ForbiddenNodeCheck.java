package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports each node of the filter's kinds, limited by the {@link Condition} when it has any part.
 * The user options are {@code text} and the filters' {@code kinds}.
 */
public final class ForbiddenNodeCheck implements UnitCheck {

  private final NodeFilter filter;
  private final Condition condition;
  private final String message;

  /** Creates the check; {@code {value}} in the message is the node's text. */
  public ForbiddenNodeCheck(
      final NodeFilter filter, final Condition condition, final String message) {
    this.filter = Objects.requireNonNull(filter, "filter");
    this.condition = Objects.requireNonNull(condition, "condition");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return filter.select(root, file).stream()
        .filter(condition::holds)
        .map(
            v ->
                CheckSupport.at(
                    v.node().span(), CheckSupport.fill(message, SyntaxWalk.text(v.node()), "")))
        .toList();
  }

  /**
   * What else a node must have to be reported; an empty part does not limit.
   *
   * @param text a pattern that must be found in the node's text, if any
   * @param withChild kinds of which a direct child must exist, if not empty
   * @param parent kinds of which the parent must be, if not empty
   * @param path the kinds of the ancestors, nearest first, as {@code parent>grandparent}; each step
   *     may list alternatives as {@code a|b}; empty does not limit
   */
  public record Condition(
      Optional<Pattern> text, Set<String> withChild, Set<String> parent, String path) {

    /** The condition that every node meets. */
    public static final Condition ANY = new Condition(Optional.empty(), Set.of(), Set.of());

    /** Creates a condition without an ancestor path. */
    public Condition(
        final Optional<Pattern> text, final Set<String> withChild, final Set<String> parent) {
      this(text, withChild, parent, "");
    }

    /**
     * Copies the sets.
     *
     * @throws NullPointerException if a field is missing
     */
    public Condition {
      Objects.requireNonNull(text, "text");
      Objects.requireNonNull(path, "path");
      withChild = Set.copyOf(withChild);
      parent = Set.copyOf(parent);
    }

    boolean holds(final SyntaxWalk.Visit visit) {
      final SyntaxNode node = visit.node();
      final boolean textOk = text.map(p -> p.matcher(SyntaxWalk.text(node)).find()).orElse(true);
      final boolean childOk =
          withChild.isEmpty()
              || node.children().stream().anyMatch(c -> withChild.contains(c.kind()));
      return textOk && childOk && parentOk(visit) && pathOk(visit);
    }

    private boolean parentOk(final SyntaxWalk.Visit visit) {
      if (parent.isEmpty()) {
        return true;
      }
      final List<SyntaxNode> up = visit.ancestors();
      return !up.isEmpty() && parent.contains(up.get(up.size() - 1).kind());
    }

    private boolean pathOk(final SyntaxWalk.Visit visit) {
      if (path.isEmpty()) {
        return true;
      }
      final String[] steps = path.split(">");
      final List<SyntaxNode> up = visit.ancestors();
      if (up.size() < steps.length) {
        return false;
      }
      for (int step = 0; step < steps.length; step++) {
        final String kind = up.get(up.size() - 1 - step).kind();
        if (!Set.of(steps[step].split("\\|")).contains(kind)) {
          return false;
        }
      }
      return true;
    }
  }
}
