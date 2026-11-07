package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Types method invocations of Java: which method a call reaches and what it returns. */
final class JavaCalls {
  private static final String EXPRESSION = "expression";
  private static final String THIS = "this";
  private static final String SUPER = "super";

  private final Session session;
  private JavaExpressions expressions;
  private JavaLambdas lambdas;
  private JavaCreators creators;

  JavaCalls(final Session session) {
    this.session = session;
  }

  void connect(final JavaTyper typer, final JavaExpressions expression) {
    this.expressions = expression;
    this.lambdas = new JavaLambdas(session, typer, expression);
    this.creators = new JavaCreators(session, this, typer);
  }

  JavaLambdas lambdas() {
    return lambdas;
  }

  JavaCreators creators() {
    return creators;
  }

  /** The arguments of an invocation: their nodes and what is known about them. */
  record Prepared(List<SyntaxNode> nodes, List<Overloads.Argument> arguments) {}

  /** Returns the class whose members a value of {@code type} has. */
  Optional<JType.ClassType> siteOf(final JType type, final Ctx ctx) {
    return switch (type) {
      case JType.ClassType named -> Optional.of(named);
      case JType.ArrayType array -> Optional.of(JType.ClassType.of(Types.OBJECT));
      case JType.TypeVariable variable -> {
        final JType bound = ctx.bounds().get(variable.name());
        final Ctx outer = new Ctx(ctx.scope(), Map.of(), List.of(), Optional.empty());
        yield bound == null ? Optional.of(JType.ClassType.of(Types.OBJECT)) : siteOf(bound, outer);
      }
      case JType.Wildcard wildcard ->
          wildcard.bound() != null && wildcard.upper()
              ? siteOf(wildcard.bound(), ctx)
              : Optional.of(JType.ClassType.of(Types.OBJECT));
      case JType.Primitive primitive -> Optional.empty();
    };
  }

  /** Types {@code name(args)} with no qualifier. */
  Typed unqualified(final SyntaxNode call, final Ctx ctx) {
    final Prepared prepared = prepare(call, ctx);
    final String name = Syntax.text(call.children().get(0));
    if (THIS.equals(name) || SUPER.equals(name)) {
      return constructorCall(name, prepared, ctx);
    }
    final List<Hierarchy.Found<JvmMethod>> candidates = new ArrayList<>();
    for (final String owner : ctx.scope().enclosing()) {
      if (candidates.isEmpty()) {
        candidates.addAll(session.hierarchy.methods(session.self(owner), name));
      }
    }
    if (candidates.isEmpty()) {
      for (final String owner : ctx.scope().staticOwners(name)) {
        session.hierarchy.methods(JType.ClassType.of(owner), name).stream()
            .filter(found -> found.member().isStatic())
            .forEach(candidates::add);
      }
    }
    final Typed typed = resolve(candidates, prepared, ctx);
    session.record(call, typed);
    return typed;
  }

  private Typed constructorCall(final String keyword, final Prepared prepared, final Ctx ctx) {
    final Optional<String> owner = ctx.scope().enclosing().stream().findFirst();
    final Optional<JType.ClassType> site =
        THIS.equals(keyword)
            ? owner.map(session::self)
            : owner.flatMap(session.hierarchy::find).flatMap(JvmClass::superclass);
    return resolve(
        site.map(found -> session.hierarchy.methods(found, JvmMethod.CONSTRUCTOR))
            .orElse(List.of()),
        prepared,
        ctx);
  }

  /** Types {@code qualifier.name(args)}. */
  Typed qualified(final Typed qualifier, final SyntaxNode call, final Ctx ctx) {
    final Prepared prepared = prepare(call, ctx);
    final String name = Syntax.text(call.children().get(0));
    final Optional<JType.ClassType> site =
        qualifier.named().or(() -> qualifier.type().flatMap(type -> siteOf(type, ctx)));
    final List<Hierarchy.Found<JvmMethod>> candidates =
        site.map(found -> session.hierarchy.methods(found, name)).orElse(List.of());
    final Typed typed = resolve(candidates, prepared, ctx);
    session.record(call, typed);
    return typed;
  }

  /** Types the arguments of the call or creator {@code node}. */
  Prepared prepare(final SyntaxNode node, final Ctx ctx) {
    final List<SyntaxNode> nodes =
        Syntax.child(node, "arguments")
            .flatMap(arguments -> Syntax.child(arguments, "expressionList"))
            .map(list -> Syntax.children(list, EXPRESSION))
            .orElse(List.of());
    return new Prepared(nodes, nodes.stream().map(argument -> argument(argument, ctx)).toList());
  }

  private Overloads.Argument argument(final SyntaxNode node, final Ctx ctx) {
    final Optional<SyntaxNode> lambda = JavaLambdas.lambdaOf(node);
    if (lambda.isPresent()) {
      return new Overloads.Argument(
          Optional.empty(), lambdas.parameters(lambda.get()).size(), false);
    }
    if (JavaLambdas.isReference(node)) {
      return new Overloads.Argument(Optional.empty(), -1, true);
    }
    return Overloads.Argument.of(expressions.expression(node, ctx, Optional.empty()).type());
  }

  /** Picks the method among {@code candidates} and types the call. */
  Typed resolve(
      final List<Hierarchy.Found<JvmMethod>> candidates, final Prepared prepared, final Ctx ctx) {
    final Optional<Hierarchy.Found<JvmMethod>> chosen =
        session.overloads.select(candidates, prepared.arguments());
    final Map<String, JType> bound =
        chosen
            .map(found -> session.inference.infer(found, prepared.arguments()))
            .orElseGet(HashMap::new);
    final Set<String> variables = new HashSet<>();
    chosen.ifPresent(
        found -> found.member().typeParameters().forEach(p -> variables.add(p.name())));
    lambdas.deferredAll(prepared, chosen, bound, variables, ctx);
    if (chosen.isEmpty()) {
      return Typed.UNKNOWN;
    }
    final JType returned =
        Types.substitute(
            Types.substitute(chosen.get().member().returnType(), chosen.get().bindings()), bound);
    return JavaLambdas.cleanup(returned, variables)
        .map(Typed::of)
        .orElse(Typed.UNKNOWN)
        .withCall(chosen.get());
  }
}
