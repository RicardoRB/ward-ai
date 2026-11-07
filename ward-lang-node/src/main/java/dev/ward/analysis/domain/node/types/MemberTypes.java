package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.function.Function;

/** Types member accesses and element accesses through declared types. */
final class MemberTypes {
  private static final String UNDEFINED = "undefined";

  private final Function<SyntaxNode, TsType> typer;
  private final Members members;

  MemberTypes(final Function<SyntaxNode, TsType> typer, final Members members) {
    this.typer = typer;
    this.members = members;
  }

  /** Returns the type of {@code receiver.name}, with {@code undefined} added for {@code ?.}. */
  TsType member(final List<SyntaxNode> parts) {
    final String name = Exprs.memberName(parts);
    final TsType receiver = typer.apply(parts.get(0));
    final boolean optional = Exprs.isOptional(parts);
    final Members.Found found =
        members.find(optional ? Operators.nonNull(receiver) : receiver, name);
    if (found == null) {
      return TsType.UNKNOWN;
    }
    final TsType type = found.sigs().isEmpty() ? found.type() : new TsType.Fn(found.sigs().get(0));
    return optional && type.isKnown()
        ? new TsType.Union(List.of(type, TsType.named(UNDEFINED)))
        : type;
  }

  /** Returns the type of {@code receiver[index]}. */
  TsType element(final List<SyntaxNode> parts) {
    final TsType receiver = Literals.widen(typer.apply(parts.get(0)));
    return switch (receiver) {
      case TsType.Arr arr -> arr.element();
      case TsType.Tuple tuple -> tupleElement(tuple, parts);
      case TsType.Obj obj -> indexed(obj);
      case TsType.Named named ->
          "string".equals(named.name()) ? TsType.named(Literals.STRING) : TsType.UNKNOWN;
      default -> TsType.UNKNOWN;
    };
  }

  private TsType indexed(final TsType.Obj obj) {
    final Members.Found found = members.find(obj, "[]");
    return found == null ? TsType.UNKNOWN : found.type();
  }

  private static TsType tupleElement(final TsType.Tuple tuple, final List<SyntaxNode> parts) {
    final String index = TreeText.text(parts.get(2));
    final boolean inside =
        index.chars().allMatch(Character::isDigit)
            && Integer.parseInt(index) < tuple.elements().size();
    return inside ? tuple.elements().get(Integer.parseInt(index)) : TsType.UNKNOWN;
  }
}
