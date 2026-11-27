package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Follows the members, the elements and the calls that take a part out of an array or an object:
 * {@code object.name}, {@code array[index]}, {@code array.find(...)} and a shorthand property.
 */
final class NodeProgramElements {
  private static final Set<String> ELEMENT_METHODS = Set.of("find", "findLast", "at");
  private static final Set<String> INHERITED =
      Set.of(
          "constructor",
          "toString",
          "valueOf",
          "hasOwnProperty",
          "isPrototypeOf",
          "propertyIsEnumerable",
          "toLocaleString",
          "__proto__",
          "__defineGetter__",
          "__defineSetter__",
          "__lookupGetter__",
          "__lookupSetter__"
      );
  private static final String ARRAY = "arrayLiteral";
  private static final String SHORTHAND = "identifierOrKeyWord";

  private final NodeProgramValues values;

  NodeProgramElements(final NodeProgramValues values) {
    this.values = values;
  }

  /** The values of either of two expressions of one file. */
  Optional<List<NodeProgramValues.Term>> union(
      final NodeProgramFile file,
      final int depth,
      final SyntaxNode first,
      final SyntaxNode second
  ) {
    final Optional<List<NodeProgramValues.Term>> left = values.of(file, first, depth + 1);
    final Optional<List<NodeProgramValues.Term>> right = values.of(file, second, depth + 1);
    if (left.isEmpty() || right.isEmpty()) {
      return Optional.empty();
    }
    final List<NodeProgramValues.Term> all = new ArrayList<>(left.get());
    all.addAll(right.get());
    return Optional.of(all);
  }

  /** The values of {@code object.name}, a count when the name is one. */
  Optional<List<NodeProgramValues.Term>> member(
      final NodeProgramFile file,
      final SyntaxNode expression,
      final NodeProgramShapes.Member member,
      final int depth
  ) {
    if (NodeNumericShapes.member(member.name())) {
      return Optional.of(List.of(new NodeProgramValues.Term(file, expression)));
    }
    return properties(file, member, depth).flatMap(found -> flatten(found, depth));
  }

  /** The expression written for a property of each object that the value of the receiver can be. */
  Optional<List<NodeProgramValues.Term>> properties(
      final NodeProgramFile file,
      final NodeProgramShapes.Member member,
      final int depth
  ) {
    final Optional<List<NodeProgramValues.Term>> objects =
        values.of(file, member.object(), depth + 1);
    if (objects.isEmpty()) {
      return Optional.empty();
    }
    final List<NodeProgramValues.Term> found = new ArrayList<>();
    for (final NodeProgramValues.Term object : objects.get()) {
      final Optional<Optional<SyntaxNode>> value =
          NodeProgramObjects.property(object, member.name());
      if (value.isEmpty() || value.get().isEmpty() && INHERITED.contains(member.name())) {
        return Optional.empty();
      }
      value.get().ifPresent(node -> found.add(new NodeProgramValues.Term(object.file(), node)));
    }
    return Optional.of(found);
  }

  private Optional<List<NodeProgramValues.Term>> flatten(
      final List<NodeProgramValues.Term> found,
      final int depth
  ) {
    final List<NodeProgramValues.Term> all = new ArrayList<>();
    for (final NodeProgramValues.Term term : found) {
      final Optional<List<NodeProgramValues.Term>> next =
          values.of(term.file(), term.expression(), depth + 1);
      if (next.isEmpty()) {
        return Optional.empty();
      }
      all.addAll(next.get());
    }
    return Optional.of(all);
  }

  /** The values of {@code array[index]}, which can be any element. */
  Optional<List<NodeProgramValues.Term>> index(
      final NodeProgramFile file,
      final SyntaxNode expression,
      final int depth
  ) {
    return NodeProgramShapes.indexed(expression).flatMap(object -> elements(file, object, depth));
  }

  /** The values of the elements of the arrays that the value of {@code array} can be. */
  Optional<List<NodeProgramValues.Term>> elements(
      final NodeProgramFile file,
      final SyntaxNode array,
      final int depth
  ) {
    final Optional<List<NodeProgramValues.Term>> arrays = values.of(file, array, depth + 1);
    if (arrays.isEmpty()) {
      return Optional.empty();
    }
    final List<NodeProgramValues.Term> found = new ArrayList<>();
    for (final NodeProgramValues.Term term : arrays.get()) {
      final Optional<List<SyntaxNode>> items = arrayItems(term);
      if (items.isEmpty()) {
        return Optional.empty();
      }
      items.get().forEach(item -> found.add(new NodeProgramValues.Term(term.file(), item)));
    }
    return flatten(found, depth);
  }

  private static Optional<List<SyntaxNode>> arrayItems(final NodeProgramValues.Term term) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(term.expression());
    if (parts.size() != 1 || !ARRAY.equals(parts.get(0).kind())) {
      return Optional.empty();
    }
    return NodeProgramShapes.elements(parts.get(0));
  }

  /** The values of a call: a pure function's own call, or an element an array accessor gives. */
  Optional<List<NodeProgramValues.Term>> call(
      final NodeProgramFile file,
      final SyntaxNode expression,
      final NodeProgramShapes.Call call,
      final int depth
  ) {
    if (NodeProgramImports.pure(file, call)) {
      return Optional.of(List.of(new NodeProgramValues.Term(file, expression)));
    }
    final Optional<NodeProgramShapes.Member> method = NodeProgramShapes.member(call.callee());
    if (method.isEmpty() || !ELEMENT_METHODS.contains(method.get().name())) {
      return Optional.empty();
    }
    return values.seal().uses().callbackSealed(file, call)
        ? elements(file, method.get().object(), depth)
        : Optional.empty();
  }

  /** The values of a property node: the name that a shorthand property writes, or the method. */
  Optional<List<NodeProgramValues.Term>> property(
      final NodeProgramFile file,
      final SyntaxNode property,
      final int depth
  ) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(property);
    if (parts.size() == 1 && SHORTHAND.equals(parts.get(0).kind())) {
      return values.resolve(file, NodeFlowSyntax.firstIdentifier(parts.get(0)), depth);
    }
    return Optional.of(List.of(new NodeProgramValues.Term(file, property)));
  }
}
