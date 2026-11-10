package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a call of {@code wait} by the locks held around it. In {@code two-locks} mode two
 * different locks are held, as SpotBugs' TLW_TWO_LOCK_WAIT does; in {@code unconditional} mode the
 * call sits in a lock with no branch or loop before it, as UW_UNCOND_WAIT does.
 */
public final class JavaWaitContextCheck implements RuleCheck {

  /** What the check reports. */
  public enum Mode {
    /** A wait with two different locks held. */
    TWO_LOCKS,
    /** A wait that no condition guards. */
    UNCONDITIONAL
  }

  private static final int MAX_ARGUMENTS = 2;
  private static final String STATEMENT = "statement";
  private static final Set<String> BRANCHES =
      Set.of("'if'", "'while'", "'for'", "'do'", "'switch'");
  private static final Set<String> STOPS =
      Set.of("methodDeclaration", "constructorDeclaration", "lambdaExpression", "classBody");

  private final Mode mode;
  private final String message;

  /**
   * Creates the check.
   *
   * @param mode what to report
   * @param message the message
   */
  public JavaWaitContextCheck(final Mode mode, final String message) {
    this.mode = Objects.requireNonNull(mode, "mode");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("methodCall"))) {
      if (isWait(visit.node()) && reported(visit)) {
        matches.add(CheckSupport.at(visit.node().span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private boolean reported(final SyntaxWalk.Visit visit) {
    final List<SyntaxNode> ancestors = visit.ancestors();
    final Set<String> locks = new LinkedHashSet<>();
    SyntaxNode region = null;
    for (int index = ancestors.size() - 1; index >= 0; index--) {
      final SyntaxNode ancestor = ancestors.get(index);
      if (STOPS.contains(ancestor.kind())) {
        region = methodLock(ancestors, index, locks, region);
        break;
      }
      region = blockLock(ancestor, locks, region);
    }
    return mode == Mode.TWO_LOCKS
        ? locks.size() > 1
        : region != null && !branchBefore(region, visit.node());
  }

  private static SyntaxNode blockLock(
      final SyntaxNode node, final Set<String> locks, final SyntaxNode region) {
    final List<SyntaxNode> parts = node.children();
    if (STATEMENT.equals(node.kind())
        && parts.size() > 2
        && "'synchronized'".equals(parts.get(0).kind())) {
      locks.add(SyntaxWalk.text(parts.get(2)));
      return node;
    }
    return region;
  }

  private static SyntaxNode methodLock(
      final List<SyntaxNode> ancestors,
      final int index,
      final Set<String> locks,
      final SyntaxNode region) {
    final SyntaxNode stop = ancestors.get(index);
    if (index < 2 || !"methodDeclaration".equals(stop.kind())) {
      return region;
    }
    final SyntaxNode holder = ancestors.get(index - 2);
    if (MemberDeclarations.modifiers(holder).contains("synchronized")) {
      locks.add(MemberDeclarations.modifiers(holder).contains("static") ? "<class>" : "this");
      return region == null ? stop : region;
    }
    return region;
  }

  private static boolean branchBefore(final SyntaxNode region, final SyntaxNode call) {
    return SyntaxWalk.descendants(region).stream()
        .filter(node -> node.span().startOffset() < call.span().startOffset())
        .anyMatch(JavaWaitContextCheck::isBranch);
  }

  private static boolean isBranch(final SyntaxNode node) {
    if (node.children().isEmpty()) {
      return false;
    }
    if (STATEMENT.equals(node.kind())) {
      return BRANCHES.contains(node.children().get(0).kind());
    }
    return "expression".equals(node.kind())
        && node.children().stream().anyMatch(child -> "'?'".equals(child.kind()));
  }

  private static boolean isWait(final SyntaxNode call) {
    final Optional<SyntaxNode> name = JavaNodes.kid(call, "identifier");
    final int arguments =
        JavaNodes.kid(call, "arguments")
            .flatMap(list -> JavaNodes.kid(list, "expressionList"))
            .map(list -> JavaNodes.kids(list, "expression").size())
            .orElse(0);
    return name.map(SyntaxWalk::text).filter("wait"::equals).isPresent()
        && arguments <= MAX_ARGUMENTS;
  }
}
