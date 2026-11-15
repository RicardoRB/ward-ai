package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Counts the distinct types a class depends on, by simple name, through the node kinds the rule
 * names: instantiated types for data abstraction coupling, or every referenced type for fan-out
 * complexity. The members of a nested type belong to that type. A class fails when the count is
 * above the limit. The class itself and the type arguments of a generic type do not count.
 *
 * <p>With {@link Counting#DECLARATIONS} it counts instead the distinct types of the fields, local
 * variables, parameters, loop variables, resources and return types of the class; a parameterized
 * type counts as written ({@code List<Row>} and {@code List<Item>} are two), and primitives and
 * {@code java.lang} types do not count. The node kinds and the excluded names are not used then.
 */
public final class ClassCouplingCheck implements RuleCheck {

  private static final String CLASS = "classDeclaration";
  private static final Set<String> CLASSES = Set.of(CLASS);
  private static final String TYPE_ARGUMENTS = "typeArguments";
  private static final char GENERICS = '<';
  private static final char PACKAGE_SEPARATOR = '.';

  /** How the types of a class are counted. */
  public enum Counting {
    /** Every referenced or created type. */
    REFERENCES,
    /** The declared types of fields, variables, parameters, resources and returns. */
    DECLARATIONS
  }

  private static final String TYPE_TYPE = "typeType";
  private static final String METHOD = "methodDeclaration";
  private static final String ARRAY = "[]";
  private static final Set<String> DECLARING_KINDS =
      Set.of(
          "fieldDeclaration",
          "localVariableDeclaration",
          "formalParameter",
          "enhancedForControl",
          "resource");
  private static final Set<String> TYPE_PARTS = Set.of(TYPE_TYPE, "classOrInterfaceType");
  private static final Set<String> JAVA_LANG =
      Set.of(
          "Boolean",
          "Byte",
          "Character",
          "Class",
          "Double",
          "Enum",
          "Exception",
          "Float",
          "Integer",
          "Iterable",
          "Long",
          "Number",
          "Object",
          "Record",
          "Runnable",
          "Short",
          "String",
          "StringBuilder",
          "StringBuffer",
          "Thread",
          "Throwable",
          "Void",
          "var",
          "boolean",
          "byte",
          "char",
          "double",
          "float",
          "int",
          "long",
          "short",
          "void",
          "RuntimeException",
          "IllegalArgumentException",
          "IllegalStateException",
          "UnsupportedOperationException",
          "Comparable",
          "CharSequence",
          "AutoCloseable",
          "Math",
          "System",
          "Error",
          "Override",
          "Deprecated",
          "SuppressWarnings",
          "FunctionalInterface",
          "SafeVarargs");

  private final Counting counting;
  private final Set<String> typeKinds;
  private final Set<String> excluded;
  private final int max;
  private final String message;

  /**
   * Creates the check.
   *
   * @param typeKinds the node kinds whose text names a type
   * @param excluded the simple names that do not count
   * @param max the largest allowed count
   * @param message the message; {@code {value}} and {@code {max}} are filled in
   * @throws IllegalArgumentException if {@code max} is negative
   */
  public ClassCouplingCheck(
      final Set<String> typeKinds,
      final Set<String> excluded,
      final int max,
      final String message) {
    this(Counting.REFERENCES, typeKinds, excluded, max, message);
  }

  /**
   * Creates the check with the given way of counting.
   *
   * @param counting how the types are counted
   * @param typeKinds the node kinds whose text names a type; unused for {@link
   *     Counting#DECLARATIONS}
   * @param excluded the simple names that do not count; unused for {@link Counting#DECLARATIONS}
   * @param max the largest allowed count
   * @param message the message; {@code {value}} and {@code {max}} are filled in
   * @throws IllegalArgumentException if {@code max} is negative
   */
  public ClassCouplingCheck(
      final Counting counting,
      final Set<String> typeKinds,
      final Set<String> excluded,
      final int max,
      final String message) {
    this.counting = Objects.requireNonNull(counting, "counting");
    this.typeKinds = Set.copyOf(typeKinds);
    this.excluded = Set.copyOf(excluded);
    this.max = CheckSupport.nonNegative(max, "max");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, CLASSES)) {
      final Set<String> types = typesOf(visit.node());
      types.remove(JUnitMembers.name(visit.node()));
      report(visit.node(), types, matches);
    }
    return List.copyOf(matches);
  }

  private void report(
      final SyntaxNode node, final Set<String> types, final List<RuleMatch> matches) {
    if (types.size() > max) {
      matches.add(
          new RuleMatch(
              node.span(),
              CheckSupport.fill(message, String.valueOf(types.size()), String.valueOf(max))));
    }
  }

  private Set<String> typesOf(final SyntaxNode node) {
    final Set<String> types = new HashSet<>();
    if (counting == Counting.DECLARATIONS) {
      declared(node, types);
    } else {
      collect(node, types);
    }
    return types;
  }

  private void collect(final SyntaxNode node, final Set<String> types) {
    for (final SyntaxNode child : node.children()) {
      if (typeKinds.contains(child.kind())) {
        addName(SyntaxWalk.text(child), types);
      }
      if (!DesignMembers.TYPE_KINDS.contains(child.kind())
          && !TYPE_ARGUMENTS.equals(child.kind())) {
        collect(child, types);
      }
    }
  }

  /** Collects the declared types of the class, leaving the nested types to themselves. */
  private static void declared(final SyntaxNode node, final Set<String> types) {
    for (final SyntaxNode child : node.children()) {
      if (DECLARING_KINDS.contains(child.kind())) {
        child.children().stream()
            .filter(part -> TYPE_PARTS.contains(part.kind()))
            .findFirst()
            .ifPresent(type -> addDeclared(SyntaxWalk.text(type), types));
      } else if (METHOD.equals(child.kind()) && !child.children().isEmpty()) {
        addDeclared(SyntaxWalk.text(child.children().get(0)), types);
      }
      if (!DesignMembers.TYPE_KINDS.contains(child.kind())) {
        declared(child, types);
      }
    }
  }

  private static void addDeclared(final String text, final Set<String> types) {
    final String bare = text.replace(ARRAY, "");
    final int generics = bare.indexOf(GENERICS);
    final String raw = generics < 0 ? bare : bare.substring(0, generics);
    final String simple = raw.substring(raw.lastIndexOf(PACKAGE_SEPARATOR) + 1);
    if (!simple.isEmpty() && !JAVA_LANG.contains(simple)) {
      types.add(generics < 0 ? simple : simple + bare.substring(generics));
    }
  }

  private void addName(final String text, final Set<String> types) {
    final String withoutGenerics =
        text.indexOf(GENERICS) < 0 ? text : text.substring(0, text.indexOf(GENERICS));
    final String simple =
        withoutGenerics.substring(withoutGenerics.lastIndexOf(PACKAGE_SEPARATOR) + 1);
    if (!simple.isEmpty() && !excluded.contains(simple)) {
      types.add(simple);
    }
  }
}
