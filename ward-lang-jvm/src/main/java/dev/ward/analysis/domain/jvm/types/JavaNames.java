package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.Binding;
import java.util.List;
import java.util.Optional;

/** Types the names of Java: simple names, member selection, class literals and patterns. */
final class JavaNames {
  private static final String IDENTIFIER = "identifier";
  private static final String METHOD_CALL = "methodCall";
  private static final int SELECTION = 3;

  private final Session session;
  private final JavaExpressions expressions;
  private final JavaCalls calls;
  private final JavaTyper typer;

  JavaNames(
      final Session session,
      final JavaExpressions expressions,
      final JavaCalls calls,
      final JavaTyper typer) {
    this.session = session;
    this.expressions = expressions;
    this.calls = calls;
    this.typer = typer;
  }

  Typed superType(final Ctx ctx) {
    return ctx.scope().enclosing().stream()
        .findFirst()
        .flatMap(session.hierarchy::find)
        .flatMap(JvmClass::superclass)
        .map(Typed::of)
        .orElse(Typed.UNKNOWN);
  }

  Typed classLiteral(final SyntaxNode written, final Ctx ctx) {
    final Optional<JType> type =
        Syntax.child(written, "typeType").flatMap(found -> JavaTypes.type(found, ctx.scope()));
    return type.map(
            found -> Typed.of(new JType.ClassType("java.lang.Class", List.of(Types.boxed(found)))))
        .orElse(Typed.UNKNOWN);
  }

  /** Resolves a simple name used as an expression. */
  Typed name(final SyntaxNode identifier, final Ctx ctx) {
    final String name = Syntax.text(identifier);
    final Optional<Binding> binding = session.binding(identifier);
    if (binding.isPresent() && ScopeIndex.isValue(binding.get())) {
      return session.variableAt(binding.get()).map(Typed::of).orElse(Typed.UNKNOWN);
    }
    final Optional<Typed> inherited = fieldOfEnclosing(name, ctx);
    if (inherited.isPresent()) {
      return inherited.get();
    }
    final Optional<Typed> imported =
        ctx.scope().staticOwners(name).stream()
            .map(owner -> field(JType.ClassType.of(owner), name))
            .flatMap(Optional::stream)
            .findFirst();
    return imported.orElseGet(
        () ->
            ctx.scope()
                .resolve(List.of(name))
                .map(binary -> Typed.naming(JType.ClassType.of(binary)))
                .orElseGet(() -> Typed.pkg(name)));
  }

  private Optional<Typed> fieldOfEnclosing(final String name, final Ctx ctx) {
    return ctx.scope().enclosing().stream()
        .map(owner -> field(session.self(owner), name))
        .flatMap(Optional::stream)
        .findFirst();
  }

  private Optional<Typed> field(final JType.ClassType site, final String name) {
    return session
        .hierarchy
        .field(site, name)
        .map(
            found -> {
              final JType type = Types.substitute(found.member().type(), found.bindings());
              return Typed.of(type).withConstant(found.member().constant());
            });
  }

  /** Types {@code qualifier.name}, {@code qualifier.name(args)} and the like. */
  Typed member(final List<SyntaxNode> parts, final Ctx ctx) {
    final Typed qualifier = expressions.expression(parts.get(0), ctx, Optional.empty());
    final SyntaxNode right = parts.get(2);
    if (parts.size() > SELECTION) {
      typer.walkChildren(parts.get(parts.size() - 1), ctx);
      return Typed.UNKNOWN;
    }
    if (METHOD_CALL.equals(right.kind())) {
      return calls.qualified(qualifier, right, ctx);
    }
    if (!IDENTIFIER.equals(right.kind())) {
      return Typed.UNKNOWN;
    }
    final String name = Syntax.text(right);
    if (qualifier.packageName().isPresent()) {
      final String dotted = qualifier.packageName().get() + '.' + name;
      return ctx.scope()
          .binaryOf(dotted)
          .map(binary -> Typed.naming(JType.ClassType.of(binary)))
          .orElseGet(() -> Typed.pkg(dotted));
    }
    if (qualifier.named().isPresent()) {
      return staticMember(qualifier.named().get(), name);
    }
    return qualifier.type().map(type -> valueMember(type, name, ctx)).orElse(Typed.UNKNOWN);
  }

  private Typed staticMember(final JType.ClassType owner, final String name) {
    return field(owner, name)
        .or(
            () ->
                session
                    .hierarchy
                    .memberClass(owner.name(), name)
                    .map(binary -> Typed.naming(JType.ClassType.of(binary))))
        .orElse(Typed.UNKNOWN);
  }

  private Typed valueMember(final JType type, final String name, final Ctx ctx) {
    if (type instanceof JType.ArrayType && "length".equals(name)) {
      return Typed.of(new JType.Primitive("int"));
    }
    return calls.siteOf(type, ctx).flatMap(site -> field(site, name)).orElse(Typed.UNKNOWN);
  }

  /** Records the variable that {@code x instanceof T v} declares, or types a plain type. */
  void instanceOf(final SyntaxNode pattern, final Ctx ctx) {
    final Optional<SyntaxNode> written =
        "typeType".equals(pattern.kind())
            ? Optional.of(pattern)
            : Syntax.child(pattern, "typeType");
    if (written.isEmpty()) {
      return;
    }
    final List<SyntaxNode> identifiers =
        Syntax.find(written.get(), "classType")
            .map(found -> Syntax.children(found, "typeIdentifier"))
            .orElse(List.of());
    if (identifiers.size() > 1 && session.isDeclaration(identifiers.get(identifiers.size() - 1))) {
      final List<String> segments =
          identifiers.subList(0, identifiers.size() - 1).stream().map(Syntax::text).toList();
      session.variable(
          identifiers.get(identifiers.size() - 1),
          ctx.scope().resolve(segments).map(JType.ClassType::of));
      return;
    }
    typer.declarePattern(pattern, ctx);
  }
}
