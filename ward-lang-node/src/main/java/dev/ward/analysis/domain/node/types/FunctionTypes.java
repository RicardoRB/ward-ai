package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Types function expressions and infers the return types of functions without annotations. */
final class FunctionTypes {
  private static final String PROMISE = "Promise";
  private static final Set<String> FUNCTION_KINDS =
      Set.of(
          "functionDeclaration",
          "anonymousFunction",
          "arrowFunctionDeclaration",
          "propertyMemberDeclaration");

  private final ExprTyper typer;
  private final UnitModel model;
  private final Set<SyntaxNode> inferring = new HashSet<>();

  FunctionTypes(final ExprTyper typer, final UnitModel model) {
    this.typer = typer;
    this.model = model;
  }

  /** Gives the parameters of the function expression {@code node} the types of {@code expected}. */
  void contextualize(final SyntaxNode node, final Ty expected) {
    if (!(expected instanceof Ty.Fn fn)) {
      return;
    }
    final SyntaxNode function = functionOf(node);
    final SyntaxNode arrow =
        function == null ? null : TreeKit.child(function, "arrowFunctionParameters");
    final List<SyntaxNode> params = new ArrayList<>();
    if (arrow != null && TreeKit.child(arrow, "propertyName") != null) {
      params.add(arrow);
    } else {
      final SyntaxNode list =
          function == null ? null : TreeKit.find(function, "formalParameterList");
      if (list != null) {
        params.addAll(TreeKit.children(list, "formalParameterArg"));
      }
    }
    for (int index = 0; index < params.size() && index < fn.sig().params().size(); index++) {
      typer.contextualize(params.get(index), fn.sig().params().get(index).type());
    }
  }

  private static SyntaxNode functionOf(final SyntaxNode node) {
    SyntaxNode current = node;
    while (current != null && !"arrowFunctionDeclaration".equals(current.kind())) {
      final List<SyntaxNode> parts = TreeKit.parts(current);
      if (parts.size() != 1 && !"anonymousFunction".equals(current.kind())) {
        return null;
      }
      current = parts.isEmpty() ? null : parts.get(0);
    }
    return current;
  }

  /** Returns the function type of a function expression. */
  Ty of(final SyntaxNode node) {
    SyntaxNode function = node;
    if ("anonymousFunction".equals(node.kind())) {
      final List<SyntaxNode> parts = TreeKit.parts(node);
      function = parts.size() == 1 ? parts.get(0) : node;
    }
    final SyntaxNode holder = signatureHolder(function);
    final Sig declared = SigReader.of(holder);
    final Ty returns = declared.returns().isKnown() ? bind(declared.returns()) : returnOf(function);
    final List<Param> params = new ArrayList<>();
    for (final Param param : declared.params()) {
      params.add(new Param(param.name(), bind(param.type()), param.optional(), param.rest()));
    }
    return new Ty.Fn(new Sig(declared.typeParams(), params, returns));
  }

  private Ty bind(final Ty type) {
    return Inst.apply(type, Map.of(), model.module());
  }

  private static SyntaxNode signatureHolder(final SyntaxNode function) {
    final SyntaxNode signature = TreeKit.child(function, "callSignature");
    return signature == null ? function : signature;
  }

  /** Returns the declared or inferred return type of a function node, or unknown. */
  Ty returnOf(final SyntaxNode function) {
    if (function == null || !FUNCTION_KINDS.contains(function.kind())) {
      return Ty.UNKNOWN;
    }
    final Ty declared = SigReader.returns(signatureHolder(function));
    if (declared.isKnown()) {
      return bind(declared);
    }
    if (!inferring.add(function)) {
      return Ty.UNKNOWN;
    }
    try {
      final Ty inferred = infer(function);
      final boolean async = TreeKit.hasToken(function, "async");
      return async && inferred.isKnown()
          ? new Ty.Named(PROMISE, List.of(inferred), null)
          : inferred;
    } finally {
      inferring.remove(function);
    }
  }

  private Ty infer(final SyntaxNode function) {
    final SyntaxNode body = TreeKit.child(function, "arrowFunctionBody");
    if (body != null) {
      final SyntaxNode expression = TreeKit.child(body, Exprs.SINGLE);
      return expression == null
          ? blockReturn(TreeKit.child(body, "functionBody"))
          : Literals.widen(typer.typeOf(expression));
    }
    return blockReturn(TreeKit.child(function, "functionBody"));
  }

  private Ty blockReturn(final SyntaxNode body) {
    if (body == null) {
      return Ty.UNKNOWN;
    }
    final List<Ty> types = new ArrayList<>();
    final boolean[] bare = {false};
    returns(body, types, bare);
    if (types.isEmpty()) {
      return Ty.named("void");
    }
    if (bare[0] || types.stream().anyMatch(type -> !type.isKnown())) {
      return Ty.UNKNOWN;
    }
    final List<Ty> distinct = new ArrayList<>();
    for (final Ty type : types) {
      if (distinct.stream().noneMatch(seen -> seen.print().equals(type.print()))) {
        distinct.add(type);
      }
    }
    return distinct.size() == 1 ? distinct.get(0) : new Ty.Union(distinct);
  }

  private void returns(final SyntaxNode node, final List<Ty> types, final boolean[] bare) {
    for (final SyntaxNode part : TreeKit.parts(node)) {
      if (FUNCTION_KINDS.contains(part.kind()) || "classDeclaration".equals(part.kind())) {
        continue;
      }
      if ("returnStatement".equals(part.kind())) {
        final SyntaxNode value = TreeKit.child(part, "expressionSequence");
        if (value == null) {
          bare[0] = true;
        } else {
          types.add(Literals.widen(typer.typeOf(value)));
        }
      } else {
        returns(part, types, bare);
      }
    }
  }
}
