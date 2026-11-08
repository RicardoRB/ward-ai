package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.TypeMembers;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds the methods of a class whose names differ only by case from a method the class sees. The
 * confusion is "intentional" when either of the two methods is deprecated.
 */
final class CaseConfusions {

  private static final String DEPRECATED = "@Deprecated";
  private static final String JAVA_DEPRECATED = "java.lang.Deprecated";

  /** A method the class sees, by name, and whether it is deprecated. */
  private record Seen(String name, boolean deprecated) {}

  private CaseConfusions() {}

  /** Returns the declared methods that are confusable with a visible one. */
  static List<DeclaredMethods.Method> of(
      final SyntaxNode type, final TypeMembers inherited, final boolean intentional) {
    final List<Seen> visible = new ArrayList<>();
    inherited.inheritedMethods().stream()
        .map(method -> new Seen(method.name(), isDeprecated(method)))
        .forEach(visible::add);
    final Set<String> deprecated = deprecatedNames(type);
    final List<DeclaredMethods.Method> found = new ArrayList<>();
    for (final DeclaredMethods.Method method : DeclaredMethods.of(type)) {
      final boolean own = deprecated.contains(method.name());
      final boolean confusing =
          visible.stream()
              .anyMatch(
                  seen ->
                      seen.name().equalsIgnoreCase(method.name())
                          && !seen.name().equals(method.name())
                          && intentional == (own || seen.deprecated()));
      if (confusing) {
        found.add(method);
      }
      visible.add(new Seen(method.name(), own));
    }
    return found;
  }

  private static boolean isDeprecated(final CallTarget method) {
    return method.annotations().contains(JAVA_DEPRECATED);
  }

  private static Set<String> deprecatedNames(final SyntaxNode type) {
    final Set<String> names = new HashSet<>();
    for (final SyntaxNode member : ClassMembers.callableMembers(type)) {
      final SyntaxNode declaration = ClassMembers.declarationOf(member);
      final boolean marked =
          member.children().stream()
              .filter(child -> !child.span().equals(declaration.span()))
              .anyMatch(child -> SyntaxWalk.text(child).contains(DEPRECATED));
      if (marked) {
        names.add(MemberDeclarations.name(declaration));
      }
    }
    return names;
  }
}
