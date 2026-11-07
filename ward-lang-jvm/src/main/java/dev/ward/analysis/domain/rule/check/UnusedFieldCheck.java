package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each private field of a class that is never read ({@link Mode#UNREAD}) or never touched
 * ({@link Mode#UNUSED}). A field that the file names anywhere else, as {@code other.x}, or that an
 * annotation marks, is left alone, because another class or a framework may use it.
 */
public final class UnusedFieldCheck implements UnitCheck {

  /** Which fields the check reports. */
  public enum Mode {
    /** A field that is written or initialized and never read. */
    UNREAD,
    /** A field that is neither written, initialized nor read. */
    UNUSED
  }

  private static final String IDENTIFIER = "identifier";
  private static final String SERIAL_VERSION_UID = "serialVersionUID";

  private final Mode mode;
  private final String message;

  /**
   * Creates the check.
   *
   * @param mode which fields to report
   * @param message the message; {@code {value}} is the field name
   */
  public UnusedFieldCheck(final Mode mode, final String message) {
    this.mode = Objects.requireNonNull(mode, "mode");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of("classDeclaration")).stream()
        .map(visit -> FieldAccesses.of(visit.node()))
        .flatMap(accesses -> report(root, accesses).stream())
        .toList();
  }

  private List<RuleMatch> report(final SyntaxNode root, final FieldAccesses accesses) {
    return accesses.fields().stream()
        .filter(field -> field.isPrivate() && !field.annotated())
        .filter(field -> !SERIAL_VERSION_UID.equals(field.name()))
        .filter(field -> flagged(field, accesses.accessesTo(field.name())))
        .filter(field -> !namedElsewhere(root, field.name(), accesses.accessesTo(field.name())))
        .map(
            field ->
                CheckSupport.at(
                    field.declarator().span(), CheckSupport.fill(message, field.name(), "")))
        .toList();
  }

  private boolean flagged(
      final FieldAccesses.Field field, final List<FieldAccesses.Access> accesses) {
    final boolean read = accesses.stream().anyMatch(a -> a.use() == FieldAccesses.Use.READ);
    final boolean touched = field.initialized() || !accesses.isEmpty();
    return mode == Mode.UNREAD ? touched && !read : !touched;
  }

  /** Tells whether the file names the field more often than the class's own accesses do. */
  private static boolean namedElsewhere(
      final SyntaxNode root, final String name, final List<FieldAccesses.Access> accesses) {
    final long total = identifiers(root, name);
    final long own = accesses.stream().mapToLong(a -> identifiers(a.node(), name)).sum();
    return total > own + 1;
  }

  private static long identifiers(final SyntaxNode node, final String name) {
    final long self = isNamed(node, name) ? 1 : 0;
    return self + SyntaxWalk.descendants(node).stream().filter(n -> isNamed(n, name)).count();
  }

  private static boolean isNamed(final SyntaxNode node, final String name) {
    return IDENTIFIER.equals(node.kind()) && name.equals(SyntaxWalk.text(node));
  }
}
