package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports {@code new AutoValue_Foo(...)} whose arguments are variables named like the properties of
 * {@code Foo} but passed in another order, as Error Prone's AutoValueConstructorOrderChecker does.
 * {@code Foo} must be an {@code @AutoValue} class of the same file with no supertype, so that its
 * abstract methods, in order, are known to be the constructor parameters. Every argument must be a
 * plain variable or {@code this.field}, because the check compares names; any other argument, or
 * names that are not exactly the property names, are left alone.
 */
public final class JavaAutoValueOrderCheck implements RuleCheck {

  private static final String SIMPLE_ANNOTATION = "@AutoValue";
  private static final String PREFIX = "AutoValue_";
  private static final String AUTO_VALUE = "com.google.auto.value.AutoValue";
  private static final Pattern VARIABLE = Pattern.compile("^(?:this\\.)?([A-Za-z_$][\\w$]*)$");
  private static final Pattern GETTER = Pattern.compile("^(get|is)([A-Z].*)$");
  private static final Set<String> OBJECT_METHODS = Set.of("toString", "hashCode");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message
   */
  public JavaAutoValueOrderCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final Map<String, List<String>> properties = properties(root);
    final List<RuleMatch> matches = new ArrayList<>();
    if (!properties.isEmpty()) {
      for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("creator"))) {
        reordered(visit, properties)
            .ifPresent(expression -> matches.add(CheckSupport.at(expression.span(), message)));
      }
    }
    return List.copyOf(matches);
  }

  private static Optional<SyntaxNode> reordered(
      final SyntaxWalk.Visit visit, final Map<String, List<String>> properties) {
    final String created =
        JavaNodes.kid(visit.node(), "createdName").map(SyntaxWalk::text).orElse("");
    final List<SyntaxNode> up = visit.ancestors();
    if (!created.startsWith(PREFIX) || up.isEmpty()) {
      return Optional.empty();
    }
    final List<String> expected = properties.get(created.substring(created.lastIndexOf('_') + 1));
    final List<String> given = argumentNames(visit.node());
    final boolean permuted =
        expected != null
            && given.size() == expected.size()
            && Set.copyOf(given).equals(Set.copyOf(expected))
            && Set.copyOf(given).size() == given.size();
    return permuted && !given.equals(expected)
        ? Optional.of(up.get(up.size() - 1))
        : Optional.empty();
  }

  /** The names of the arguments, or an empty list when one is not a plain variable. */
  private static List<String> argumentNames(final SyntaxNode creator) {
    final List<String> names = new ArrayList<>();
    final List<SyntaxNode> arguments =
        JavaNodes.kid(creator, "classCreatorRest")
            .flatMap(rest -> JavaNodes.kid(rest, "arguments"))
            .flatMap(list -> JavaNodes.kid(list, "expressionList"))
            .map(list -> JavaNodes.kids(list, "expression"))
            .orElse(List.of());
    for (final SyntaxNode argument : arguments) {
      final Matcher variable = VARIABLE.matcher(SyntaxWalk.text(argument));
      if (!variable.matches()) {
        return List.of();
      }
      names.add(variable.group(1));
    }
    return names;
  }

  private static Map<String, List<String>> properties(final SyntaxNode root) {
    final Set<String> imports = JavaAnnotations.imports(root);
    final Map<String, List<String>> found = new HashMap<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(MemberDeclarations.CLASS))) {
      final boolean annotated =
          JavaNodes.modifiers(visit).stream().anyMatch(text -> isAutoValue(text, imports));
      final SyntaxNode clazz = visit.node();
      if (annotated
          && ClassTypeNames.superclass(clazz).isEmpty()
          && ClassTypeNames.implemented(clazz).isEmpty()) {
        found.put(ClassMembers.nameOf(clazz), propertyNames(clazz));
      }
    }
    return found;
  }

  private static boolean isAutoValue(final String annotation, final Set<String> imports) {
    return ("@" + AUTO_VALUE).equals(annotation)
        || SIMPLE_ANNOTATION.equals(annotation)
            && JavaAnnotations.resolves(imports, "AutoValue", AUTO_VALUE);
  }

  /** The AutoValue property names of a class: its abstract accessors, in order. */
  private static List<String> propertyNames(final SyntaxNode clazz) {
    final List<String> accessors =
        ClassShapes.methods(clazz).stream()
            .filter(JavaAutoValueOrderCheck::accessor)
            .map(member -> MemberDeclarations.name(ClassMembers.declarationOf(member)))
            .toList();
    final boolean prefixed = accessors.stream().allMatch(name -> GETTER.matcher(name).matches());
    return accessors.stream().map(name -> prefixed ? property(name) : name).toList();
  }

  private static String property(final String accessor) {
    final String stem = GETTER.matcher(accessor).replaceFirst("$2");
    final boolean acronym = stem.length() > 1 && Character.isUpperCase(stem.charAt(1));
    return acronym ? stem : Character.toLowerCase(stem.charAt(0)) + stem.substring(1);
  }

  private static boolean accessor(final SyntaxNode member) {
    final SyntaxNode method = ClassMembers.declarationOf(member);
    final List<String> modifiers = MemberDeclarations.modifiers(member);
    return modifiers.contains("abstract")
        && !modifiers.contains("static")
        && ClassShapes.takesNothing(method)
        && !OBJECT_METHODS.contains(MemberDeclarations.name(method))
        && ClassMembers.typeText(method).filter(type -> !"void".equals(type)).isPresent();
  }
}
