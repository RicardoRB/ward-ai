package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Resolves {@code new} expressions and the type of {@code this}. */
final class ConstructTyper {
  private static final String CLASS = "classDeclaration";
  private static final String FUNCTION = "functionDeclaration";
  private static final int ARGUMENTS_INDEX = 2;

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
    if (known != null) {
      return known;
    }
    memo.put(node, Optional.empty());
    final Optional<CallTyper.Call> call = construct(node);
    memo.put(node, call);
    return call;
  }

  private Optional<CallTyper.Call> construct(final SyntaxNode node) {
    final List<SyntaxNode> parts = TreeKit.parts(node);
    final SyntaxNode target = parts.get(1);
    final Optional<SyntaxNode> nameNode = nameOf(target);
    if (nameNode.isEmpty()) {
      return Optional.empty();
    }
    final TsType constructor = typer.typeOf(nameNode.get());
    if (!(constructor instanceof TsType.Typeof typeOf)
        || typeOf.ref() == null
        || typeOf.ref().sym() == null) {
      return Optional.empty();
    }
    final List<TsType> args =
        arguments.argumentTypes(
            parts.size() > ARGUMENTS_INDEX
                ? Exprs.arguments(parts.get(ARGUMENTS_INDEX))
                : List.of(),
            List.of());
    final TsType instance =
        new TsType.Named(typeOf.name(), typeArguments(TreeKit.parts(target)), typeOf.ref());
    final Sig declared =
        Calls.pick(constructors(typeOf.ref().sym()), args)
            .map(Calls.Picked::sig)
            .orElse(new Sig(List.of(), List.of(), instance));
    final Sig chosen = new Sig(declared.typeParams(), declared.params(), instance);
    return Optional.of(new CallTyper.Call(typeOf.name(), "<init>", chosen, instance, false));
  }

  private static List<Sig> constructors(final Sym sym) {
    return sym.of(Decl.Kind.CLASS).stream()
        .flatMap(
            located ->
                located.decl().header().constructs().stream()
                    .map(sig -> Instantiate.sig(sig, Map.of(), located.scope())))
        .toList();
  }

  private static Optional<SyntaxNode> nameOf(final SyntaxNode target) {
    if (Exprs.isNameKind(target.kind())) {
      return Optional.of(target);
    }
    final List<SyntaxNode> parts = TreeKit.parts(target);
    if (parts.isEmpty() || Exprs.memberName(parts) != null) {
      return Optional.empty();
    }
    return nameOf(parts.get(0));
  }

  private List<TsType> typeArguments(final List<SyntaxNode> targetParts) {
    return targetParts.stream()
        .map(part -> Exprs.SINGLE.equals(part.kind()) ? TreeKit.child(part, "typeArguments") : part)
        .map(generics -> generics == null ? null : TreeKit.find(generics, "typeArgumentList"))
        .filter(list -> list != null)
        .flatMap(list -> TreeKit.children(list, "typeArgument").stream())
        .map(
            argument ->
                Instantiate.apply(
                    TyParser.parse(TreeText.leaves(argument)), Map.of(), model.module()))
        .toList();
  }

  /** Returns the type of {@code this} at {@code node}: the instance of the enclosing class. */
  TsType thisType(final SyntaxNode node) {
    final SyntaxNode parent = model.parent(node);
    if (parent == null || FUNCTION.equals(parent.kind())) {
      return TsType.UNKNOWN;
    }
    return CLASS.equals(parent.kind()) ? instanceOf(parent) : thisType(parent);
  }

  private TsType instanceOf(final SyntaxNode declaration) {
    final SyntaxNode name = TreeKit.child(declaration, "identifier");
    final Sym sym = name == null ? null : model.module().find(TreeText.text(name));
    return sym == null
        ? TsType.UNKNOWN
        : new TsType.Named(TreeText.text(name), List.of(), new NameRef(sym, TreeText.text(name)));
  }
}
