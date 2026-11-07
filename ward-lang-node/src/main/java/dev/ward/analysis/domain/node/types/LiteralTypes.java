package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Types array and object literals from the types of their elements. */
final class LiteralTypes {
  private final Function<SyntaxNode, TsType> typer;

  LiteralTypes(final Function<SyntaxNode, TsType> typer) {
    this.typer = typer;
  }

  /** Returns the array type of an array literal whose elements all have known types. */
  TsType arrayLiteral(final SyntaxNode node) {
    final SyntaxNode list = TreeKit.child(node, "elementList");
    if (list == null) {
      return TsType.UNKNOWN;
    }
    final List<TsType> types = new ArrayList<>();
    for (final SyntaxNode element : TreeKit.children(list, "arrayElement")) {
      final TsType type = elementType(element);
      if (!type.isKnown()) {
        return TsType.UNKNOWN;
      }
      if (types.stream().noneMatch(seen -> seen.print().equals(type.print()))) {
        types.add(type);
      }
    }
    return types.isEmpty()
        ? TsType.UNKNOWN
        : new TsType.Arr(types.size() == 1 ? types.get(0) : new TsType.Union(types));
  }

  private TsType elementType(final SyntaxNode element) {
    final SyntaxNode value = TreeKit.child(element, Exprs.SINGLE);
    if (TreeKit.hasToken(element, "...") || value == null) {
      return TsType.UNKNOWN;
    }
    return Literals.widen(typer.apply(value));
  }

  /** Returns the object type of an object literal whose properties all have known types. */
  TsType objectLiteral(final SyntaxNode node) {
    final List<Member> found = new ArrayList<>();
    for (final SyntaxNode property : TreeKit.children(node, "propertyAssignment")) {
      final SyntaxNode name = TreeKit.child(property, "propertyName");
      final SyntaxNode value = TreeKit.child(property, Exprs.SINGLE);
      if (name == null || value == null || !TreeKit.hasToken(property, ":")) {
        return TsType.UNKNOWN;
      }
      final TsType type = Literals.widen(typer.apply(value));
      if (!type.isKnown()) {
        return TsType.UNKNOWN;
      }
      found.add(new Member(TreeText.unquote(TreeText.text(name)), type, List.of(), 0, List.of()));
    }
    return new TsType.Obj(found);
  }
}
