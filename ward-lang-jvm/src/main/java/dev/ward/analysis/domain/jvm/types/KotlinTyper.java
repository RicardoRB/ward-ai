package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Types the expressions of Kotlin that resolve to declared types: literals, names, property reads,
 * constructor calls and calls of methods whose parameters and results have declared types. What
 * needs full Kotlin inference, such as lambdas, extension functions and flow typing, stays unknown.
 */
final class KotlinTyper {
  private static final String POSTFIX = "postfixUnaryExpression";
  private static final String SIMPLE_IDENTIFIER = "simpleIdentifier";
  private static final String EXPRESSION = "expression";
  private static final Set<String> BOOLEAN_OPERATIONS =
      Set.of("comparison", "equalityComparison", "conjunction", "disjunction");
  private static final Set<String> ARITHMETIC = Set.of("additiveExpression", "multiplicativeExpression");

  private final Session session;
  private final JavaCalls calls;

  KotlinTyper(final Session session) {
    this.session = session;
    this.calls = new JavaCalls(session);
  }

  void run(final KotlinUnitModel model, final SyntaxNode tree) {
    final Ctx root = new Ctx(model.scope(), Map.of(), List.of(), Optional.empty());
    for (final KotlinUnitModel.Declared declared : model.classes()) {
      final DeclInfo info =
          new DeclInfo(Optional.of(declared.jvm()), Optional.empty(), Optional.empty(), Optional.empty(), List.of());
      session.declare(declared.node(), info);
      final Ctx inside = root.withScope(declared.scope(), declared.jvm().typeParameters());
      registerParameters(declared.node(), inside);
      declared.members().forEach(member -> registerMember(member, inside));
      walk(declared.node(), inside);
    }
  }

  private void registerParameters(final SyntaxNode declaration, final Ctx ctx) {
    Syntax.child(declaration, "primaryConstructor")
        .flatMap(constructor -> Syntax.child(constructor, "classParameters"))
        .stream()
        .flatMap(list -> Syntax.children(list, "classParameter").stream())
        .forEach(parameter -> declare(parameter, ctx));
  }

  private void registerMember(final SyntaxNode member, final Ctx ctx) {
    if ("propertyDeclaration".equals(member.kind())) {
      Syntax.child(member, "variableDeclaration")
          .ifPresent(variable -> declareWritten(variable, ctx));
    }
  }

  private void declare(final SyntaxNode holder, final Ctx ctx) {
    Syntax.child(holder, SIMPLE_IDENTIFIER)
        .ifPresent(
            name ->
                session.variable(
                    name,
                    Syntax.child(holder, "type").flatMap(written -> KotlinTypes.type(written, ctx.scope()))));
  }

  private void declareWritten(final SyntaxNode variable, final Ctx ctx) {
    final Optional<JType> written =
        Syntax.child(variable, "type").flatMap(type -> KotlinTypes.type(type, ctx.scope()));
    if (written.isPresent()) {
      Syntax.child(variable, SIMPLE_IDENTIFIER).ifPresent(name -> session.variable(name, written));
    }
  }

  private void walk(final SyntaxNode node, final Ctx ctx) {
    switch (node.kind()) {
      case EXPRESSION -> expr(node, ctx);
      case "propertyDeclaration" -> property(node, ctx);
      case "functionDeclaration" -> function(node, ctx);
      case "forExpression" -> forLoop(node, ctx);
      default -> node.children().forEach(child -> walk(child, ctx));
    }
  }

  private void property(final SyntaxNode node, final Ctx ctx) {
    final Optional<SyntaxNode> variable = Syntax.child(node, "variableDeclaration");
    final Optional<SyntaxNode> value = Syntax.child(node, EXPRESSION);
    final Typed initial = value.map(found -> expr(found, ctx)).orElse(Typed.UNKNOWN);
    if (variable.isPresent()) {
      final Optional<JType> written =
          Syntax.child(variable.get(), "type").flatMap(type -> KotlinTypes.type(type, ctx.scope()));
      Syntax.child(variable.get(), SIMPLE_IDENTIFIER)
          .ifPresent(name -> session.variable(name, written.or(() -> initial.type().filter(t -> !Types.isNull(t)))));
    }
  }

  private void function(final SyntaxNode node, final Ctx ctx) {
    final TypeScope scope = ctx.scope();
    Syntax.children(node, "functionValueParameters").stream()
        .flatMap(list -> Syntax.children(list, "functionValueParameter").stream())
        .flatMap(holder -> Syntax.child(holder, "parameter").stream())
        .forEach(parameter -> declare(parameter, ctx));
    KotlinUnitModel.function(node, scope);
    node.children().stream()
        .filter(child -> "functionBody".equals(child.kind()))
        .forEach(body -> walk(body, ctx));
  }

  private void forLoop(final SyntaxNode node, final Ctx ctx) {
    final Optional<SyntaxNode> source = Syntax.child(node, EXPRESSION);
    final Typed iterable = source.map(found -> expr(found, ctx)).orElse(Typed.UNKNOWN);
    Syntax.child(node, "variableDeclaration")
        .flatMap(variable -> Syntax.child(variable, SIMPLE_IDENTIFIER))
        .ifPresent(name -> session.variable(name, element(iterable.type())));
    node.children().stream().filter(child -> "controlStructureBody".equals(child.kind())).forEach(body -> walk(body, ctx));
  }

  private Optional<JType> element(final Optional<JType> iterable) {
    if (iterable.isPresent() && iterable.get() instanceof JType.ArrayType array) {
      return Optional.of(array.component());
    }
    if (iterable.isPresent() && iterable.get() instanceof JType.ClassType site) {
      return session.hierarchy
          .asSuper(site, "java.lang.Iterable")
          .filter(view -> view.arguments().size() == 1)
          .map(view -> view.arguments().get(0));
    }
    return Optional.empty();
  }

  private Typed expr(final SyntaxNode node, final Ctx ctx) {
    final Typed typed = dispatch(node, ctx);
    session.record(node, typed);
    return typed;
  }

  private Typed dispatch(final SyntaxNode node, final Ctx ctx) {
    if (POSTFIX.equals(node.kind())) {
      return postfix(node, Optional.empty(), ctx);
    }
    if ("atomicExpression".equals(node.kind())) {
      return atomic(node, ctx);
    }
    if (node.children().size() == 1 && !node.children().get(0).children().isEmpty()) {
      return expr(node.children().get(0), ctx);
    }
    final List<Typed> operands = new ArrayList<>();
    for (final SyntaxNode child : node.children()) {
      if (!child.children().isEmpty()) {
        final Typed typed = expr(child, ctx);
        if (!child.kind().toLowerCase(java.util.Locale.ROOT).contains("operat")) {
          operands.add(typed);
        }
      }
    }
    if (BOOLEAN_OPERATIONS.contains(node.kind())) {
      return Typed.of(new JType.Primitive("boolean"));
    }
    return ARITHMETIC.contains(node.kind()) ? arithmetic(node, operands) : Typed.UNKNOWN;
  }

  private Typed arithmetic(final SyntaxNode node, final List<Typed> operands) {
    if (operands.isEmpty() || operands.stream().anyMatch(operand -> operand.type().isEmpty())) {
      return Typed.UNKNOWN;
    }
    final boolean text = operands.stream().anyMatch(operand -> operand.type().get().equals(JType.ClassType.of(Types.STRING)));
    if (text && "additiveExpression".equals(node.kind())) {
      return Typed.of(JType.ClassType.of(Types.STRING));
    }
    JType result = operands.get(0).type().get();
    for (final Typed next : operands.subList(1, operands.size())) {
      final JType promoted = Types.promote(result, next.type().get());
      if (promoted == null) {
        return Typed.UNKNOWN;
      }
      result = promoted;
    }
    return Typed.of(result);
  }

  private Typed atomic(final SyntaxNode node, final Ctx ctx) {
    final SyntaxNode head = node.children().get(0);
    return switch (head.kind()) {
      case "literalConstant" -> literal(head);
      case SIMPLE_IDENTIFIER -> name(head, ctx);
      case "parenthesizedExpression" ->
          Syntax.child(head, EXPRESSION).map(inner -> expr(inner, ctx)).orElse(Typed.UNKNOWN);
      default -> {
        walk(head, ctx);
        yield Typed.UNKNOWN;
      }
    };
  }

  private static Typed literal(final SyntaxNode node) {
    final String text = Syntax.text(node);
    if (text.startsWith("\"")) {
      final boolean plain = !text.contains("$");
      return Typed.of(JType.ClassType.of(Types.STRING))
          .withConstant(plain && text.length() >= 2 && !text.startsWith("\"\"\"")
              ? Optional.of(text.substring(1, text.length() - 1)) : Optional.empty());
    }
    if ("true".equals(text) || "false".equals(text)) {
      return Typed.of(new JType.Primitive("boolean")).withConstant(Optional.of(Boolean.valueOf(text)));
    }
    if ("null".equals(text)) {
      return Typed.of(JType.ClassType.of(Types.NULL));
    }
    if (text.startsWith("'")) {
      return Typed.of(new JType.Primitive("char"));
    }
    if (text.matches("[0-9_]+[lL]")) {
      return Typed.of(new JType.Primitive("long"));
    }
    if (text.matches("[0-9_]+")) {
      return Typed.of(new JType.Primitive("int")).withConstant(Optional.of(Long.parseLong(text.replace("_", ""))));
    }
    if (text.matches("[0-9_.eE+-]+[fF]")) {
      return Typed.of(new JType.Primitive("float"));
    }
    return text.matches("[0-9_]*\\.[0-9_eE+-]+") ? Typed.of(new JType.Primitive("double")) : Typed.UNKNOWN;
  }

  private Typed name(final SyntaxNode identifier, final Ctx ctx) {
    final String name = Syntax.text(identifier);
    final Optional<dev.ward.analysis.domain.scope.Binding> binding = session.binding(identifier);
    if (binding.isPresent() && Session.isValue(binding.get())) {
      return session.variableAt(binding.get()).map(Typed::of).orElse(Typed.UNKNOWN);
    }
    for (final String owner : ctx.scope().enclosing()) {
      final Optional<Typed> field = session.hierarchy.field(session.self(owner), name)
          .map(found -> Typed.of(Types.substitute(found.member().type(), found.bindings())));
      if (field.isPresent()) {
        return field.get();
      }
    }
    return ctx.scope().resolve(List.of(name))
        .map(binary -> Typed.naming(JType.ClassType.of(binary)))
        .orElseGet(() -> Typed.pkg(name));
  }

  private Typed postfix(final SyntaxNode node, final Optional<Typed> receiver, final Ctx ctx) {
    final List<SyntaxNode> parts = node.children();
    final boolean callFollows = parts.size() > 1 && callOf(parts.get(1)).isPresent();
    final Optional<String> written = parts.get(0).children().isEmpty() ? Optional.empty()
        : Syntax.child(parts.get(0), SIMPLE_IDENTIFIER).map(Syntax::text);
    Typed current;
    if (receiver.isEmpty()) {
      current = written.isPresent() && callFollows
          ? unqualified(written.get(), callOf(parts.get(1)).get(), ctx)
          : expr(parts.get(0), ctx);
    } else if (written.isPresent()) {
      current = callFollows
          ? member(receiver.get(), written.get(), callOf(parts.get(1)).get(), ctx)
          : property(receiver.get(), written.get(), ctx);
    } else {
      current = Typed.UNKNOWN;
    }
    for (final SyntaxNode operation : parts.subList(callFollows ? 2 : 1, parts.size())) {
      final Optional<SyntaxNode> access = Syntax.find(operation, POSTFIX);
      current = access.isPresent() && Syntax.find(operation, "memberAccessOperator").isPresent()
          ? postfix(access.get(), Optional.of(current), ctx)
          : current;
    }
    session.record(node, current);
    return current;
  }

  private static Optional<SyntaxNode> callOf(final SyntaxNode operation) {
    return "postfixUnaryOperation".equals(operation.kind()) ? Syntax.child(operation, "callSuffix") : Optional.empty();
  }

  private List<Overloads.Argument> arguments(final SyntaxNode suffix, final Ctx ctx, final boolean[] complete) {
    final List<Overloads.Argument> arguments = new ArrayList<>();
    Syntax.child(suffix, "valueArguments").stream()
        .flatMap(list -> Syntax.children(list, "valueArgument").stream())
        .forEach(argument -> arguments.add(Overloads.Argument.of(Syntax.child(argument, EXPRESSION).map(value -> expr(value, ctx)).orElse(Typed.UNKNOWN).type())));
    if (Syntax.child(suffix, "annotatedLambda").isPresent()) {
      complete[0] = false;
      walk(Syntax.child(suffix, "annotatedLambda").get(), ctx);
    }
    return arguments;
  }

  private Typed unqualified(final String name, final SyntaxNode suffix, final Ctx ctx) {
    final boolean[] complete = {true};
    final List<Overloads.Argument> arguments = arguments(suffix, ctx, complete);
    if (!complete[0]) {
      return Typed.UNKNOWN;
    }
    for (final String owner : ctx.scope().enclosing()) {
      final List<Hierarchy.Found<JvmMethod>> candidates = session.hierarchy.methods(session.self(owner), name);
      if (!candidates.isEmpty()) {
        return finish(candidates, arguments);
      }
    }
    return ctx.scope().resolve(List.of(name))
        .map(binary -> JType.ClassType.of(binary))
        .map(site -> finishConstruction(site, arguments))
        .orElse(Typed.UNKNOWN);
  }

  private Typed finishConstruction(final JType.ClassType site, final List<Overloads.Argument> arguments) {
    final Typed call = finish(session.hierarchy.methods(site, JvmMethod.CONSTRUCTOR), arguments);
    final Typed typed = Typed.of(site);
    return call.call().map(typed::withCall).orElse(typed);
  }

  private Typed member(final Typed receiver, final String name, final SyntaxNode suffix, final Ctx ctx) {
    final boolean[] complete = {true};
    final List<Overloads.Argument> arguments = arguments(suffix, ctx, complete);
    final Optional<JType.ClassType> site =
        receiver.named().or(() -> receiver.type().flatMap(type -> calls.siteOf(type, ctx)));
    if (!complete[0] || site.isEmpty()) {
      return Typed.UNKNOWN;
    }
    return finish(session.hierarchy.methods(site.get(), name), arguments);
  }

  private Typed finish(final List<Hierarchy.Found<JvmMethod>> candidates, final List<Overloads.Argument> arguments) {
    final Optional<Hierarchy.Found<JvmMethod>> chosen = session.overloads.select(candidates, arguments);
    if (chosen.isEmpty()) {
      return Typed.UNKNOWN;
    }
    final Map<String, JType> bound = session.overloads.infer(chosen.get(), arguments);
    final JType returned = Types.substitute(Types.substitute(chosen.get().member().returnType(), chosen.get().bindings()), bound);
    final Typed typed = Types.mentionsVariable(returned) && !chosen.get().member().typeParameters().isEmpty() ? Typed.UNKNOWN : Typed.of(returned);
    return typed.withCall(chosen.get());
  }

  /** A property read: a field, a getter, or the method of that name that Kotlin maps to a property. */
  private Typed property(final Typed receiver, final String name, final Ctx ctx) {
    final Optional<JType.ClassType> site =
        receiver.named().or(() -> receiver.type().flatMap(type -> calls.siteOf(type, ctx)));
    if (site.isEmpty()) {
      return Typed.UNKNOWN;
    }
    final Optional<Typed> field = session.hierarchy.field(site.get(), name)
        .map(found -> Typed.of(Types.substitute(found.member().type(), found.bindings())).withConstant(found.member().constant()));
    if (field.isPresent()) {
      return field.get();
    }
    if (receiver.named().isPresent()) {
      return session.hierarchy.memberClass(site.get().name(), name)
          .map(binary -> Typed.naming(JType.ClassType.of(binary))).orElse(Typed.UNKNOWN);
    }
    final String suffix = Character.toUpperCase(name.charAt(0)) + name.substring(1);
    for (final String accessor : List.of("get" + suffix, "is" + suffix, name)) {
      final Typed read = finish(session.hierarchy.methods(site.get(), accessor), List.of());
      if (read.known()) {
        return read;
      }
    }
    return Typed.UNKNOWN;
  }
}
