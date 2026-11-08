package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports a getter that is not synchronized while the setter of the same field is, as SpotBugs'
 * UG_SYNC_SET_UNSYNC_GET does. The getter must return the field the setter writes, and the field
 * must be neither volatile nor final.
 */
public final class JavaSyncAccessorCheck implements RuleCheck {

  private static final Pattern GETTER = Pattern.compile("^(?:get|is)([A-Z]\\w*)$");
  private static final Pattern RETURNS = Pattern.compile("^\\{return(?:this\\.)?(\\w+);\\}$");
  private static final String SYNCHRONIZED = "synchronized";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the field
   */
  public JavaSyncAccessorCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(MemberDeclarations.CLASS))) {
      for (final SyntaxNode getter : ClassShapes.methods(visit.node())) {
        field(visit.node(), getter)
            .ifPresent(
                name ->
                    matches.add(
                        CheckSupport.at(getter.span(), CheckSupport.fill(message, name, ""))));
      }
    }
    return List.copyOf(matches);
  }

  private static Optional<String> field(final SyntaxNode clazz, final SyntaxNode getter) {
    final SyntaxNode method = ClassMembers.declarationOf(getter);
    final Matcher named = GETTER.matcher(MemberDeclarations.name(method));
    final Optional<String> returned = returned(method);
    if (!named.matches()
        || returned.isEmpty()
        || isSynchronized(getter)
        || !ClassShapes.takesNothing(method)
        || !plainField(clazz, returned.get())) {
      return Optional.empty();
    }
    final String setter = "set" + named.group(1);
    return ClassShapes.methods(clazz).stream()
        .filter(JavaSyncAccessorCheck::isSynchronized)
        .filter(
            member -> setter.equals(MemberDeclarations.name(ClassMembers.declarationOf(member))))
        .filter(member -> writesField(ClassMembers.declarationOf(member), returned.get()))
        .findFirst()
        .map(member -> returned.get());
  }

  private static Optional<String> returned(final SyntaxNode method) {
    final Matcher body =
        RETURNS.matcher(JavaNodes.kid(method, "methodBody").map(SyntaxWalk::text).orElse(""));
    return body.matches() ? Optional.of(body.group(1)) : Optional.empty();
  }

  private static boolean writesField(final SyntaxNode setter, final String field) {
    return Assignments.in(setter).stream()
        .anyMatch(assignment -> ClassShapes.writes(Assignments.target(assignment), field));
  }

  private static boolean isSynchronized(final SyntaxNode member) {
    return MemberDeclarations.modifiers(member).contains(SYNCHRONIZED);
  }

  private static boolean plainField(final SyntaxNode clazz, final String name) {
    return ClassMembers.fieldMembers(clazz).stream()
        .filter(field -> ClassMembers.fieldNames(ClassMembers.declarationOf(field)).contains(name))
        .anyMatch(
            field ->
                !MemberDeclarations.modifiers(field).contains("volatile")
                    && !MemberDeclarations.modifiers(field).contains(MemberDeclarations.FINAL));
  }
}
