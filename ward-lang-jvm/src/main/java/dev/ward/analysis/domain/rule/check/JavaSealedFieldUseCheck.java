package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a public or protected field that the code never reads, never writes or never uses, as
 * SpotBugs' URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD, UWF_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD and
 * UUF_UNUSED_PUBLIC_OR_PROTECTED_FIELD do. Other files can use such a field, so only a field of a
 * private nested class is checked, whose every use is in this file. A use is any mention of the
 * field name in the file, so a second field of the same name hides an unused one. A field with an
 * annotation is left alone.
 */
public final class JavaSealedFieldUseCheck implements RuleCheck {

  /** The use the check looks for. */
  public enum Mode {
    /** The field is written but never read. */
    UNREAD,
    /** The field is read but never written. */
    UNWRITTEN,
    /** The field is neither read nor written. */
    UNUSED
  }

  private static final String PLAIN_ASSIGNMENT = "'='";

  private final Mode mode;
  private final String message;

  /**
   * Creates the check.
   *
   * @param mode the use that is missing
   * @param message the message; {@code {value}} is the field
   */
  public JavaSealedFieldUseCheck(final Mode mode, final String message) {
    this.mode = Objects.requireNonNull(mode, "mode");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(MemberDeclarations.CLASS))) {
      if (isNested(visit) && JavaNodes.modifiers(visit).contains(MemberDeclarations.PRIVATE)) {
        for (final SyntaxNode field : ClassMembers.fieldMembers(visit.node())) {
          fieldsOf(field, root, matches);
        }
      }
    }
    return List.copyOf(matches);
  }

  private static boolean isNested(final SyntaxWalk.Visit visit) {
    return visit.ancestors().stream()
        .anyMatch(node -> MemberDeclarations.CLASS.equals(node.kind()));
  }

  private void fieldsOf(
      final SyntaxNode field, final SyntaxNode root, final List<RuleMatch> matches) {
    final List<String> modifiers = MemberDeclarations.modifiers(field);
    final boolean exposed =
        modifiers.contains(MemberDeclarations.PUBLIC)
            || modifiers.contains(MemberDeclarations.PROTECTED);
    final boolean annotated =
        field.children().stream().anyMatch(child -> SyntaxWalk.text(child).startsWith("@"));
    if (exposed && !annotated) {
      final boolean initialized =
          SyntaxWalk.descendants(field).stream()
              .anyMatch(node -> "variableInitializer".equals(node.kind()));
      for (final String name : ClassMembers.fieldNames(ClassMembers.declarationOf(field))) {
        if (isReported(name, initialized, root)) {
          matches.add(CheckSupport.at(field.span(), CheckSupport.fill(message, name, "")));
        }
      }
    }
  }

  private boolean isReported(final String name, final boolean initialized, final SyntaxNode root) {
    final long mentions =
        SyntaxWalk.descendants(root).stream()
            .filter(node -> JavaNodes.IDENTIFIER.equals(node.kind()))
            .filter(node -> name.equals(SyntaxWalk.text(node)))
            .count();
    final long uses = mentions - 1;
    final long plainWrites = plainWrites(root, name);
    final long writes = plainWrites + otherWrites(root, name);
    final long reads = uses - plainWrites;
    return switch (mode) {
      case UNREAD -> reads == 0 && (writes > 0 || initialized);
      case UNWRITTEN -> writes == 0 && !initialized && reads > 0;
      default -> uses == 0 && !initialized;
    };
  }

  private static long plainWrites(final SyntaxNode root, final String name) {
    return Assignments.in(root).stream()
        .filter(assignment -> PLAIN_ASSIGNMENT.equals(assignment.children().get(1).kind()))
        .filter(assignment -> targets(Assignments.target(assignment), name))
        .count();
  }

  private static long otherWrites(final SyntaxNode root, final String name) {
    final long compound =
        Assignments.in(root).stream()
            .filter(assignment -> !PLAIN_ASSIGNMENT.equals(assignment.children().get(1).kind()))
            .filter(assignment -> targets(Assignments.target(assignment), name))
            .count();
    final long steps =
        SyntaxWalk.descendants(root).stream()
            .filter(node -> "expression".equals(node.kind()) && node.children().size() == 2)
            .filter(node -> targets(stepOperand(node), name))
            .count();
    return compound + steps;
  }

  private static String stepOperand(final SyntaxNode node) {
    final boolean prefix = Set.of("'++'", "'--'").contains(node.children().get(0).kind());
    final boolean suffix = Set.of("'++'", "'--'").contains(node.children().get(1).kind());
    if (prefix) {
      return SyntaxWalk.text(node.children().get(1));
    }
    return suffix ? SyntaxWalk.text(node.children().get(0)) : "";
  }

  private static boolean targets(final String target, final String name) {
    return target.equals(name) || target.endsWith("." + name);
  }
}
