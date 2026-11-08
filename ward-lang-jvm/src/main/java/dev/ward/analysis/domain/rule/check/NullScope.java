package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * What the file declares that the null data-flow needs: its methods and constructors with their
 * modifiers and whether they must not return null, the fields that must not hold null, and the
 * parameters annotated as possibly null. A method is found by its type, name and parameter count; a
 * name that two methods share is left out, so a call is never matched to the wrong one.
 *
 * @param methods the methods by key {@code Type#name(count)}
 * @param nonNullFields the names of the fields that must not be null, by type
 * @param nullableParameters the start offsets of the parameters annotated {@code @CheckForNull}
 */
record NullScope(
    Map<String, Method> methods,
    Map<String, Set<String>> nonNullFields,
    Set<Integer> nullableParameters) {

  static final Set<String> NON_NULL = Set.of("Nonnull", "NonNull", "NotNull");

  private static final Set<String> TYPE_KINDS =
      Set.of("classDeclaration", "enumDeclaration", "recordDeclaration");
  private static final Set<String> CLOSED_KINDS = Set.of("enumDeclaration", "recordDeclaration");

  /**
   * A method or constructor.
   *
   * @param nonNullReturn whether it must not return null
   * @param isStatic whether it is static
   * @param isPrivate whether it is private
   * @param isFinal whether it is final
   * @param constructor whether it is a constructor
   * @param closedType whether no other class can extend its type: final, enum or record
   */
  record Method(
      boolean nonNullReturn,
      boolean isStatic,
      boolean isPrivate,
      boolean isFinal,
      boolean constructor,
      boolean closedType) {}

  NullScope {
    methods = Map.copyOf(methods);
    nonNullFields = Map.copyOf(nonNullFields);
    nullableParameters = Set.copyOf(nullableParameters);
  }

  /** Reads the declarations of {@code root}. */
  static NullScope of(final SyntaxNode root) {
    final Map<String, Method> methods = new HashMap<>();
    final Set<String> repeated = new HashSet<>();
    for (final SyntaxWalk.Visit visit :
        SyntaxWalk.visits(root, Set.of("methodDeclaration", "constructorDeclaration"))) {
      final String key = key(visit);
      if (methods.put(key, method(visit)) != null) {
        repeated.add(key);
      }
    }
    repeated.forEach(methods::remove);
    return new NullScope(methods, fields(root), nullableParameters(root));
  }

  /** Returns the key of a method: the simple name of its type, its name and parameter count. */
  static String key(final String type, final String name, final int count) {
    return type.substring(type.lastIndexOf('.') + 1) + "#" + name + "(" + count + ")";
  }

  static String key(final SyntaxWalk.Visit visit) {
    final SyntaxNode node = visit.node();
    final boolean constructor = "constructorDeclaration".equals(node.kind());
    final String name = constructor ? "<init>" : identifier(node);
    final long count =
        SyntaxWalk.descendants(node).stream()
            .filter(part -> "formalParameter".equals(part.kind()))
            .filter(part -> !insideBody(node, part))
            .count();
    return key(typeOf(visit.ancestors()), name, (int) count);
  }

  private static boolean insideBody(final SyntaxNode method, final SyntaxNode part) {
    return method.children().stream()
        .filter(child -> "methodBody".equals(child.kind()) || "block".equals(child.kind()))
        .anyMatch(
            body ->
                part.span().startOffset() >= body.span().startOffset()
                    && part.span().endOffset() <= body.span().endOffset());
  }

  private static String identifier(final SyntaxNode node) {
    return NullSyntax.first(node, "identifier").map(SyntaxWalk::text).orElse("");
  }

  static String typeOf(final List<SyntaxNode> ancestors) {
    for (int index = ancestors.size() - 1; index >= 0; index--) {
      if (TYPE_KINDS.contains(ancestors.get(index).kind())) {
        return identifier(ancestors.get(index));
      }
    }
    return "";
  }

  private static Method method(final SyntaxWalk.Visit visit) {
    final List<SyntaxNode> up = visit.ancestors();
    final SyntaxNode entry = last(up, "classBodyDeclaration");
    final Set<String> modifiers = entry == null ? Set.of() : modifiers(entry);
    final boolean nonNull =
        entry != null
            && NullSyntax.annotations(entry).stream()
                .map(NullSyntax::simpleName)
                .anyMatch(NON_NULL::contains);
    return new Method(
        nonNull,
        modifiers.contains("static"),
        modifiers.contains("private"),
        modifiers.contains("final"),
        "constructorDeclaration".equals(visit.node().kind()),
        closed(up));
  }

  private static SyntaxNode last(final List<SyntaxNode> up, final String kind) {
    for (int index = up.size() - 1; index >= 0; index--) {
      if (kind.equals(up.get(index).kind())) {
        return up.get(index);
      }
    }
    return null;
  }

  private static Set<String> modifiers(final SyntaxNode holder) {
    return holder.children().stream()
        .filter(child -> NullSyntax.HOLDERS.contains(child.kind()))
        .map(SyntaxWalk::text)
        .collect(Collectors.toSet());
  }

  /** Tells whether the type that declares the method is final, an enum or a record. */
  private static boolean closed(final List<SyntaxNode> up) {
    for (int index = up.size() - 1; index >= 0; index--) {
      final SyntaxNode type = up.get(index);
      if (TYPE_KINDS.contains(type.kind())) {
        return CLOSED_KINDS.contains(type.kind())
            || index > 0 && finalHolder(up.get(index - 1), up, index);
      }
    }
    return false;
  }

  private static boolean finalHolder(
      final SyntaxNode wrapper, final List<SyntaxNode> up, final int index) {
    if ("memberDeclaration".equals(wrapper.kind()) && index > 1) {
      return modifiers(up.get(index - 2)).contains("final");
    }
    return modifiers(wrapper).contains("final");
  }

  private static Map<String, Set<String>> fields(final SyntaxNode root) {
    return NullModel.types(root).stream()
        .collect(
            Collectors.toMap(
                NullModel.Type::name,
                NullScope::fieldNames,
                (left, right) ->
                    Stream.concat(left.stream(), right.stream()).collect(Collectors.toSet())));
  }

  private static Set<String> fieldNames(final NullModel.Type type) {
    return type.fields().stream()
        .filter(field -> field.notes().nonNull() && !field.primitive())
        .map(NullModel.Field::name)
        .collect(Collectors.toSet());
  }

  private static Set<Integer> nullableParameters(final SyntaxNode root) {
    final Set<Integer> out = new HashSet<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("formalParameter"))) {
      final boolean checked =
          NullSyntax.annotations(visit.node()).stream()
              .map(NullSyntax::simpleName)
              .anyMatch("CheckForNull"::equals);
      if (checked) {
        NullSyntax.first(visit.node(), "variableDeclaratorId")
            .ifPresent(id -> out.add(id.span().startOffset()));
      }
    }
    return out;
  }
}
