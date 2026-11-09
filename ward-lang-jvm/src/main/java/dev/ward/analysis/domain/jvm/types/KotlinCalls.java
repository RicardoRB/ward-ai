package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.Nullness;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Types the member accesses and calls of Kotlin that resolve to declared members. */
final class KotlinCalls {
  private static final String POSTFIX = "postfixUnaryExpression";
  private static final String SIMPLE_IDENTIFIER = "simpleIdentifier";
  private static final String EXPRESSION = "expression";
  private static final String LAMBDA = "annotatedLambda";

  private final Session session;
  private final KotlinExpressions expressions;
  private final KotlinTyper typer;
  private final JavaCalls javaCalls;

  KotlinCalls(final Session session, final KotlinExpressions expressions, final KotlinTyper typer) {
    this.session = session;
    this.expressions = expressions;
    this.typer = typer;
    this.javaCalls = new JavaCalls(session);
  }

  /** The arguments of a call, and whether all of them were typed. */
  private record Arguments(List<Overloads.Argument> list, boolean complete) {}

  Typed postfix(final SyntaxNode node, final Optional<Typed> receiver, final Ctx ctx) {
    final List<SyntaxNode> parts = node.children();
    final boolean callFollows = parts.size() > 1 && callOf(parts.get(1)).isPresent();
    Typed current = head(parts, receiver, callFollows, ctx);
    for (final SyntaxNode operation : parts.stream().skip(callFollows ? 2 : 1).toList()) {
      final Optional<SyntaxNode> access = Syntax.find(operation, POSTFIX);
      if (access.isPresent() && Syntax.find(operation, "memberAccessOperator").isPresent()) {
        current = postfix(access.get(), Optional.of(current), ctx);
      }
    }
    // An operation such as !! or a call changes nullness, so only a bare name keeps its own.
    final Typed result = parts.size() > 1 ? current.withNullness(Nullness.UNKNOWN) : current;
    session.record(node, result);
    return result;
  }

  private Typed head(
      final List<SyntaxNode> parts,
      final Optional<Typed> receiver,
      final boolean callFollows,
      final Ctx ctx) {
    final Optional<String> written =
        parts.get(0).children().isEmpty()
            ? Optional.empty()
            : Syntax.child(parts.get(0), SIMPLE_IDENTIFIER).map(Syntax::text);
    if (receiver.isEmpty()) {
      return written.isPresent() && callFollows
          ? unqualified(written.get(), callOf(parts.get(1)).get(), ctx)
          : expressions.expr(parts.get(0), ctx);
    }
    if (written.isEmpty()) {
      return Typed.UNKNOWN;
    }
    return callFollows
        ? member(receiver.get(), written.get(), callOf(parts.get(1)).get(), ctx)
        : read(receiver.get(), written.get(), ctx);
  }

  private static Optional<SyntaxNode> callOf(final SyntaxNode operation) {
    return "postfixUnaryOperation".equals(operation.kind())
        ? Syntax.child(operation, "callSuffix")
        : Optional.empty();
  }

  private Arguments arguments(final SyntaxNode suffix, final Ctx ctx) {
    final List<Overloads.Argument> arguments = new ArrayList<>();
    Syntax.child(suffix, "valueArguments").stream()
        .flatMap(list -> Syntax.children(list, "valueArgument").stream())
        .map(argument -> Syntax.child(argument, EXPRESSION))
        .map(value -> value.map(found -> expressions.expr(found, ctx)).orElse(Typed.UNKNOWN))
        .forEach(typed -> arguments.add(Overloads.Argument.of(typed.type())));
    final Optional<SyntaxNode> lambda = Syntax.child(suffix, LAMBDA);
    lambda.ifPresent(found -> typer.walk(found, ctx));
    return new Arguments(arguments, lambda.isEmpty());
  }

  private Typed unqualified(final String name, final SyntaxNode suffix, final Ctx ctx) {
    final Arguments arguments = arguments(suffix, ctx);
    if (!arguments.complete()) {
      return Typed.UNKNOWN;
    }
    for (final String owner : ctx.scope().enclosing()) {
      final List<Hierarchy.Found<JvmMethod>> candidates =
          session.hierarchy.methods(session.self(owner), name);
      if (!candidates.isEmpty()) {
        return finish(candidates, arguments.list());
      }
    }
    return ctx.scope()
        .resolve(List.of(name))
        .map(JType.ClassType::of)
        .map(site -> construction(site, arguments.list()))
        .orElse(Typed.UNKNOWN);
  }

  private Typed construction(final JType.ClassType site, final List<Overloads.Argument> arguments) {
    final Typed call = finish(session.hierarchy.methods(site, JvmMethod.CONSTRUCTOR), arguments);
    final Typed typed = Typed.of(site);
    return call.call().map(typed::withCall).orElse(typed);
  }

  private Typed member(
      final Typed receiver, final String name, final SyntaxNode suffix, final Ctx ctx) {
    final Arguments arguments = arguments(suffix, ctx);
    final Optional<JType.ClassType> site = siteOf(receiver, ctx);
    if (!arguments.complete() || site.isEmpty()) {
      return Typed.UNKNOWN;
    }
    return finish(session.hierarchy.methods(site.get(), name), arguments.list());
  }

  private Optional<JType.ClassType> siteOf(final Typed receiver, final Ctx ctx) {
    return receiver.named().or(() -> receiver.type().flatMap(type -> javaCalls.siteOf(type, ctx)));
  }

  private Typed finish(
      final List<Hierarchy.Found<JvmMethod>> candidates, final List<Overloads.Argument> arguments) {
    final Optional<Hierarchy.Found<JvmMethod>> chosen =
        session.overloads.select(candidates, arguments);
    if (chosen.isEmpty()) {
      return Typed.UNKNOWN;
    }
    final Map<String, JType> bound = session.inference.infer(chosen.get(), arguments);
    final JType returned =
        Types.substitute(
            Types.substitute(chosen.get().member().returnType(), chosen.get().bindings()), bound);
    final boolean unresolved =
        Types.mentionsVariable(returned) && !chosen.get().member().typeParameters().isEmpty();
    return (unresolved ? Typed.UNKNOWN : Typed.of(returned)).withCall(chosen.get());
  }

  /** A property read: a field, a getter, or the method of that name that Kotlin maps to one. */
  private Typed read(final Typed receiver, final String name, final Ctx ctx) {
    final Optional<JType.ClassType> site = siteOf(receiver, ctx);
    if (site.isEmpty()) {
      return Typed.UNKNOWN;
    }
    final Optional<Typed> field =
        session
            .hierarchy
            .field(site.get(), name)
            .map(
                found ->
                    Typed.of(Types.substitute(found.member().type(), found.bindings()))
                        .withConstant(found.member().constant()));
    if (field.isPresent()) {
      return field.get();
    }
    if (receiver.named().isPresent()) {
      return session
          .hierarchy
          .memberClass(site.get().name(), name)
          .map(binary -> Typed.naming(JType.ClassType.of(binary)))
          .orElse(Typed.UNKNOWN);
    }
    final String suffix = Character.toUpperCase(name.charAt(0)) + name.substring(1);
    return List.of("get" + suffix, "is" + suffix, name).stream()
        .map(accessor -> finish(session.hierarchy.methods(site.get(), accessor), List.of()))
        .filter(Typed::known)
        .findFirst()
        .orElse(Typed.UNKNOWN);
  }
}
