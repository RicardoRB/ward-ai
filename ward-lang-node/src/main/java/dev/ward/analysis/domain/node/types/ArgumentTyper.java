package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;

/** Types the arguments of a call, giving function arguments the parameter types of the callee. */
final class ArgumentTyper {
  private final ExprTyper typer;
  private final Members members;

  ArgumentTyper(final ExprTyper typer, final Members members) {
    this.typer = typer;
    this.members = members;
  }

  /**
   * The overloads of the callee, which give functions passed as arguments their parameter types.
   */
  List<Sig> contextSigs(final SyntaxNode callee) {
    final List<SyntaxNode> parts = TreeKit.parts(callee);
    final String name = Exprs.memberName(parts);
    if (name != null) {
      final Members.Found found = members.find(Operators.nonNull(typer.typeOf(parts.get(0))), name);
      return found == null
          ? List.of()
          : found.sigs().isEmpty() ? Callables.callable(found.type()) : found.sigs();
    }
    return Callables.callable(typer.typeOf(callee));
  }

  List<TsType> argumentTypes(final List<SyntaxNode> arguments, final List<Sig> sigs) {
    final List<Sig> fitting = sigs.stream().filter(sig -> sig.accepts(arguments.size())).toList();
    final List<TsType> types = new ArrayList<>();
    for (int index = 0; index < arguments.size(); index++) {
      final SyntaxNode value = Exprs.argumentValue(arguments.get(index));
      if (Exprs.isSpread(arguments.get(index))) {
        types.add(TsType.UNKNOWN);
        continue;
      }
      if (!fitting.isEmpty() && index < fitting.get(0).params().size()) {
        ParamContext.contextualize(
            value, fitting.get(0).params().get(index).type(), typer.names()::contextualize);
      }
      types.add(typer.typeOf(value));
    }
    return types;
  }
}
