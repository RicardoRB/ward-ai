package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reports {@code synchronized (field)} where the field of the same class is initialised with {@code
 * new Integer(...)} or another boxed constructor: the lock is a fresh object, but it is boxed, so
 * any code that assigns the field a boxed value later locks a shared one. Lock a plain {@code
 * Object}.
 */
public final class UnsharedBoxedLockCheck implements UnitCheck {
  private static final String CLASS_DECLARATION = "classDeclaration";
  private static final String DECLARATOR = "variableDeclarator";
  private static final String STATEMENT = "statement";
  private static final String SYNCHRONIZED = "'synchronized'";
  private static final String THIS = "this.";
  private static final int DECLARATOR_PARTS = 3;
  private static final int LOCK = 2;
  private static final Pattern BOXED_NEW =
      Pattern.compile(
          "^new(?:java\\.lang\\.)?(?:Integer|Long|Short|Byte|Character|Float|Double|Boolean)\\(");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the synchronized statement
   */
  public UnsharedBoxedLockCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of(CLASS_DECLARATION)).stream()
        .map(SyntaxWalk.Visit::node)
        .flatMap(clazz -> locks(clazz, boxedFields(clazz)).stream())
        .map(lock -> CheckSupport.at(lock.span(), message))
        .toList();
  }

  private static Set<String> boxedFields(final SyntaxNode clazz) {
    return ClassMembers.fieldMembers(clazz).stream()
        .flatMap(field -> SyntaxWalk.descendants(field).stream())
        .filter(
            node -> DECLARATOR.equals(node.kind()) && node.children().size() == DECLARATOR_PARTS)
        .filter(node -> BOXED_NEW.matcher(SyntaxWalk.text(node.children().get(2))).find())
        .map(node -> SyntaxWalk.text(node.children().get(0)))
        .collect(Collectors.toUnmodifiableSet());
  }

  private static List<SyntaxNode> locks(final SyntaxNode clazz, final Set<String> fields) {
    if (fields.isEmpty()) {
      return List.of();
    }
    return SyntaxWalk.descendants(clazz).stream()
        .filter(node -> STATEMENT.equals(node.kind()) && synchronizesOn(node, fields))
        .toList();
  }

  private static boolean synchronizesOn(final SyntaxNode statement, final Set<String> fields) {
    final List<SyntaxNode> parts = statement.children();
    if (parts.size() <= LOCK || !SYNCHRONIZED.equals(parts.get(0).kind())) {
      return false;
    }
    final String lock = SyntaxWalk.text(parts.get(LOCK));
    return fields.contains(lock.startsWith(THIS) ? lock.substring(THIS.length()) : lock);
  }
}
