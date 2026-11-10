package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports the fields of a class that its own members use wrongly: a private field that is read but
 * never written, one that only ever holds null, a read of an unwritten field that selects a member
 * of it, and a field a constructor reads before it writes it.
 */
public final class FieldAccessCheck implements UnitCheck {

  /** What the check looks for. */
  public enum Mode {
    /** A private field that is read and never written. */
    UNWRITTEN,
    /** A private field that is read and only ever holds null. */
    NULL_ONLY,
    /** A member of a private, never written field is selected. */
    DEREFERENCED,
    /** A constructor reads a field before it writes it. */
    UNINITIALIZED_READ,
    /** A local variable has the name of a field that is never written. */
    LOCAL_MASKS
  }

  private static final String CLASS_DECLARATION = "classDeclaration";

  private final Mode mode;
  private final String message;

  /**
   * Creates the check.
   *
   * @param mode what the check looks for
   * @param message the message reported at each finding; {@code {value}} is the field name
   */
  public FieldAccessCheck(final Mode mode, final String message) {
    this.mode = Objects.requireNonNull(mode, "mode");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(CLASS_DECLARATION))) {
      final FieldAccesses accesses = FieldAccesses.of(visit.node());
      switch (mode) {
        case UNWRITTEN -> unwritten(accesses, matches);
        case NULL_ONLY -> nullOnly(accesses, matches);
        case DEREFERENCED -> dereferenced(accesses, matches);
        case LOCAL_MASKS ->
            LocalMasks.of(visit.node(), accesses)
                .forEach(node -> matches.add(report(node, LocalMasks.name(node))));
        default -> uninitializedReads(accesses, matches);
      }
    }
    return List.copyOf(matches);
  }

  private void unwritten(final FieldAccesses accesses, final List<RuleMatch> matches) {
    for (final FieldAccesses.Field field : accesses.fields()) {
      if (neverWritten(field, accesses) && !reads(field, accesses).isEmpty()) {
        matches.add(report(field.declarator(), field.name()));
      }
    }
  }

  private void nullOnly(final FieldAccesses accesses, final List<RuleMatch> matches) {
    for (final FieldAccesses.Field field : accesses.fields()) {
      final List<FieldAccesses.Access> uses = accesses.accessesTo(field.name());
      final boolean writesValue =
          uses.stream().anyMatch(use -> use.use() == FieldAccesses.Use.WRITE)
              || field.initialized() && !field.initializedWithNull();
      final boolean writesNull =
          field.initializedWithNull()
              || uses.stream().anyMatch(use -> use.use() == FieldAccesses.Use.WRITE_NULL);
      if (eligible(field) && writesNull && !writesValue && !reads(field, accesses).isEmpty()) {
        matches.add(report(field.declarator(), field.name()));
      }
    }
  }

  private void dereferenced(final FieldAccesses accesses, final List<RuleMatch> matches) {
    for (final FieldAccesses.Field field : accesses.fields()) {
      if (neverWritten(field, accesses)) {
        reads(field, accesses).stream()
            .filter(FieldAccesses.Access::dereferenced)
            .forEach(read -> matches.add(report(read.node(), field.name())));
      }
    }
  }

  private void uninitializedReads(final FieldAccesses accesses, final List<RuleMatch> matches) {
    final Set<String> initializedByBlocks = new HashSet<>();
    accesses.members().stream()
        .filter(member -> member.kind() == FieldAccesses.MemberKind.INITIALIZER)
        .flatMap(member -> member.accesses().stream())
        .filter(access -> access.use() != FieldAccesses.Use.READ)
        .forEach(access -> initializedByBlocks.add(access.field()));
    final Set<String> candidates = new HashSet<>();
    accesses.fields().stream()
        .filter(field -> !field.initialized() && !field.isStatic() && !field.annotated())
        .filter(field -> !initializedByBlocks.contains(field.name()))
        .forEach(field -> candidates.add(field.name()));
    accesses.members().stream()
        .filter(member -> member.kind() == FieldAccesses.MemberKind.CONSTRUCTOR)
        .forEach(member -> firstReads(member, candidates, matches));
  }

  private void firstReads(
      final FieldAccesses.Member constructor,
      final Set<String> candidates,
      final List<RuleMatch> matches) {
    final Set<String> seen = new HashSet<>();
    for (final FieldAccesses.Access access : constructor.accesses()) {
      if (candidates.contains(access.field())
          && seen.add(access.field())
          && access.use() == FieldAccesses.Use.READ) {
        matches.add(report(access.node(), access.field()));
      }
    }
  }

  private static boolean eligible(final FieldAccesses.Field field) {
    return field.isPrivate() && !field.annotated();
  }

  private static boolean neverWritten(
      final FieldAccesses.Field field, final FieldAccesses accesses) {
    return eligible(field)
        && !field.initialized()
        && accesses.accessesTo(field.name()).stream()
            .allMatch(access -> access.use() == FieldAccesses.Use.READ);
  }

  private static List<FieldAccesses.Access> reads(
      final FieldAccesses.Field field, final FieldAccesses accesses) {
    return accesses.accessesTo(field.name()).stream()
        .filter(access -> access.use() == FieldAccesses.Use.READ)
        .toList();
  }

  private RuleMatch report(final SyntaxNode node, final String field) {
    return CheckSupport.at(node.span(), CheckSupport.fill(message, field, ""));
  }
}
