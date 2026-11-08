package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a write to a static field that is not final from an instance method, as SpotBugs'
 * ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD does, or, in {@code locked} mode, from code that holds
 * the lock of the instance, as SSD_DO_NOT_USE_INSTANCE_LOCK_ON_SHARED_STATIC_DATA does. A parameter
 * or a local with the name of the field hides it and is not reported.
 */
public final class JavaStaticWriteCheck implements RuleCheck {

  private static final String SYNCHRONIZED = "synchronized";
  private static final String STATEMENT = "statement";
  private static final String THIS = "this";

  private final boolean locked;
  private final String message;

  /**
   * Creates the check.
   *
   * @param locked whether only writes under the instance lock are reported
   * @param message the message; {@code {value}} is the field
   */
  public JavaStaticWriteCheck(final boolean locked, final String message) {
    this.locked = locked;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(MemberDeclarations.CLASS))) {
      final Set<String> statics = staticFields(visit.node());
      if (!statics.isEmpty()) {
        for (final SyntaxNode method : ClassShapes.methods(visit.node())) {
          instanceMethod(method, statics, matches);
        }
      }
    }
    return List.copyOf(matches);
  }

  private void instanceMethod(
      final SyntaxNode member, final Set<String> statics, final List<RuleMatch> matches) {
    final List<String> modifiers = MemberDeclarations.modifiers(member);
    if (modifiers.contains(MemberDeclarations.STATIC)) {
      return;
    }
    final SyntaxNode method = ClassMembers.declarationOf(member);
    final Set<String> fields = new HashSet<>(statics);
    fields.removeAll(ClassShapes.declaredNames(method));
    scopes(method, modifiers.contains(SYNCHRONIZED)).stream()
        .flatMap(scope -> Assignments.in(scope).stream())
        .filter(assignment -> fields.contains(written(Assignments.target(assignment))))
        .forEach(
            assignment ->
                matches.add(
                    CheckSupport.at(
                        assignment.span(),
                        CheckSupport.fill(message, written(Assignments.target(assignment)), ""))));
  }

  private List<SyntaxNode> scopes(final SyntaxNode method, final boolean synchronizedMethod) {
    if (!locked || synchronizedMethod) {
      return List.of(method);
    }
    return SyntaxWalk.descendants(method).stream()
        .filter(JavaStaticWriteCheck::isThisLock)
        .toList();
  }

  private static boolean isThisLock(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    return STATEMENT.equals(node.kind())
        && parts.size() > 2
        && "'synchronized'".equals(parts.get(0).kind())
        && THIS.equals(SyntaxWalk.text(parts.get(2)));
  }

  private static String written(final String target) {
    return target.startsWith("this.") ? target.substring("this.".length()) : target;
  }

  private static Set<String> staticFields(final SyntaxNode clazz) {
    final Set<String> names = new HashSet<>();
    for (final SyntaxNode field : ClassMembers.fieldMembers(clazz)) {
      final List<String> modifiers = MemberDeclarations.modifiers(field);
      if (modifiers.contains(MemberDeclarations.STATIC)
          && !modifiers.contains(MemberDeclarations.FINAL)) {
        names.addAll(ClassMembers.fieldNames(ClassMembers.declarationOf(field)));
      }
    }
    return names;
  }
}
