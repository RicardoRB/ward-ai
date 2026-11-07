package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Resolves a call or an object creation to the method of this file that it runs. */
final class NullCallees {

  private NullCallees() {}

  /** The method or constructor a call resolves to in this file, and the type that holds it. */
  record Callee(NullModel.Type owner, NullModel.Method method) {}

  static Optional<Callee> resolve(
      final NullFinder.Unit unit, final NullModel.Type type, final SyntaxNode expression) {
    final Optional<NullFlow.Call> call = NullFlow.call(expression);
    if (call.isPresent()
        && ("".equals(call.get().receiver()) || "this".equals(call.get().receiver()))) {
      return unique(type, call.get().name(), call.get().args().size(), false);
    }
    final List<SyntaxNode> parts = expression.children();
    if (parts.size() == 2 && "new".equals(SyntaxWalk.text(parts.get(0)))) {
      final String name = created(parts.get(1));
      return unit.types().stream()
          .filter(candidate -> candidate.name().equals(name))
          .findFirst()
          .flatMap(owner -> unique(owner, name, NullFlow.arguments(expression).size(), true));
    }
    return Optional.empty();
  }

  private static String created(final SyntaxNode creator) {
    final String text = NullSyntax.first(creator, "createdName").map(SyntaxWalk::text).orElse("");
    final int generic = text.indexOf('<');
    return generic < 0 ? text : text.substring(0, generic);
  }

  private static Optional<Callee> unique(
      final NullModel.Type owner, final String name, final int arity, final boolean constructor) {
    final List<NullModel.Method> found =
        owner.methods().stream()
            .filter(method -> method.constructor() == constructor)
            .filter(method -> constructor || method.name().equals(name))
            .filter(method -> method.params().size() == arity)
            .toList();
    return found.size() == 1 ? Optional.of(new Callee(owner, found.get(0))) : Optional.empty();
  }

  static void passes(
      final Callee callee,
      final List<SyntaxNode> args,
      final Set<String> names,
      final Set<String> calls,
      final List<NullHit> hits) {
    final List<NullModel.Param> params = callee.method().params();
    for (int index = 0; index < args.size(); index++) {
      final NullModel.Param param = params.get(index);
      if (!param.varargs()
          && NullFlow.nonNull(param.notes(), callee.owner().marked(), param.primitive())
          && NullFlow.nullish(args.get(index), names, calls)) {
        hits.add(NullHit.at(args.get(index), SyntaxWalk.text(args.get(index))));
      }
    }
  }
}
