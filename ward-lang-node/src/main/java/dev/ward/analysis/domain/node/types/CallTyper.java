package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.NameReference;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Resolves calls and constructions to the signature they use. */
final class CallTyper {

  /**
   * A resolved call.
   *
   * @param owner the type or module that declares the callee
   * @param name the method or function name, {@code <init>} for a constructor
   * @param sig the signature as declared
   * @param returns the type the call evaluates to
   * @param isStatic whether the callee needs no instance
   */
  record Call(String owner, String name, Sig sig, Ty returns, boolean isStatic) {}

  private final ExprTyper typer;
  private final UnitModel model;
  private final Members members;
  private final Map<SyntaxNode, Optional<Call>> memo = new IdentityHashMap<>();

  CallTyper(final ExprTyper typer, final UnitModel model, final Members members) {
    this.typer = typer;
    this.model = model;
    this.members = members;
  }

  /** Returns whether the parts of an expression are a call: a callee followed by arguments. */
  boolean isCall(final List<SyntaxNode> parts) {
    return parts.size() == 2 && "arguments".equals(parts.get(1).kind());
  }

  /** Resolves a call expression. */
  Optional<Call> call(final SyntaxNode node) {
    final Optional<Call> known = memo.get(node);
    return known != null ? known : compute(node);
  }

  private Optional<Call> compute(final SyntaxNode node) {
    memo.put(node, Optional.empty());
    final Optional<Call> resolved = resolve(node);
    memo.put(node, resolved);
    return resolved;
  }

  private Optional<Call> resolve(final SyntaxNode node) {
    final List<SyntaxNode> parts = TreeKit.parts(node);
    final SyntaxNode callee = parts.get(0);
    final List<Ty> types = argumentTypes(Exprs.arguments(parts.get(1)), contextSigs(callee));
    final List<SyntaxNode> calleeParts = TreeKit.parts(callee);
    final String name = Exprs.memberName(calleeParts);
    final SyntaxNode target = Exprs.nameOf(callee);
    if (target == null && name != null) {
      return viaMember(calleeParts, name, types);
    }
    return target == null ? viaType(typer.typeOf(callee), types) : viaName(target, types);
  }

  /**
   * The overloads of the callee, which give functions passed as arguments their parameter types.
   */
  private List<Sig> contextSigs(final SyntaxNode callee) {
    final List<SyntaxNode> parts = TreeKit.parts(callee);
    final String name = Exprs.memberName(parts);
    if (name != null) {
      final Members.Found found = members.find(ExprTyper.nonNull(typer.typeOf(parts.get(0))), name);
      return found == null
          ? List.of()
          : found.sigs().isEmpty() ? callable(found.type()) : found.sigs();
    }
    return callable(typer.typeOf(callee));
  }

  private List<Ty> argumentTypes(final List<SyntaxNode> arguments, final List<Sig> sigs) {
    final List<Sig> fitting = sigs.stream().filter(sig -> sig.accepts(arguments.size())).toList();
    final List<Ty> types = new ArrayList<>();
    for (int index = 0; index < arguments.size(); index++) {
      final SyntaxNode value = Exprs.argumentValue(arguments.get(index));
      if (Exprs.isSpread(arguments.get(index))) {
        types.add(Ty.UNKNOWN);
        continue;
      }
      if (!fitting.isEmpty() && index < fitting.get(0).params().size()) {
        typer.functions().contextualize(value, fitting.get(0).params().get(index).type());
      }
      types.add(typer.typeOf(value));
    }
    return types;
  }

  private Optional<Call> viaMember(
      final List<SyntaxNode> parts, final String name, final List<Ty> args) {
    final Ty receiver = ExprTyper.nonNull(typer.typeOf(parts.get(0)));
    final Members.Found found = members.find(receiver, name);
    if (found == null) {
      return Optional.empty();
    }
    final List<Sig> sigs = found.sigs().isEmpty() ? callable(found.type()) : found.sigs();
    final boolean isStatic = receiver instanceof Ty.Typeof;
    return Calls.pick(sigs, args)
        .map(picked -> new Call(found.owner(), name, picked.sig(), picked.returns(), isStatic));
  }

  private Optional<Call> viaName(final SyntaxNode name, final List<Ty> args) {
    final Ty callee = typer.typeOf(name);
    final Optional<Call> call = viaType(callee, args);
    if (call.isPresent() && !call.get().returns().isKnown()) {
      final Ty inferred = typer.functions().returnOf(declaration(name));
      return Optional.of(
          new Call(call.get().owner(), call.get().name(), call.get().sig(), inferred, true));
    }
    return call;
  }

  private SyntaxNode declaration(final SyntaxNode name) {
    final NameReference reference = model.reference(name.span());
    if (reference == null || reference.resolved().isEmpty()) {
      return null;
    }
    final UnitModel.Site site = model.site(reference.resolved().get().declaration());
    return site == null ? null : site.node();
  }

  private Optional<Call> viaType(final Ty callee, final List<Ty> args) {
    final List<Sig> sigs = callable(callee);
    final String name = callee instanceof Ty.Typeof typeOf ? typeOf.name() : "";
    return Calls.pick(sigs, args)
        .map(picked -> new Call("", name, picked.sig(), picked.returns(), true));
  }

  /** Returns the signatures that calling a value of {@code type} can use. */
  List<Sig> callable(final Ty type) {
    return switch (type) {
      case Ty.Fn fn -> List.of(fn.sig());
      case Ty.Typeof typeOf -> functionSigs(typeOf);
      case Ty.Named named -> interfaceCalls(named);
      case Ty.Obj obj ->
          obj.members().stream()
              .filter(member -> "()".equals(member.name()))
              .flatMap(m -> m.sigs().stream())
              .toList();
      default -> List.of();
    };
  }

  private static List<Sig> functionSigs(final Ty.Typeof typeOf) {
    final Sym sym = typeOf.ref() == null ? null : typeOf.ref().sym();
    final List<Sig> sigs = new ArrayList<>();
    if (sym != null) {
      for (final Sym.Located located : sym.of(Decl.Kind.FUNCTION)) {
        located
            .decl()
            .header()
            .calls()
            .forEach(sig -> sigs.add(Inst.sig(sig, Map.of(), located.scope())));
      }
    }
    return sigs;
  }

  private static List<Sig> interfaceCalls(final Ty.Named named) {
    final List<Sig> sigs = new ArrayList<>();
    final Sym sym = named.ref() == null ? null : named.ref().sym();
    if (sym == null) {
      return sigs;
    }
    for (final Sym.Located located : sym.types()) {
      final Map<String, Ty> subst =
          Members.substitution(located.decl().header().typeParams(), named.args());
      located
          .decl()
          .header()
          .calls()
          .forEach(sig -> sigs.add(Inst.sig(sig, subst, located.scope())));
    }
    return sigs;
  }

  /** Resolves a {@code new} expression to the constructor it calls. */
  Optional<Call> constructed(final SyntaxNode node) {
    final Optional<Call> known = memo.get(node);
    return known != null ? known : construct(node);
  }

  private Optional<Call> construct(final SyntaxNode node) {
    memo.put(node, Optional.empty());
    final List<SyntaxNode> parts = TreeKit.parts(node);
    final SyntaxNode target = parts.get(1);
    final List<SyntaxNode> targetParts = TreeKit.parts(target);
    final SyntaxNode nameNode = nameOf(target);
    if (nameNode == null) {
      return Optional.empty();
    }
    final Ty constructor = typer.typeOf(nameNode);
    if (!(constructor instanceof Ty.Typeof typeOf)
        || typeOf.ref() == null
        || typeOf.ref().sym() == null) {
      return Optional.empty();
    }
    final List<Ty> args =
        argumentTypes(parts.size() > 2 ? Exprs.arguments(parts.get(2)) : List.of(), List.of());
    final Ty instance = new Ty.Named(typeOf.name(), typeArguments(targetParts), typeOf.ref());
    final List<Sig> sigs = new ArrayList<>();
    for (final Sym.Located located : typeOf.ref().sym().of(Decl.Kind.CLASS)) {
      located
          .decl()
          .header()
          .constructs()
          .forEach(sig -> sigs.add(Inst.sig(sig, Map.of(), located.scope())));
    }
    final Sig declared =
        Calls.pick(sigs, args)
            .map(Calls.Picked::sig)
            .orElse(new Sig(List.of(), List.of(), instance));
    final Sig chosen = new Sig(declared.typeParams(), declared.params(), instance);
    final Optional<Call> call =
        Optional.of(new Call(typeOf.name(), "<init>", chosen, instance, false));
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

  private List<Ty> typeArguments(final List<SyntaxNode> targetParts) {
    final List<Ty> args = new ArrayList<>();
    for (final SyntaxNode part : targetParts) {
      final SyntaxNode generics =
          "singleExpression".equals(part.kind()) ? TreeKit.child(part, "typeArguments") : part;
      final SyntaxNode list = generics == null ? null : TreeKit.find(generics, "typeArgumentList");
      if (list != null) {
        for (final SyntaxNode argument : TreeKit.children(list, "typeArgument")) {
          args.add(Inst.apply(TyParser.parse(TreeKit.leaves(argument)), Map.of(), model.module()));
        }
      }
    }
    return args;
  }

  /** Returns the type of {@code this} at {@code node}: the instance of the enclosing class. */
  Ty thisType(final SyntaxNode node) {
    SyntaxNode current = model.parent(node);
    while (current != null) {
      if ("classDeclaration".equals(current.kind())) {
        final SyntaxNode name = TreeKit.child(current, "identifier");
        final Sym sym = name == null ? null : model.module().find(TreeKit.text(name));
        return sym == null
            ? Ty.UNKNOWN
            : new Ty.Named(TreeKit.text(name), List.of(), new Ref(sym, TreeKit.text(name)));
      }
      if ("functionDeclaration".equals(current.kind())) {
        return Ty.UNKNOWN;
      }
      current = model.parent(current);
    }
    return Ty.UNKNOWN;
  }
}
