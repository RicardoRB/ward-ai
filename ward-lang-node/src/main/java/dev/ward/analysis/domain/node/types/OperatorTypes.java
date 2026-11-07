package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** Types unary operators, casts, conditionals and the other operator forms of expressions. */
final class OperatorTypes {
  private static final String NUMBER = Literals.NUMBER;
  private static final Map<String, String> PREFIX_TYPES =
      Map.of(
          "typeof", Literals.STRING,
          "void", "undefined",
          "!", Literals.BOOLEAN,
          "delete", Literals.BOOLEAN);
  private static final Set<String> UNARY = Set.of("-", "+", "~", "++", "--");
  private static final int BINARY_PARTS = 3;
  private static final int TERNARY_PARTS = 5;
  private static final int TERNARY_ELSE = 4;

  private final Function<SyntaxNode, TsType> typer;
  private final UnitModel model;
  private final MemberTypes memberTypes;

  OperatorTypes(
      final Function<SyntaxNode, TsType> typer,
      final UnitModel model,
      final MemberTypes memberTypes) {
    this.typer = typer;
    this.model = model;
    this.memberTypes = memberTypes;
  }

  /** Returns the type of the last expression of a sequence. */
  TsType sequence(final SyntaxNode node) {
    final List<SyntaxNode> parts = TreeKit.children(node, Exprs.SINGLE);
    return parts.isEmpty() ? TsType.UNKNOWN : typer.apply(parts.get(parts.size() - 1));
  }

  /**
   * Returns the type of a prefix form such as {@code typeof x} or {@code -x}, if the parts are one.
   */
  Optional<TsType> prefix(final String first, final List<SyntaxNode> parts) {
    final String known = PREFIX_TYPES.get(first);
    if (known != null) {
      return Optional.of(TsType.named(known));
    }
    return UNARY.contains(first) ? Optional.of(unary(first, parts.get(1))) : Optional.empty();
  }

  /** Returns the type of a prefix operator applied to {@code operand}. */
  private TsType unary(final String operator, final SyntaxNode operand) {
    final TsType type = typer.apply(operand);
    final boolean negatedNumber =
        "-".equals(operator)
            && type instanceof TsType.Named named
            && Character.isDigit(named.name().charAt(0));
    if (negatedNumber) {
      return TsType.named("-" + type.print());
    }
    return "~".equals(operator) && "bigint".equals(type.print()) ? type : TsType.named(NUMBER);
  }

  /** Returns the type of an {@code await} of a value of type {@code type}. */
  static TsType awaited(final TsType type) {
    final boolean promise =
        type instanceof TsType.Named named
            && named.args().size() == 1
            && ("Promise".equals(named.name()) || "PromiseLike".equals(named.name()));
    return promise ? ((TsType.Named) type).args().get(0) : type;
  }

  /**
   * The grammar reads {@code a as T} as the name {@code a} followed by the expression {@code as T}.
   */
  TsType leadingCast(final List<SyntaxNode> parts) {
    final List<SyntaxNode> rest = parts.size() == 2 ? TreeKit.parts(parts.get(1)) : List.of();
    return rest.size() == 2 && "as".equals(TreeText.text(rest.get(0)))
        ? castTo(rest.get(1))
        : TsType.UNKNOWN;
  }

  private TsType castTo(final SyntaxNode target) {
    return Instantiate.apply(TyParser.parse(TreeText.leaves(target)), Map.of(), model.module());
  }

  /** Types the operator forms whose operator is the second part. */
  TsType operator(final List<SyntaxNode> parts) {
    final SyntaxNode left = parts.get(0);
    final String operator = Exprs.token(parts.get(1));
    return switch (operator) {
      case "[" -> memberTypes.element(parts);
      case "!" -> Operators.nonNull(typer.apply(left));
      case "as" -> asType(parts, left);
      case "?" -> ternary(parts);
      case "=" -> typer.apply(parts.get(parts.size() - 1));
      case "++", "--" -> TsType.named(NUMBER);
      default ->
          parts.size() == BINARY_PARTS
              ? Operators.binary(operator, typer.apply(left), typer.apply(parts.get(2)))
              : TsType.UNKNOWN;
    };
  }

  private TsType asType(final List<SyntaxNode> parts, final SyntaxNode left) {
    final SyntaxNode target = parts.get(parts.size() - 1);
    return "const".equals(TreeText.text(target)) ? typer.apply(left) : castTo(target);
  }

  private TsType ternary(final List<SyntaxNode> parts) {
    if (parts.size() != TERNARY_PARTS) {
      return TsType.UNKNOWN;
    }
    return Operators.join(typer.apply(parts.get(2)), typer.apply(parts.get(TERNARY_ELSE)));
  }
}
