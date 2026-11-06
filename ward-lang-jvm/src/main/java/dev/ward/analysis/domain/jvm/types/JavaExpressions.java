package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.Binding;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Types the expressions of Java: names, members, operators and literals. */
final class JavaExpressions {
  private static final String EXPRESSION = "expression";
  private static final String BOOLEAN = "boolean";
  private static final int TERNARY = 5;
  private static final int INDEX = 4;
  private static final int BINARY = 3;
  private static final Set<String> COMPARISONS =
      Set.of("<", ">", "<=", ">=", "==", "!=", "&&", "||");
  private static final Set<String> ASSIGNMENTS =
      Set.of("=", "+=", "-=", "*=", "/=", "&=", "|=", "^=", ">>=", ">>>=", "<<=", "%=");
  private static final Set<String> PREFIX = Set.of("+", "-", "++", "--", "~", "!");

  private final Session session;
  private final JavaTyper typer;
  private final JavaCalls calls;

  JavaExpressions(final Session session, final JavaTyper typer, final JavaCalls calls) {
    this.session = session;
    this.typer = typer;
    this.calls = calls;
  }

  /** Types {@code node}, an {@code expression}, and everything inside it. */
  Typed expression(final SyntaxNode node, final Ctx ctx, final Optional<JType> target) {
    final Typed typed = dispatch(node, ctx, target);
    session.record(node, typed);
    return typed;
  }

  private Typed dispatch(final SyntaxNode node, final Ctx ctx, final Optional<JType> target) {
    final List<SyntaxNode> parts = node.children();
    final SyntaxNode first = parts.get(0);
    if (parts.size() == 1) {
      return single(first, ctx, target);
    }
    if (EXPRESSION.equals(first.kind())) {
      return withLeft(node, parts, ctx, target);
    }
    final String lead = Syntax.token(first);
    if ("new".equals(lead)) {
      return calls.creator(parts.get(1), ctx, target);
    }
    if ("(".equals(lead)) {
      return cast(node, ctx);
    }
    if (PREFIX.contains(lead) && parts.size() == 2) {
      return unary(lead, expression(parts.get(1), ctx, Optional.empty()));
    }
    typer.walkChildren(node, ctx);
    return Typed.UNKNOWN;
  }

  private Typed single(final SyntaxNode child, final Ctx ctx, final Optional<JType> target) {
    return switch (child.kind()) {
      case "primary" -> primary(child, ctx);
      case "methodCall" -> calls.unqualified(child, ctx);
      case "lambdaExpression" -> calls.lambda(child, ctx, target);
      case "switchExpression" -> switchExpression(child, ctx);
      default -> {
        typer.walkChildren(child, ctx);
        yield Typed.UNKNOWN;
      }
    };
  }

  private Typed withLeft(
      final SyntaxNode node, final List<SyntaxNode> parts, final Ctx ctx, final Optional<JType> target) {
    final String operator = Syntax.token(parts.get(1));
    if (".".equals(operator)) {
      return member(parts, ctx);
    }
    if ("[".equals(operator) && parts.size() == INDEX) {
      return index(parts, ctx);
    }
    if ("::".equals(operator)) {
      expression(parts.get(0), ctx, Optional.empty());
      return Typed.of(target);
    }
    if (parts.size() == 2) {
      return expression(parts.get(0), ctx, Optional.empty());
    }
    if ("instanceof".equals(operator)) {
      expression(parts.get(0), ctx, Optional.empty());
      instanceOf(parts.get(2), ctx);
      return Typed.of(new JType.Primitive(BOOLEAN));
    }
    if ("?".equals(operator) && parts.size() == TERNARY) {
      return ternary(parts, ctx, target);
    }
    if (ASSIGNMENTS.contains(operator) && parts.size() == BINARY) {
      final Typed left = expression(parts.get(0), ctx, Optional.empty());
      expression(parts.get(2), ctx, left.type());
      return Typed.of(left.type());
    }
    return binary(parts, ctx);
  }

  private Typed primary(final SyntaxNode node, final Ctx ctx) {
    final List<SyntaxNode> parts = node.children();
    final SyntaxNode first = parts.get(0);
    if (parts.size() == 1 && "literal".equals(first.kind())) {
      return Literals.of(first)
          .map(literal -> Typed.of(literal.type()).withConstant(literal.value()))
          .orElse(Typed.UNKNOWN);
    }
    if (parts.size() == 1 && "identifier".equals(first.kind())) {
      return name(first, ctx);
    }
    if (parts.size() == BINARY && "(".equals(Syntax.token(first))) {
      return expression(parts.get(1), ctx, Optional.empty());
    }
    if (parts.size() == 1 && "this".equals(Syntax.token(first))) {
      return ctx.scope().enclosing().stream()
          .findFirst()
          .map(owner -> Typed.of(session.self(owner)))
          .orElse(Typed.UNKNOWN);
    }
    if (parts.size() == 1 && "super".equals(Syntax.token(first))) {
      return superType(ctx);
    }
    if (parts.size() == BINARY && "class".equals(Syntax.token(parts.get(2)))) {
      return classLiteral(parts.get(0), ctx);
    }
    typer.walkChildren(node, ctx);
    return Typed.UNKNOWN;
  }

  private Typed superType(final Ctx ctx) {
    return ctx.scope().enclosing().stream()
        .findFirst()
        .flatMap(session.hierarchy::find)
        .flatMap(owner -> owner.superclass())
        .map(Typed::of)
        .orElse(Typed.UNKNOWN);
  }

  private Typed classLiteral(final SyntaxNode written, final Ctx ctx) {
    final Optional<JType> type =
        Syntax.child(written, "typeType").flatMap(found -> JavaTypes.type(found, ctx.scope()));
    return type.map(found -> Typed.of(new JType.ClassType("java.lang.Class", List.of(Types.boxed(found)))))
        .orElse(Typed.UNKNOWN);
  }

  /** Resolves a simple name used as an expression. */
  Typed name(final SyntaxNode identifier, final Ctx ctx) {
    final String name = Syntax.text(identifier);
    final Optional<Binding> binding = session.binding(identifier);
    if (binding.isPresent() && Session.isValue(binding.get())) {
      final Optional<Optional<JType>> declared = session.variableAt(binding.get());
      return declared.map(Typed::of).orElse(Typed.UNKNOWN);
    }
    final Optional<Typed> inherited = fieldOfEnclosing(name, ctx);
    if (inherited.isPresent()) {
      return inherited.get();
    }
    for (final String owner : ctx.scope().staticOwners(name)) {
      final Optional<Typed> imported = field(JType.ClassType.of(owner), name, ctx);
      if (imported.isPresent()) {
        return imported.get();
      }
    }
    return ctx.scope()
        .resolve(List.of(name))
        .map(binary -> Typed.naming(JType.ClassType.of(binary)))
        .orElseGet(() -> Typed.pkg(name));
  }

  private Optional<Typed> fieldOfEnclosing(final String name, final Ctx ctx) {
    for (final String owner : ctx.scope().enclosing()) {
      final Optional<Typed> found = field(session.self(owner), name, ctx);
      if (found.isPresent()) {
        return found;
      }
    }
    return Optional.empty();
  }

  private Optional<Typed> field(final JType.ClassType site, final String name, final Ctx ctx) {
    return session.hierarchy
        .field(site, name)
        .map(
            found -> {
              final JType type = Types.substitute(found.member().type(), found.bindings());
              return Typed.of(type).withConstant(found.member().constant());
            });
  }

  private Typed member(final List<SyntaxNode> parts, final Ctx ctx) {
    final Typed qualifier = expression(parts.get(0), ctx, Optional.empty());
    final SyntaxNode right = parts.get(2);
    if (parts.size() > BINARY) {
      typer.walkChildren(parts.get(parts.size() - 1), ctx);
      return Typed.UNKNOWN;
    }
    if ("methodCall".equals(right.kind())) {
      return calls.qualified(qualifier, right, ctx);
    }
    if (!"identifier".equals(right.kind())) {
      return Typed.UNKNOWN;
    }
    final String name = Syntax.text(right);
    if (qualifier.packageName().isPresent()) {
      final String dotted = qualifier.packageName().get() + '.' + name;
      return ctx.scope().binaryOf(dotted).map(binary -> Typed.naming(JType.ClassType.of(binary))).orElseGet(() -> Typed.pkg(dotted));
    }
    if (qualifier.named().isPresent()) {
      return staticMember(qualifier.named().get(), name, ctx);
    }
    return qualifier.type().map(type -> valueMember(type, name, ctx)).orElse(Typed.UNKNOWN);
  }

  private Typed staticMember(final JType.ClassType owner, final String name, final Ctx ctx) {
    final Optional<Typed> field = field(owner, name, ctx);
    if (field.isPresent()) {
      return field.get();
    }
    return session.hierarchy
        .memberClass(owner.name(), name)
        .map(binary -> Typed.naming(JType.ClassType.of(binary)))
        .orElse(Typed.UNKNOWN);
  }

  private Typed valueMember(final JType type, final String name, final Ctx ctx) {
    if (type instanceof JType.ArrayType && "length".equals(name)) {
      return Typed.of(new JType.Primitive("int"));
    }
    return calls.siteOf(type, ctx).flatMap(site -> field(site, name, ctx)).orElse(Typed.UNKNOWN);
  }

  private Typed index(final List<SyntaxNode> parts, final Ctx ctx) {
    final Typed array = expression(parts.get(0), ctx, Optional.empty());
    expression(parts.get(2), ctx, Optional.empty());
    return array.type().filter(JType.ArrayType.class::isInstance).map(found -> Typed.of(((JType.ArrayType) found).component())).orElse(Typed.UNKNOWN);
  }

  private Typed cast(final SyntaxNode node, final Ctx ctx) {
    final List<SyntaxNode> parts = node.children();
    final Optional<JType> type =
        Syntax.child(node, "typeType").flatMap(written -> JavaTypes.type(written, ctx.scope()));
    expression(parts.get(parts.size() - 1), ctx, type);
    return Typed.of(type);
  }

  private void instanceOf(final SyntaxNode pattern, final Ctx ctx) {
    final Optional<SyntaxNode> written = "typeType".equals(pattern.kind()) ? Optional.of(pattern) : Syntax.child(pattern, "typeType");
    if (written.isEmpty()) {
      return;
    }
    final Optional<SyntaxNode> classType = Syntax.find(written.get(), "classType");
    if (classType.isPresent()) {
      final List<SyntaxNode> names = Syntax.children(classType.get(), "typeIdentifier");
      final SyntaxNode last = names.get(names.size() - 1);
      if (names.size() > 1 && session.isDeclaration(last)) {
        final List<String> segments = names.subList(0, names.size() - 1).stream().map(Syntax::text).toList();
        session.variable(last, ctx.scope().resolve(segments).map(JType.ClassType::of));
        return;
      }
    }
    typer.declarePattern(pattern, ctx);
  }

  private Typed ternary(final List<SyntaxNode> parts, final Ctx ctx, final Optional<JType> target) {
    expression(parts.get(0), ctx, Optional.empty());
    final Typed yes = expression(parts.get(2), ctx, target);
    final Typed no = expression(parts.get(4), ctx, target);
    return Typed.of(unify(yes.type(), no.type()));
  }

  /** Returns the common type of two branches, empty when they have none that is known. */
  Optional<JType> unify(final Optional<JType> first, final Optional<JType> second) {
    if (first.isEmpty() || second.isEmpty()) {
      return Optional.empty();
    }
    final JType a = first.get();
    final JType b = second.get();
    if (a.equals(b)) {
      return first;
    }
    if (Types.isNull(a)) {
      return Optional.of(Types.boxed(b));
    }
    if (Types.isNull(b)) {
      return Optional.of(Types.boxed(a));
    }
    final JType numeric = Types.promote(a, b);
    if (numeric != null && (Types.isPrimitive(a) || Types.isPrimitive(b))) {
      return Optional.of(numeric);
    }
    return commonSuperclass(Types.boxed(a), Types.boxed(b));
  }

  private Optional<JType> commonSuperclass(final JType a, final JType b) {
    if (!(a instanceof JType.ClassType first)) {
      return Optional.empty();
    }
    JType.ClassType current = first;
    while (true) {
      if (session.hierarchy.assignable(b, JType.ClassType.of(current.name()), false)) {
        return Optional.of(JType.ClassType.of(current.name()));
      }
      final Optional<JType.ClassType> parent =
          session.hierarchy.find(current.name()).flatMap(JvmClass::superclass);
      if (parent.isEmpty()) {
        return Optional.empty();
      }
      current = parent.get();
    }
  }

  private Typed unary(final String operator, final Typed operand) {
    if ("!".equals(operator)) {
      return Typed.of(new JType.Primitive(BOOLEAN)).withConstant(operand.constant().map(v -> v instanceof Boolean flag ? (Object) !flag : v).filter(Boolean.class::isInstance));
    }
    if (operand.type().isEmpty()) {
      return Typed.UNKNOWN;
    }
    if ("++".equals(operator) || "--".equals(operator)) {
      return Typed.of(operand.type());
    }
    final JType promoted = Types.promote(operand.type().get(), new JType.Primitive("int"));
    final Typed typed = promoted == null ? Typed.UNKNOWN : Typed.of(promoted);
    return "-".equals(operator) ? typed.withConstant(operand.constant().flatMap(Constants::negate)) : typed;
  }

  private Typed binary(final List<SyntaxNode> parts, final Ctx ctx) {
    final Typed left = expression(parts.get(0), ctx, Optional.empty());
    final Typed right = expression(parts.get(parts.size() - 1), ctx, Optional.empty());
    final String operator = parts.stream().skip(1).limit(parts.size() - 2L).map(Syntax::token).reduce("", String::concat);
    if (COMPARISONS.contains(operator)) {
      return Typed.of(new JType.Primitive(BOOLEAN));
    }
    if (left.type().isEmpty() || right.type().isEmpty()) {
      return Typed.UNKNOWN;
    }
    final JType a = left.type().get();
    final JType b = right.type().get();
    if ("+".equals(operator) && (isString(a) || isString(b))) {
      return Typed.of(JType.ClassType.of(Types.STRING)).withConstant(Operators.concat(left.constant(), right.constant()));
    }
    if (("&".equals(operator) || "|".equals(operator) || "^".equals(operator))
        && Types.unboxed(a).equals(new JType.Primitive(BOOLEAN))) {
      return Typed.of(new JType.Primitive(BOOLEAN));
    }
    if (operator.startsWith("<<") || operator.startsWith(">>")) {
      return Optional.ofNullable(Types.promote(a, new JType.Primitive("int"))).map(Typed::of).orElse(Typed.UNKNOWN);
    }
    final JType promoted = Types.promote(a, b);
    if (promoted == null) {
      return Typed.UNKNOWN;
    }
    return Typed.of(promoted).withConstant(Operators.arithmetic(operator, left.constant(), right.constant(), promoted));
  }

  private static boolean isString(final JType type) {
    return type instanceof JType.ClassType named && Types.STRING.equals(named.name());
  }

  private Typed switchExpression(final SyntaxNode node, final Ctx ctx) {
    final List<Optional<JType>> results = new java.util.ArrayList<>();
    typer.walkSwitch(node, ctx, results);
    if (results.isEmpty()) {
      return Typed.UNKNOWN;
    }
    Optional<JType> merged = results.get(0);
    for (final Optional<JType> next : results.subList(1, results.size())) {
      merged = unify(merged, next);
    }
    return Typed.of(merged);
  }
}
