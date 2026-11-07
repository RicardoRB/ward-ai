package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a second use of the group generator that {@code itertools.groupby} yields in a {@code
 * for} loop, like {@code B031}: the first use exhausts it, so the second sees nothing.
 */
public final class GroupbyReuseCheck implements RuleCheck {

  private static final Set<String> LOOPS = Set.of("for_stmt");
  private static final Pattern GROUPBY = Pattern.compile("^(itertools\\.)?groupby\\(");
  private static final String IN = "in";
  private static final int TARGET_LEAVES = 3;
  private static final int GROUP = 2;
  private static final long FIRST_USE = 1L;

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public GroupbyReuseCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, LOOPS)) {
      secondUse(visit.node()).ifPresent(leaf -> found.add(new RuleMatch(leaf.span(), message)));
    }
    return List.copyOf(found);
  }

  /** The second read of the group name in the loop body, when the loop iterates a groupby. */
  private static Optional<SyntaxNode> secondUse(final SyntaxNode loop) {
    final List<SyntaxNode> children = loop.children();
    final List<SyntaxNode> target = SyntaxWalk.leaves(children.get(1));
    if (target.size() != TARGET_LEAVES || !iteratesGroupby(children)) {
      return Optional.empty();
    }
    final String group = SyntaxWalk.text(target.get(GROUP));
    return children.stream()
        .filter(child -> "block".equals(child.kind()))
        .findFirst()
        .flatMap(
            block ->
                SyntaxWalk.leaves(block).stream()
                    .filter(leaf -> group.equals(SyntaxWalk.text(leaf)))
                    .skip(FIRST_USE)
                    .findFirst());
  }

  private static boolean iteratesGroupby(final List<SyntaxNode> children) {
    for (int index = 1; index + 1 < children.size(); index++) {
      if (IN.equals(SyntaxWalk.text(children.get(index)))) {
        return GROUPBY.matcher(SyntaxWalk.text(children.get(index + 1))).find();
      }
    }
    return false;
  }
}
