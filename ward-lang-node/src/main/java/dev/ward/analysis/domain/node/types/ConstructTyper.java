package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Resolves {@code new} expressions and the type of {@code this}. */
final class ConstructTyper {
  private final ExprTyper typer;
  private final UnitModel model;
  private final ArgumentTyper arguments;
  private final Map<SyntaxNode, Optional<CallTyper.Call>> memo = new IdentityHashMap<>();

  ConstructTyper(final ExprTyper typer, final UnitModel model, final ArgumentTyper arguments) {
    this.typer = typer;
    this.model = model;
    this.arguments = arguments;
  }

  /** Resolves a {@code new} expression to the constructor it calls. */
  Optional<CallTyper.Call> constructed(final SyntaxNode node) {
    final Optional<CallTyper.Call> known = memo.get(node);
    return known != null ? known : construct(node);
  }

  private Optional<CallTyper.Call> construct(final SyntaxNode node) {
    memo.put(node, Optional.empty());
    final List<SyntaxNode> parts = TreeKit.parts(node);
    final SyntaxNode target = parts.get(1);
    final List<SyntaxNode> targetParts = TreeKit.parts(target);
    final SyntaxNode nameNode = nameOf(target);
    if (nameNode == null) {
      return Optional.empty();
    }
    final TsType constructor = typer.typeOf(nameNode);
    if (!(constructor instanceof TsType.Typeof typeOf)
        || typeOf.ref() == null
        || typeOf.ref().sym() == null) {
      return Optional.empty();
    }
    final List<TsType> args =
        arguments.argumentTypes(
            parts.size() > 2 ? Exprs.arguments(parts.get(2)) : List.of(), List.of());
    final TsType instance =
        new TsType.Named(typeOf.name(), typeArguments(targetParts), typeOf.ref());
    final List<Sig> sigs = new ArrayList<>();
    for (final Sym.Located located : typeOf.ref().sym().of(Decl.Kind.CLASS)) {
      located
          .decl()
          .header()
          .constructs()
          .forEach(sig -> sigs.add(Instantiate.sig(sig, Map.of(), located.scope())));
    }
    final Sig declared =
        Calls.pick(sigs, args)
            .map(Calls.Picked::sig)
            .orElse(new Sig(List.of(), List.of(), instance));
    final Sig chosen = new Sig(declared.typeParams(), declared.params(), instance);
    final Optional<CallTyper.Call> call =
        Optional.of(new CallTyper.Call(typeOf.name(), "<init>", chosen, instance, false));
    memo.put(node, call);
    return call;
  }

  private static SyntaxNode nameOf(final SyntaxNode target) {
    SyntaxNode current = target;
    while (current != null && !Exprs.isNameKind(current.kind())) {
      final List<SyntaxNode> parts = TreeKit.parts(current);
      current = parts.isEmpty() || Exprs.memberName(parts) != null ? null : parts.get(0);
    }
    return current;
  }

  private List<TsType> typeArguments(final List<SyntaxNode> targetParts) {
    final List<TsType> args = new ArrayList<>();
    for (final SyntaxNode part : targetParts) {
      final SyntaxNode generics =
          "singleExpression".equals(part.kind()) ? TreeKit.child(part, "typeArguments") : part;
      final SyntaxNode list = generics == null ? null : TreeKit.find(generics, "typeArgumentList");
      if (list != null) {
        for (final SyntaxNode argument : TreeKit.children(list, "typeArgument")) {
          args.add(
              Instantiate.apply(
                  TyParser.parse(TreeText.leaves(argument)), Map.of(), model.module()));
        }
      }
    }
    return args;
  }

  /** Returns the type of {@code this} at {@code node}: the instance of the enclosing class. */
  TsType thisType(final SyntaxNode node) {
    SyntaxNode current = model.parent(node);
    while (current != null) {
      if ("classDeclaration".equals(current.kind())) {
        final SyntaxNode name = TreeKit.child(current, "identifier");
        final Sym sym = name == null ? null : model.module().find(TreeText.text(name));
        return sym == null
            ? TsType.UNKNOWN
            : new TsType.Named(
                TreeText.text(name), List.of(), new NameRef(sym, TreeText.text(name)));
      }
      if ("functionDeclaration".equals(current.kind())) {
        return TsType.UNKNOWN;
      }
      current = model.parent(current);
    }
    return TsType.UNKNOWN;
  }
}
