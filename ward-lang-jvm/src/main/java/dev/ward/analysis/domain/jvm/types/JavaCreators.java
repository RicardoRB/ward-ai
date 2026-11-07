package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Types object and array creation of Java. */
final class JavaCreators {
  private static final String IDENTIFIER = "identifier";
  private static final String BRACKET = "[";
  private static final String DIAMOND = "typeArgumentsOrDiamond";

  private final Session session;
  private final JavaCalls calls;
  private final JavaTyper typer;

  JavaCreators(final Session session, final JavaCalls calls, final JavaTyper typer) {
    this.session = session;
    this.calls = calls;
    this.typer = typer;
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
    final JavaCalls.Prepared prepared =
        rest.map(found -> calls.prepare(found, ctx))
            .orElse(new JavaCalls.Prepared(List.of(), List.of()));
    rest.flatMap(found -> Syntax.child(found, "classBody"))
        .ifPresent(body -> typer.walkChildren(body, ctx));
    final Optional<JType.ClassType> created = createdType(name.get(), ctx, target);
    if (created.isEmpty()) {
      return Typed.UNKNOWN;
    }
    final Typed constructed =
        calls.resolve(
            session.hierarchy.methods(created.get(), JvmMethod.CONSTRUCTOR), prepared, ctx);
    final Typed typed = Typed.of(created.get());
    return constructed.call().map(typed::withCall).orElse(typed);
  }

  private Typed arrayCreation(final SyntaxNode name, final SyntaxNode rest, final Ctx ctx) {
    final Optional<JType> element =
        Syntax.child(name, "primitiveType")
            .<JType>map(primitive -> new JType.Primitive(Syntax.text(primitive)))
            .or(() -> createdType(name, ctx, Optional.empty()).map(JType.class::cast));
    typer.walkChildren(rest, ctx);
    final int dimensions =
        (int) rest.children().stream().filter(child -> BRACKET.equals(Syntax.token(child))).count();
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
      } else if (DIAMOND.equals(child.kind())) {
        arguments = Optional.of(child);
      }
    }
    final Optional<SyntaxNode> usedArguments = arguments;
    return ctx.scope()
        .resolve(segments)
        .map(binary -> withArguments(binary, usedArguments, ctx, target));
  }

  private JType.ClassType withArguments(
      final String binary,
      final Optional<SyntaxNode> arguments,
      final Ctx ctx,
      final Optional<JType> target) {
    final Optional<SyntaxNode> written =
        arguments.flatMap(found -> Syntax.child(found, "typeArguments"));
    if (written.isPresent()) {
      return new JType.ClassType(binary, JavaTypes.typeArguments(written.get(), ctx.scope()));
    }
    final JType.ClassType raw = JType.ClassType.of(binary);
    return arguments.isPresent() ? diamond(raw, target) : raw;
  }

  /** Infers the arguments of {@code new C<>()} from the type it is assigned to. */
  private JType.ClassType diamond(final JType.ClassType raw, final Optional<JType> target) {
    final Optional<JvmClass> owner = session.hierarchy.find(raw.name());
    if (owner.isEmpty() || !(target.orElse(null) instanceof JType.ClassType wanted)) {
      return raw;
    }
    final Optional<JType.ClassType> view =
        session.hierarchy.asSuper(session.self(raw.name()), wanted.name());
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
    final List<JType> arguments =
        owner.get().typeParameters().stream().map(p -> bound.get(p.name())).toList();
    return arguments.stream().allMatch(java.util.Objects::nonNull)
        ? new JType.ClassType(raw.name(), arguments)
        : raw;
  }
}
