package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports each {@code synchronized} statement whose lock is a field of the class that some member
 * other than a constructor assigns. With {@code guardingOnly}, only a statement whose own body
 * assigns the lock is reported, because it guards the very field it replaces.
 */
public final class SynchronizedOnUpdatedFieldCheck implements UnitCheck {

  private static final String SYNCHRONIZED = "'synchronized'";
  private static final String THIS = "this.";

  private final boolean guardingOnly;
  private final String message;

  /**
   * Creates the check.
   *
   * @param guardingOnly whether the statement's own body must assign the lock
   * @param message the message; {@code {value}} is the field name
   */
  public SynchronizedOnUpdatedFieldCheck(final boolean guardingOnly, final String message) {
    this.guardingOnly = guardingOnly;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of("classDeclaration")).stream()
        .flatMap(visit -> inClass(visit.node()).stream())
        .toList();
  }

  private List<RuleMatch> inClass(final SyntaxNode classDeclaration) {
    final FieldAccesses accesses = FieldAccesses.of(classDeclaration);
    return SyntaxWalk.descendants(classDeclaration).stream()
        .filter(node -> "statement".equals(node.kind()))
        .filter(node -> node.children().stream().anyMatch(c -> SYNCHRONIZED.equals(c.kind())))
        .flatMap(node -> match(node, accesses).stream())
        .toList();
  }

  private java.util.Optional<RuleMatch> match(
      final SyntaxNode statement, final FieldAccesses accesses) {
    final String lock = lockOf(statement);
    final boolean field = accesses.fields().stream().anyMatch(f -> f.name().equals(lock));
    final boolean updated =
        field
            && accesses.members().stream()
                .filter(m -> m.kind() != FieldAccesses.MemberKind.CONSTRUCTOR)
                .flatMap(m -> m.accesses().stream())
                .anyMatch(a -> a.field().equals(lock) && a.use() != FieldAccesses.Use.READ);
    final boolean flagged = updated && (!guardingOnly || assignsInside(statement, lock));
    return flagged
        ? java.util.Optional.of(
            CheckSupport.at(statement.span(), CheckSupport.fill(message, lock, "")))
        : java.util.Optional.empty();
  }

  private static String lockOf(final SyntaxNode statement) {
    final String text =
        statement.children().stream()
            .filter(c -> "expression".equals(c.kind()))
            .findFirst()
            .map(SyntaxWalk::text)
            .orElse("");
    return text.startsWith(THIS) ? text.substring(THIS.length()) : text;
  }

  private static boolean assignsInside(final SyntaxNode statement, final String lock) {
    final Pattern assign =
        Pattern.compile("^(?:this\\.)?" + Pattern.quote(lock) + "(?:=[^=]|\\+\\+|--)");
    return SyntaxWalk.descendants(statement).stream()
        .filter(n -> "expression".equals(n.kind()))
        .anyMatch(n -> assign.matcher(SyntaxWalk.text(n)).find());
  }
}
