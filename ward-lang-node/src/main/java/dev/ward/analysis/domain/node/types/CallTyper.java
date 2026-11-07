package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.NameReference;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Resolves calls and constructions to the signature they use. */
final class CallTyper {

  private final ExprTyper typer;
  private final UnitModel model;
  private final Members members;
  private final ArgumentTyper arguments;
  private final Map<SyntaxNode, Optional<Call>> memo = new IdentityHashMap<>();

  /**
   * A resolved call.
   *
   * @param owner the type or module that declares the callee
   * @param name the method or function name, {@code <init>} for a constructor
   * @param sig the signature as declared
   * @param returns the type the call evaluates to
   * @param isStatic whether the callee needs no instance
   */
  record Call(String owner, String name, Sig sig, TsType returns, boolean isStatic) {}

  CallTyper(
      final ExprTyper typer,
      final UnitModel model,
      final Members members,
      final ArgumentTyper arguments) {
    this.typer = typer;
    this.model = model;
    this.members = members;
    this.arguments = arguments;
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
    final List<TsType> types =
        arguments.argumentTypes(Exprs.arguments(parts.get(1)), arguments.contextSigs(callee));
    final List<SyntaxNode> calleeParts = TreeKit.parts(callee);
    final String name = Exprs.memberName(calleeParts);
    final SyntaxNode target = Exprs.nameOf(callee);
    if (target == null && name != null) {
      return viaMember(calleeParts, name, types);
    }
    return target == null ? viaType(typer.typeOf(callee), types) : viaName(target, types);
  }

  private Optional<Call> viaMember(
      final List<SyntaxNode> parts, final String name, final List<TsType> args) {
    final TsType receiver = Operators.nonNull(typer.typeOf(parts.get(0)));
    final Members.Found found = members.find(receiver, name);
    if (found == null) {
      return Optional.empty();
    }
    final List<Sig> sigs = found.sigs().isEmpty() ? Callables.callable(found.type()) : found.sigs();
    final boolean isStatic = receiver instanceof TsType.Typeof;
    return Calls.pick(sigs, args)
        .map(picked -> new Call(found.owner(), name, picked.sig(), picked.returns(), isStatic));
  }

  private Optional<Call> viaName(final SyntaxNode name, final List<TsType> args) {
    final TsType callee = typer.typeOf(name);
    final Optional<Call> call = viaType(callee, args);
    if (call.isPresent() && !call.get().returns().isKnown()) {
      final TsType inferred = typer.functions().returnOf(declaration(name));
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

  private Optional<Call> viaType(final TsType callee, final List<TsType> args) {
    final List<Sig> sigs = Callables.callable(callee);
    final String name = callee instanceof TsType.Typeof typeOf ? typeOf.name() : "";
    return Calls.pick(sigs, args)
        .map(picked -> new Call("", name, picked.sig(), picked.returns(), true));
  }
}
