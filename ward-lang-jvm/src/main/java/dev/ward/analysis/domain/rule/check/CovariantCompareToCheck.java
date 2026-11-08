package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a class that declares {@code compareTo(Self)} but no {@code compareTo(Object)}, so the
 * method does not override {@code Comparable.compareTo}. A class that extends another class or
 * implements {@code Comparable<...>} is left alone, because the inherited type may make the method
 * a real override. Abstract classes and concrete classes are told apart by the {@code
 * abstractClass} flag.
 */
public final class CovariantCompareToCheck implements UnitCheck {
  private static final String CLASS_DECLARATION = "classDeclaration";
  private static final String COMPARE_TO = "compareTo";
  private static final String OBJECT = "Object";
  private static final String GENERIC_COMPARABLE = "Comparable<";
  private static final String IMPLEMENTS = "'implements'";

  private final boolean abstractClass;
  private final String message;

  /**
   * Creates the check.
   *
   * @param abstractClass whether it judges abstract classes (true) or concrete classes (false)
   * @param message the message reported at the class
   */
  public CovariantCompareToCheck(final boolean abstractClass, final String message) {
    this.abstractClass = abstractClass;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of(CLASS_DECLARATION)).stream()
        .filter(visit -> DeclaredModifiers.has(visit.ancestors(), "abstract") == abstractClass)
        .map(SyntaxWalk.Visit::node)
        .filter(CovariantCompareToCheck::standsAlone)
        .filter(CovariantCompareToCheck::declaresCovariantOnly)
        .map(clazz -> CheckSupport.at(clazz.span(), message))
        .toList();
  }

  /** Returns whether nothing inherited could make a compareTo(Self) a real override. */
  private static boolean standsAlone(final SyntaxNode clazz) {
    return ClassHeaders.extendsType(clazz).isEmpty()
        && !ClassHeaders.isGeneric(clazz)
        && !implementsGenericComparable(clazz);
  }

  private static boolean implementsGenericComparable(final SyntaxNode clazz) {
    final List<SyntaxNode> parts = clazz.children();
    for (int index = 0; index + 1 < parts.size(); index++) {
      if (IMPLEMENTS.equals(parts.get(index).kind())
          && SyntaxWalk.text(parts.get(index + 1)).contains(GENERIC_COMPARABLE)) {
        return true;
      }
    }
    return false;
  }

  private static boolean declaresCovariantOnly(final SyntaxNode clazz) {
    final String self = ClassHeaders.name(clazz);
    final List<DeclaredMethods.Method> compareTos =
        DeclaredMethods.of(clazz).stream().filter(m -> COMPARE_TO.equals(m.name())).toList();
    return compareTos.stream().anyMatch(m -> m.parameterTypes().equals(List.of(self)))
        && compareTos.stream().noneMatch(m -> m.parameterTypes().equals(List.of(OBJECT)));
  }
}
