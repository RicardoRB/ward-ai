package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a transient field of a serializable class that is written in several places but never in
 * {@code readObject} or {@code readResolve}, so a deserialized object holds the default value.
 */
public final class TransientFieldCheck implements RuleCheck {

  private static final String TRANSIENT = "transient";
  private static final Set<String> RESTORERS = Set.of("readObject", "readResolve");
  private static final int SEVERAL = 2;

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the field
   */
  public TransientFieldCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(MemberDeclarations.CLASS))) {
      final SyntaxNode clazz = visit.node();
      if (ClassHeaders.implementsSerializable(clazz)) {
        for (final SyntaxNode field : ClassMembers.fieldMembers(clazz)) {
          final List<String> modifiers = MemberDeclarations.modifiers(field);
          if (modifiers.contains(TRANSIENT) && !modifiers.contains(MemberDeclarations.STATIC)) {
            ClassMembers.fieldNames(ClassMembers.declarationOf(field))
                .forEach(name -> report(clazz, field, name, matches));
          }
        }
      }
    }
    return List.copyOf(matches);
  }

  private void report(
      final SyntaxNode clazz,
      final SyntaxNode field,
      final String name,
      final List<RuleMatch> matches) {
    int writes = 0;
    int restores = 0;
    for (final SyntaxNode member : ClassMembers.members(clazz)) {
      final int count = writes(member, name);
      if (RESTORERS.contains(MemberDeclarations.name(ClassMembers.declarationOf(member)))) {
        restores += count;
      } else {
        writes += count;
      }
    }
    if (restores == 0 && writes >= SEVERAL) {
      matches.add(CheckSupport.at(field.span(), CheckSupport.fill(message, name, "")));
    }
  }

  private static int writes(final SyntaxNode member, final String name) {
    final String qualified = "." + name;
    return (int)
        Assignments.in(member).stream()
            .map(Assignments::target)
            .filter(target -> target.equals(name) || target.endsWith(qualified))
            .count();
  }
}
