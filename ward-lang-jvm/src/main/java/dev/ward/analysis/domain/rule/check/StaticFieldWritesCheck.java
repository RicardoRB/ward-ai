package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a public or protected static field that is not final. In {@code outside} mode it is
 * written outside a static initializer, so it cannot simply be made final; in {@code initializer}
 * mode it is written more than once, all in static initializers, so it could be made final after a
 * refactoring; in {@code mutable} mode its type is mutable, so it should be final and not exposed.
 */
public final class StaticFieldWritesCheck implements RuleCheck {

  private static final String BLOCK = "block";
  private static final String STATIC_KEYWORD = "'static'";
  private static final int TWICE = 2;

  private final String mode;
  private final Pattern types;
  private final String message;

  /**
   * Creates the check.
   *
   * @param mode {@code outside}, {@code initializer} or {@code mutable}
   * @param types a pattern the compact type text of the field matches, for {@code mutable} mode
   * @param message the message; {@code {value}} is the field
   */
  public StaticFieldWritesCheck(final String mode, final Pattern types, final String message) {
    this.mode = Objects.requireNonNull(mode, "mode");
    this.types = Objects.requireNonNull(types, "types");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(MemberDeclarations.CLASS))) {
      final SyntaxNode clazz = visit.node();
      for (final SyntaxNode field : ClassMembers.fieldMembers(clazz)) {
        if (isCandidate(field)) {
          for (final String name : ClassMembers.fieldNames(ClassMembers.declarationOf(field))) {
            report(clazz, field, name, matches);
          }
        }
      }
    }
    return List.copyOf(matches);
  }

  private static boolean isCandidate(final SyntaxNode field) {
    final List<String> modifiers = MemberDeclarations.modifiers(field);
    return modifiers.contains(MemberDeclarations.STATIC)
        && !modifiers.contains(MemberDeclarations.FINAL)
        && (modifiers.contains(MemberDeclarations.PUBLIC)
            || modifiers.contains(MemberDeclarations.PROTECTED));
  }

  private void report(
      final SyntaxNode clazz,
      final SyntaxNode field,
      final String name,
      final List<RuleMatch> matches) {
    int inInitializers = SyntaxWalk.text(field).contains("=") ? 1 : 0;
    int elsewhere = 0;
    for (final SyntaxNode member : ClassMembers.members(clazz)) {
      final int writes = writes(member, name);
      if (isStaticInitializer(member)) {
        inInitializers += writes;
      } else {
        elsewhere += writes;
      }
    }
    final boolean reported =
        switch (mode) {
          case "outside" -> elsewhere > 0;
          case "initializer" -> elsewhere == 0 && inInitializers >= TWICE;
          default ->
              types
                  .matcher(ClassMembers.typeText(ClassMembers.declarationOf(field)).orElse(""))
                  .find();
        };
    if (reported) {
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

  private static boolean isStaticInitializer(final SyntaxNode member) {
    return member.children().stream().anyMatch(child -> BLOCK.equals(child.kind()))
        && member.children().stream().anyMatch(child -> STATIC_KEYWORD.equals(child.kind()));
  }
}
