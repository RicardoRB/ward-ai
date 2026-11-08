package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a serializable class that cannot be deserialized for want of a constructor, as SpotBugs'
 * SE_NO_SUITABLE_CONSTRUCTOR and SE_NO_SUITABLE_CONSTRUCTOR_FOR_EXTERNALIZATION do. In {@code
 * super} mode the class extends a class of the same file that is not serializable and has
 * constructors but none without parameters; in {@code externalizable} mode the class implements
 * {@code Externalizable} and declares constructors but no public one without parameters. A
 * superclass that is not in the file is never reported.
 */
public final class JavaSerialConstructorCheck implements RuleCheck {

  private static final String SERIALIZABLE = "Serializable";
  private static final String EXTERNALIZABLE = "Externalizable";

  private final boolean externalizable;
  private final String message;

  /**
   * Creates the check.
   *
   * @param externalizable whether to check {@code Externalizable} classes instead of superclasses
   * @param message the message; {@code {value}} is the class name
   */
  public JavaSerialConstructorCheck(final boolean externalizable, final String message) {
    this.externalizable = externalizable;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final Map<String, SyntaxNode> classes = new HashMap<>();
    final List<SyntaxNode> all = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(MemberDeclarations.CLASS))) {
      all.add(visit.node());
      classes.merge(ClassMembers.nameOf(visit.node()), visit.node(), (first, second) -> first);
    }
    return all.stream()
        .filter(
            clazz ->
                externalizable
                    ? lacksPublicConstructor(clazz)
                    : lacksSuperConstructor(clazz, classes, all))
        .map(
            clazz ->
                CheckSupport.at(
                    clazz.span(), CheckSupport.fill(message, ClassMembers.nameOf(clazz), "")))
        .toList();
  }

  private static boolean lacksPublicConstructor(final SyntaxNode clazz) {
    final List<SyntaxNode> constructors = ClassShapes.constructors(clazz);
    return ClassTypeNames.implemented(clazz).contains(EXTERNALIZABLE)
        && !constructors.isEmpty()
        && constructors.stream().noneMatch(JavaSerialConstructorCheck::isPublicNoArg);
  }

  private static boolean isPublicNoArg(final SyntaxNode member) {
    return MemberDeclarations.modifiers(member).contains(MemberDeclarations.PUBLIC)
        && ClassShapes.takesNothing(ClassMembers.declarationOf(member));
  }

  private static boolean lacksSuperConstructor(
      final SyntaxNode clazz, final Map<String, SyntaxNode> classes, final List<SyntaxNode> all) {
    if (!ClassTypeNames.implemented(clazz).contains(SERIALIZABLE)) {
      return false;
    }
    final SyntaxNode parent = ClassTypeNames.superclass(clazz).map(classes::get).orElse(null);
    if (parent == null || isDuplicate(parent, all)) {
      return false;
    }
    final List<SyntaxNode> constructors = ClassShapes.constructors(parent);
    return plainRoot(parent)
        && !constructors.isEmpty()
        && constructors.stream().noneMatch(JavaSerialConstructorCheck::isReachableNoArg);
  }

  private static boolean plainRoot(final SyntaxNode parent) {
    return ClassTypeNames.superclass(parent).isEmpty()
        && ClassTypeNames.implemented(parent).isEmpty();
  }

  private static boolean isReachableNoArg(final SyntaxNode member) {
    return !MemberDeclarations.modifiers(member).contains(MemberDeclarations.PRIVATE)
        && ClassShapes.takesNothing(ClassMembers.declarationOf(member));
  }

  private static boolean isDuplicate(final SyntaxNode parent, final List<SyntaxNode> all) {
    final String name = ClassMembers.nameOf(parent);
    return all.stream().filter(clazz -> name.equals(ClassMembers.nameOf(clazz))).count() > 1;
  }
}
