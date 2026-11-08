package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports an access to a field annotated {@code @GuardedBy} from a method that takes no lock at
 * all, as Error Prone's GuardedBy does for the plain case. A method that is synchronized, has a
 * synchronized block, calls {@code lock()} or similar, or is itself annotated {@code @GuardedBy} is
 * not read, and neither is a constructor.
 */
public final class JavaGuardedByCheck implements RuleCheck {

  private static final String FIELD = "fieldDeclaration";
  private static final Pattern LOCK = Pattern.compile("\"([^\"]*)\"");
  private static final Set<String> LOCK_CALLS =
      Set.of("lock", "lockInterruptibly", "tryLock", "readLock", "writeLock", "synchronized");

  private final boolean skipPrivate;
  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the field and {@code {max}} its lock
   */
  public JavaGuardedByCheck(final String message) {
    this(false, message);
  }

  /**
   * Creates the check.
   *
   * @param skipPrivate whether private methods are left unread, because their callers may hold the
   *     lock
   * @param message the message; {@code {value}} is the field and {@code {max}} its lock
   */
  public JavaGuardedByCheck(final boolean skipPrivate, final String message) {
    this.skipPrivate = skipPrivate;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("classDeclaration"))) {
      final List<JavaNodes.Member> members = JavaNodes.members(visit.node());
      final Map<String, String> guarded = guarded(members);
      if (!guarded.isEmpty()) {
        members.stream()
            .filter(member -> "methodDeclaration".equals(member.node().kind()))
            .filter(member -> !skipPrivate || !member.has("private"))
            .filter(JavaGuardedByCheck::unlocked)
            .forEach(member -> accesses(member.node(), guarded, matches));
      }
    }
    return List.copyOf(matches);
  }

  private static Map<String, String> guarded(final List<JavaNodes.Member> members) {
    final Map<String, String> guarded = new HashMap<>();
    for (final JavaNodes.Member member : members) {
      if (FIELD.equals(member.node().kind())) {
        JavaAnnotations.of(member.holder()).stream()
            .filter(annotation -> "GuardedBy".equals(JavaAnnotations.simpleName(annotation)))
            .findFirst()
            .ifPresent(annotation -> register(member.node(), annotation, guarded));
      }
    }
    return guarded;
  }

  private static void register(
      final SyntaxNode field, final SyntaxNode annotation, final Map<String, String> guarded) {
    final Matcher lock = LOCK.matcher(SyntaxWalk.text(annotation));
    if (lock.find()) {
      SyntaxWalk.descendants(field).stream()
          .filter(node -> "variableDeclaratorId".equals(node.kind()))
          .forEach(node -> guarded.put(SyntaxWalk.text(node), lock.group(1)));
    }
  }

  private static boolean unlocked(final JavaNodes.Member method) {
    final boolean annotated =
        JavaAnnotations.of(method.holder()).stream()
            .anyMatch(
                annotation -> {
                  final String name = JavaAnnotations.simpleName(annotation);
                  return "GuardedBy".equals(name) || "HoldsLock".equals(name);
                });
    return !annotated
        && !method.has("synchronized")
        && SyntaxWalk.leaves(method.node()).stream()
            .noneMatch(leaf -> LOCK_CALLS.contains(text(leaf)));
  }

  private void accesses(
      final SyntaxNode method, final Map<String, String> guarded, final List<RuleMatch> matches) {
    final List<SyntaxNode> leaves = SyntaxWalk.leaves(method);
    final Set<String> declared = new java.util.HashSet<>();
    SyntaxWalk.descendants(method).stream()
        .filter(node -> "variableDeclaratorId".equals(node.kind()))
        .forEach(node -> declared.add(SyntaxWalk.text(node)));
    for (int index = 0; index < leaves.size(); index++) {
      final String name = text(leaves.get(index));
      if ("IDENTIFIER".equals(leaves.get(index).kind())
          && guarded.containsKey(name)
          && !declared.contains(name)
          && ownAccess(leaves, index)) {
        matches.add(
            CheckSupport.at(
                leaves.get(index).span(), CheckSupport.fill(message, name, guarded.get(name))));
      }
    }
  }

  private static boolean ownAccess(final List<SyntaxNode> leaves, final int index) {
    final boolean qualified = index > 0 && ".".equals(text(leaves.get(index - 1)));
    return !qualified || index > 1 && "this".equals(text(leaves.get(index - 2)));
  }

  private static String text(final SyntaxNode leaf) {
    return leaf.fields().getOrDefault("text", "");
  }
}
