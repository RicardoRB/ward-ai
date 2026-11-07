package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Types method invocations, object creation and lambdas of Java. */
final class JavaCalls {
  private static final String IDENTIFIER = "identifier";
  private static final String EXPRESSION = "expression";

  private final Session session;
  private JavaTyper typer;
  private JavaExpressions expressions;

  JavaCalls(final Session session) {
    this.session = session;
  }

  void connect(final JavaTyper owner, final JavaExpressions expression) {
    this.typer = owner;
    this.expressions = expression;
  }

  /** The arguments of an invocation: their nodes and what is known about them. */
  private record Prepared(List<SyntaxNode> nodes, List<Overloads.Argument> arguments) {}

  /** Returns the class whose members a value of {@code type} has. */
  Optional<JType.ClassType> siteOf(final JType type, final Ctx ctx) {
    return switch (type) {
      case JType.ClassType named -> Optional.of(named);
      case JType.ArrayType array -> Optional.of(JType.ClassType.of(Types.OBJECT));
      case JType.TypeVariable variable ->
          ctx.bounds().containsKey(variable.name())
              ? siteOf(
                  ctx.bounds().get(variable.name()),
                  new Ctx(ctx.scope(), Map.of(), List.of(), Optional.empty()))
              : Optional.of(JType.ClassType.of(Types.OBJECT));
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
    final SyntaxNode head = call.children().get(0);
    final String name = Syntax.text(head);
    if ("this".equals(name) || "super".equals(name)) {
      return constructorCall(name, prepared, ctx);
    }
    for (final String owner : ctx.scope().enclosing()) {
      final List<Hierarchy.Found<JvmMethod>> candidates =
          session.hierarchy.methods(session.self(owner), name);
      if (!candidates.isEmpty()) {
        return finish(call, candidates, prepared, ctx);
      }
    }
    final List<Hierarchy.Found<JvmMethod>> imported = new ArrayList<>();
    for (final String owner : ctx.scope().staticOwners(name)) {
      session.hierarchy.methods(JType.ClassType.of(owner), name).stream()
          .filter(found -> found.member().isStatic())
          .forEach(imported::add);
    }
    return finish(call, imported, prepared, ctx);
  }

  private Typed constructorCall(final String keyword, final Prepared prepared, final Ctx ctx) {
    final Optional<String> owner = ctx.scope().enclosing().stream().findFirst();
    final Optional<JType.ClassType> site =
        "this".equals(keyword)
            ? owner.map(session::self)
            : owner.flatMap(session.hierarchy::find).flatMap(JvmClass::superclass);
    final List<Hierarchy.Found<JvmMethod>> candidates =
        site.map(found -> session.hierarchy.methods(found, JvmMethod.CONSTRUCTOR))
            .orElse(List.of());
    return resolve(candidates, prepared, ctx, false);
  }

  /** Types {@code qualifier.name(args)}. */
  Typed qualified(final Typed qualifier, final SyntaxNode call, final Ctx ctx) {
    final Prepared prepared = prepare(call, ctx);
    final String name = Syntax.text(call.children().get(0));
    final Optional<JType.ClassType> site =
        qualifier.named().or(() -> qualifier.type().flatMap(type -> siteOf(type, ctx)));
    final List<Hierarchy.Found<JvmMethod>> candidates =
        site.map(found -> session.hierarchy.methods(found, name)).orElse(List.of());
    return finish(call, candidates, prepared, ctx);
  }

  private Typed finish(
      final SyntaxNode call,
      final List<Hierarchy.Found<JvmMethod>> candidates,
      final Prepared prepared,
      final Ctx ctx) {
    final Typed typed = resolve(candidates, prepared, ctx, false);
    session.record(call, typed);
    return typed;
  }

  private Prepared prepare(final SyntaxNode call, final Ctx ctx) {
    final List<SyntaxNode> nodes =
        Syntax.child(call, "arguments")
            .flatMap(arguments -> Syntax.child(arguments, "expressionList"))
            .map(list -> Syntax.children(list, EXPRESSION))
            .orElse(List.of());
    return new Prepared(nodes, arguments(nodes, ctx));
  }

  private List<Overloads.Argument> arguments(final List<SyntaxNode> nodes, final Ctx ctx) {
    final List<Overloads.Argument> arguments = new ArrayList<>();
    for (final SyntaxNode node : nodes) {
      final Optional<SyntaxNode> lambda = lambdaOf(node);
      if (lambda.isPresent()) {
        arguments.add(
            new Overloads.Argument(Optional.empty(), lambdaParameters(lambda.get()).size(), false));
      } else if (isReference(node)) {
        arguments.add(new Overloads.Argument(Optional.empty(), -1, true));
      } else {
        arguments.add(
            Overloads.Argument.of(expressions.expression(node, ctx, Optional.empty()).type()));
      }
    }
    return arguments;
  }

  private static Optional<SyntaxNode> lambdaOf(final SyntaxNode expression) {
    return expression.children().size() == 1
            && "lambdaExpression".equals(expression.children().get(0).kind())
        ? Optional.of(expression.children().get(0))
        : Optional.empty();
  }

  private static boolean isReference(final SyntaxNode expression) {
    return expression.children().stream().anyMatch(child -> "::".equals(Syntax.token(child)));
  }

  private Typed resolve(
      final List<Hierarchy.Found<JvmMethod>> candidates,
      final Prepared prepared,
      final Ctx ctx,
      final boolean constructor) {
    final Optional<Hierarchy.Found<JvmMethod>> chosen =
        session.overloads.select(candidates, prepared.arguments());
    final Map<String, JType> bound =
        chosen
            .map(found -> session.inference.infer(found, prepared.arguments()))
            .orElseGet(HashMap::new);
    final List<JType> parameters =
        chosen
            .map(
                found ->
                    found.member().parameters().stream()
                        .map(parameter -> Types.substitute(parameter, found.bindings()))
                        .toList())
            .orElse(List.of());
    final Set<String> variables = new HashSet<>();
    chosen.ifPresent(
        found -> found.member().typeParameters().forEach(p -> variables.add(p.name())));
    for (int index = 0; index < prepared.nodes().size(); index++) {
      final Overloads.Argument argument = prepared.arguments().get(index);
      if (!argument.deferred()) {
        continue;
      }
      final Optional<JType> declared =
          index < parameters.size()
              ? Optional.of(Types.substitute(parameters.get(index), bound))
              : Optional.empty();
      deferred(prepared.nodes().get(index), declared, variables, bound, ctx);
    }
    if (chosen.isEmpty()) {
      return Typed.UNKNOWN;
    }
    final JType returned =
        Types.substitute(
            Types.substitute(chosen.get().member().returnType(), chosen.get().bindings()), bound);
    return cleanup(returned, variables).map(Typed::of).orElse(Typed.UNKNOWN).withCall(chosen.get());
  }

  /** Types a lambda or method reference argument against the parameter it is passed to. */
  private void deferred(
      final SyntaxNode node,
      final Optional<JType> declared,
      final Set<String> variables,
      final Map<String, JType> bound,
      final Ctx ctx) {
    final Optional<SyntaxNode> lambda = lambdaOf(node);
    if (lambda.isEmpty()) {
      expressions.expression(node, ctx, declared);
      return;
    }
    final Optional<Inference.Shape> shape = declared.flatMap(session.inference::shape);
    final List<Optional<JType>> parameters = new ArrayList<>();
    for (int index = 0; index < lambdaParameters(lambda.get()).size(); index++) {
      final int at = index;
      parameters.add(
          shape
              .filter(found -> at < found.parameters().size())
              .map(found -> found.parameters().get(at))
              .flatMap(type -> cleanup(type, variables)));
    }
    final Optional<JType> body = lambdaBody(lambda.get(), parameters, ctx);
    shape.ifPresent(
        found ->
            body.ifPresent(
                type -> session.inference.unify(found.returns(), type, variables, bound)));
    session.record(node, Typed.of(declared));
    session.record(lambda.get(), Typed.of(declared));
  }

  private static Optional<JType> cleanup(final JType type, final Set<String> variables) {
    if (type instanceof JType.TypeVariable variable && variables.contains(variable.name())) {
      return Optional.empty();
    }
    if (type instanceof JType.ClassType named) {
      final List<JType> arguments = new ArrayList<>();
      for (final JType argument : named.arguments()) {
        final Optional<JType> clean = cleanup(argument, variables);
        if (clean.isEmpty()) {
          return Optional.of(JType.ClassType.of(named.name()));
        }
        arguments.add(clean.get());
      }
      return Optional.of(new JType.ClassType(named.name(), arguments));
    }
    if (type instanceof JType.ArrayType array) {
      return cleanup(array.component(), variables).map(JType.ArrayType::new);
    }
    if (type instanceof JType.Wildcard wildcard && wildcard.bound() != null) {
      return cleanup(wildcard.bound(), variables)
          .map(bound -> new JType.Wildcard(bound, wildcard.upper()));
    }
    return Optional.of(type);
  }

  /** Types a lambda that is not an argument, against the type it is assigned to. */
  Typed lambda(final SyntaxNode node, final Ctx ctx, final Optional<JType> target) {
    final Optional<Inference.Shape> shape = target.flatMap(session.inference::shape);
    final List<Optional<JType>> parameters = new ArrayList<>();
    for (int index = 0; index < lambdaParameters(node).size(); index++) {
      final int at = index;
      parameters.add(
          shape
              .filter(found -> at < found.parameters().size())
              .map(found -> found.parameters().get(at)));
    }
    lambdaBody(node, parameters, ctx);
    final Typed typed = Typed.of(target);
    session.record(node, typed);
    return typed;
  }

  private List<SyntaxNode> lambdaParameters(final SyntaxNode lambda) {
    final List<SyntaxNode> found = new ArrayList<>();
    Syntax.child(lambda, "lambdaParameters")
        .ifPresent(
            parameters -> {
              for (final SyntaxNode child : parameters.children()) {
                if (IDENTIFIER.equals(child.kind())) {
                  found.add(child);
                } else if ("formalParameterList".equals(child.kind())) {
                  found.addAll(Syntax.children(child, "formalParameter"));
                } else if ("lambdaLVTIList".equals(child.kind())) {
                  found.addAll(Syntax.children(child, "lambdaLVTIParameter"));
                }
              }
            });
    return found;
  }

  private Optional<JType> lambdaBody(
      final SyntaxNode lambda, final List<Optional<JType>> parameterTypes, final Ctx ctx) {
    final List<SyntaxNode> parameters = lambdaParameters(lambda);
    for (int index = 0; index < parameters.size(); index++) {
      final SyntaxNode parameter = parameters.get(index);
      final SyntaxNode name =
          IDENTIFIER.equals(parameter.kind())
              ? parameter
              : Syntax.find(parameter, IDENTIFIER).orElse(parameter);
      final Optional<JType> explicit =
          Syntax.child(parameter, "typeType")
              .flatMap(written -> JavaTypes.type(written, ctx.scope()));
      final Optional<JType> inferred = parameterTypes.get(index);
      session.variable(name, explicit.or(() -> inferred));
    }
    final Optional<SyntaxNode> body = Syntax.child(lambda, "lambdaBody");
    if (body.isEmpty()) {
      return Optional.empty();
    }
    final Optional<SyntaxNode> expression = Syntax.child(body.get(), EXPRESSION);
    if (expression.isPresent()) {
      return expressions.expression(expression.get(), ctx, Optional.empty()).type();
    }
    final List<Optional<JType>> returns = new ArrayList<>();
    typer.walk(body.get(), ctx.collecting(returns));
    Optional<JType> merged = returns.isEmpty() ? Optional.empty() : returns.get(0);
    for (final Optional<JType> next :
        returns.subList(Math.min(1, returns.size()), returns.size())) {
      merged = expressions.unify(merged, next);
    }
    return merged;
  }

  /** Types {@code new ...}: the {@code creator} node. */
  Typed creator(final SyntaxNode creator, final Ctx ctx, final Optional<JType> target) {
    final Optional<SyntaxNode> name = Syntax.child(creator, "createdName");
    if (name.isEmpty()) {
      typer.walkChildren(creator, ctx);
      return Typed.UNKNOWN;
    }
    final Optional<SyntaxNode> array = Syntax.child(creator, "arrayCreatorRest");
    if (array.isPresent()) {
      return arrayCreation(name.get(), array.get(), ctx);
    }
    final Optional<SyntaxNode> rest = Syntax.child(creator, "classCreatorRest");
    final Prepared prepared =
        rest.map(found -> prepare(found, ctx)).orElse(new Prepared(List.of(), List.of()));
    rest.flatMap(found -> Syntax.child(found, "classBody"))
        .ifPresent(body -> typer.walkChildren(body, ctx));
    final Optional<JType.ClassType> created = createdType(name.get(), ctx, target);
    if (created.isEmpty()) {
      return Typed.UNKNOWN;
    }
    final Typed constructed =
        resolve(
            session.hierarchy.methods(created.get(), JvmMethod.CONSTRUCTOR), prepared, ctx, true);
    final Typed typed = Typed.of(created.get());
    return constructed.call().map(typed::withCall).orElse(typed);
  }

  private Typed arrayCreation(final SyntaxNode name, final SyntaxNode rest, final Ctx ctx) {
    final Optional<JType> element =
        Syntax.child(name, "primitiveType")
            .<JType>map(p -> new JType.Primitive(Syntax.text(p)))
            .or(() -> createdType(name, ctx, Optional.empty()).map(JType.class::cast));
    typer.walkChildren(rest, ctx);
    final int dimensions =
        (int) rest.children().stream().filter(child -> "[".equals(Syntax.token(child))).count();
    return Typed.of(element.map(found -> JavaTypes.arrayOf(found, dimensions)));
  }

  private Optional<JType.ClassType> createdType(
      final SyntaxNode name, final Ctx ctx, final Optional<JType> target) {
    final List<String> segments = new ArrayList<>();
    Optional<SyntaxNode> arguments = Optional.empty();
    for (final SyntaxNode child : name.children()) {
      if (IDENTIFIER.equals(child.kind())) {
        segments.add(Syntax.text(child));
        arguments = Optional.empty();
      } else if ("typeArgumentsOrDiamond".equals(child.kind())) {
        arguments = Optional.of(child);
      }
    }
    final Optional<String> binary = ctx.scope().resolve(segments);
    if (binary.isEmpty()) {
      return Optional.empty();
    }
    final Optional<SyntaxNode> usedArguments = arguments;
    final Optional<SyntaxNode> written =
        arguments.flatMap(found -> Syntax.child(found, "typeArguments"));
    if (written.isPresent()) {
      return Optional.of(
          new JType.ClassType(binary.get(), JavaTypes.typeArguments(written.get(), ctx.scope())));
    }
    final JType.ClassType raw = JType.ClassType.of(binary.get());
    return Optional.of(usedArguments.isPresent() ? diamond(raw, target) : raw);
  }

  /** Infers the arguments of {@code new C<>()} from the type it is assigned to. */
  private JType.ClassType diamond(final JType.ClassType raw, final Optional<JType> target) {
    final Optional<JvmClass> owner = session.hierarchy.find(raw.name());
    if (owner.isEmpty() || !(target.orElse(null) instanceof JType.ClassType wanted)) {
      return raw;
    }
    final JType.ClassType self = session.self(raw.name());
    final Optional<JType.ClassType> view = session.hierarchy.asSuper(self, wanted.name());
    if (view.isEmpty() || view.get().arguments().size() != wanted.arguments().size()) {
      return raw;
    }
    final Set<String> variables = new HashSet<>();
    owner.get().typeParameters().forEach(parameter -> variables.add(parameter.name()));
    final Map<String, JType> bound = new HashMap<>();
    for (int index = 0; index < wanted.arguments().size(); index++) {
      session.inference.unify(
          view.get().arguments().get(index), wanted.arguments().get(index), variables, bound);
    }
    final List<JType> arguments = new ArrayList<>();
    for (final TypeParameter parameter : owner.get().typeParameters()) {
      if (!bound.containsKey(parameter.name())) {
        return raw;
      }
      arguments.add(bound.get(parameter.name()));
    }
    return new JType.ClassType(raw.name(), arguments);
  }
}
