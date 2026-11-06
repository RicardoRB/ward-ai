package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ImportOrigin;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.ScopeBuilder;
import dev.ward.analysis.domain.scope.ScopeKind;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Walks a Swift syntax tree and records its scopes: members in type scopes, parameters in function
 * and closure scopes, locals in block scopes. A {@code guard let} binds in the enclosing block, an
 * {@code if let} in the statement. Types are not references. A type with a supertype, an extension
 * and a protocol are open because they bring in names the file does not spell, and so is a closure
 * with no signature, whose {@code $0} and receiver-like names are implicit.
 */
final class SwiftScopeWalk {

  private static final Set<String> TYPES =
      Set.of(
          "struct_declaration",
          "class_declaration",
          "union_style_enum",
          "raw_value_style_enum",
          "protocol_declaration",
          "extension_declaration",
          "actor_declaration");
  private static final Set<String> NAMED_TYPES =
      Set.of("struct_name", "class_name", "enum_name", "protocol_name", "actor_name");
  private static final Set<String> SCOPED =
      Set.of("code_block", "while_statement", "switch_case", "repeat_while_statement");
  private static final Set<String> SKIPPED =
      Set.of(
          "type_",
          "type_annotation",
          "type_identifier",
          "attributes",
          "attribute",
          "declaration_modifiers",
          "generic_parameter_clause",
          "generic_argument_clause",
          "type_inheritance_clause",
          "generic_where_clause",
          "function_result",
          "label_identifier");
  private static final Set<String> COMPOUND =
      Set.of("+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<=", ">>=", "&&=", "||=", "??=");
  private static final Set<String> ACCESSORS = Set.of("get", "set", "willSet", "didSet");
  private static final String IDENTIFIER = "declaration_identifier";
  private static final String PRIMARY = "primary_expression";
  private static final String PATTERN = "pattern";
  private static final String INITIALIZER = "initializer";

  private final ScopeBuilder builder;
  private final Map<String, Consumer<SyntaxNode>> handlers = new HashMap<>();
  private BindingKind declaring = BindingKind.CONSTANT;

  SwiftScopeWalk(final ScopeBuilder builder) {
    this.builder = builder;
    handlers.put("import_declaration", this::importDeclaration);
    handlers.put("function_declaration", this::function);
    handlers.put("initializer_declaration", node -> scoped(node, ScopeKind.FUNCTION, false));
    handlers.put("deinitializer_declaration", node -> scoped(node, ScopeKind.FUNCTION, false));
    handlers.put("subscript_declaration", node -> scoped(node, ScopeKind.FUNCTION, false));
    handlers.put("parameter", this::parameter);
    handlers.put("typealias_declaration", this::typeAlias);
    handlers.put("union_style_enum_case", this::enumCase);
    handlers.put("raw_value_style_enum_case", this::enumCase);
    handlers.put("constant_declaration", node -> declaration(node, BindingKind.CONSTANT));
    handlers.put("variable_declaration", node -> declaration(node, BindingKind.VARIABLE));
    handlers.put("pattern_initializer", this::patternInitializer);
    handlers.put("optional_binding_condition", this::optionalBinding);
    handlers.put("for_in_statement", this::forIn);
    handlers.put("catch_clause", this::catchClause);
    handlers.put("closure_expression", this::closure);
    handlers.put("closure_parameter_name", this::closureParameter);
    handlers.put("closure_parameter_clause_identifier_list", this::closureIdentifiers);
    handlers.put("setter_clause", node -> accessor(node, "newValue"));
    handlers.put("willSet_clause", node -> accessor(node, "newValue"));
    handlers.put("didSet_clause", node -> accessor(node, "oldValue"));
    handlers.put("if_statement", this::ifStatement);
    handlers.put("expression", this::expression);
    handlers.put(PRIMARY, this::primary);
    handlers.put("postfix_expression", this::postfix);
    handlers.put(PATTERN, node -> pattern(node, false, false));
    TYPES.forEach(kind -> handlers.put(kind, this::typeLike));
    SCOPED.forEach(kind -> handlers.put(kind, node -> scoped(node, ScopeKind.BLOCK, false)));
    SKIPPED.forEach(kind -> handlers.put(kind, node -> {}));
  }

  /** Visits {@code node} and everything under it. */
  void visit(final SyntaxNode node) {
    handlers.getOrDefault(node.kind(), this::generic).accept(node);
  }

  private void generic(final SyntaxNode node) {
    node.children().forEach(this::visit);
  }

  private void scoped(final SyntaxNode node, final ScopeKind kind, final boolean open) {
    final int scope = builder.open(kind, node.span());
    if (open) {
      builder.markOpen(scope);
    }
    generic(node);
    builder.close();
  }

  private static String text(final SyntaxNode node) {
    final StringBuilder out = new StringBuilder();
    append(node, out);
    return out.toString();
  }

  private static void append(final SyntaxNode node, final StringBuilder out) {
    if (node.children().isEmpty()) {
      out.append(node.fields().getOrDefault("text", ""));
    }
    node.children().forEach(child -> append(child, out));
  }

  private static SyntaxNode child(final SyntaxNode node, final String kind) {
    return node.children().stream().filter(c -> kind.equals(c.kind())).findFirst().orElse(null);
  }

  private void declare(
      final SyntaxNode name,
      final BindingKind kind,
      final boolean written,
      final boolean destructured) {
    final Set<BindingFlag> flags = EnumSet.noneOf(BindingFlag.class);
    if (destructured) {
      flags.add(BindingFlag.DESTRUCTURED);
    }
    builder.declare(text(name), kind, name.span(), flags);
    if (written) {
      builder.reference(text(name), name.span(), ReferenceAccess.WRITE);
    }
  }

  /** An {@code if let} binds in the condition and the first block, not in the {@code else}. */
  private void ifStatement(final SyntaxNode node) {
    builder.open(ScopeKind.BLOCK, node.span());
    node.children().stream()
        .filter(child -> !"else_clause".equals(child.kind()))
        .forEach(this::visit);
    builder.close();
    node.children().stream()
        .filter(child -> "else_clause".equals(child.kind()))
        .forEach(this::visit);
  }

  private void importDeclaration(final SyntaxNode node) {
    final SyntaxNode path = child(node, "import_path");
    final List<SyntaxNode> segments =
        path.children().stream().filter(c -> "import_path_identifier".equals(c.kind())).toList();
    final boolean member = child(node, "import_kind") != null;
    final SyntaxNode bound = member ? segments.get(segments.size() - 1) : segments.get(0);
    builder.declareImport(
        text(bound),
        bound.span(),
        new ImportOrigin(text(path), text(segments.get(segments.size() - 1)), node.span()),
        EnumSet.noneOf(BindingFlag.class));
  }

  private void typeLike(final SyntaxNode node) {
    final SyntaxNode name =
        node.children().stream()
            .filter(c -> NAMED_TYPES.contains(c.kind()))
            .findFirst()
            .orElse(null);
    if (name != null) {
      declare(name, BindingKind.CLASS, false, false);
    }
    final boolean inherits =
        child(node, "type_inheritance_clause") != null
            || "extension_declaration".equals(node.kind())
            || "protocol_declaration".equals(node.kind());
    scoped(node, ScopeKind.CLASS, inherits);
  }

  private void function(final SyntaxNode node) {
    final SyntaxNode name = child(node, "function_name");
    if (name != null) {
      builder.declare(text(name), BindingKind.FUNCTION, name.span(), Set.of());
    }
    scoped(node, ScopeKind.FUNCTION, false);
  }

  private void parameter(final SyntaxNode node) {
    final SyntaxNode local = child(node, "local_parameter_name");
    final SyntaxNode name = local == null ? child(node, "external_parameter_name") : local;
    if (name != null && !"_".equals(text(name))) {
      declare(name, BindingKind.PARAMETER, false, false);
    }
    node.children().stream()
        .filter(c -> "default_argument_clause".equals(c.kind()))
        .forEach(this::visit);
  }

  private void typeAlias(final SyntaxNode node) {
    final SyntaxNode name = child(node, "typealias_name");
    if (name != null) {
      builder.declare(text(name), BindingKind.TYPE, name.span(), EnumSet.of(BindingFlag.TYPE_ONLY));
    }
  }

  private void enumCase(final SyntaxNode node) {
    declare(child(node, "enum_case_name"), BindingKind.CONSTANT, false, false);
  }

  private void declaration(final SyntaxNode node, final BindingKind kind) {
    final BindingKind outer = declaring;
    declaring = kind;
    final SyntaxNode name = child(node, "variable_name");
    if (name == null) {
      generic(node);
    } else {
      declare(name, kind, false, false);
      node.children().stream().filter(c -> c != name).forEach(this::bodyOrAccessors);
    }
    declaring = outer;
  }

  /**
   * The grammar reads the accessors of a computed property, {@code { get { ... } set { ... } }}, as
   * a code block of calls with trailing closures, so a block made only of those is a list of
   * accessors.
   */
  private void bodyOrAccessors(final SyntaxNode node) {
    final List<SyntaxNode> accessors =
        "code_block".equals(node.kind()) ? accessors(node) : List.of();
    if (accessors.isEmpty()) {
      visit(node);
      return;
    }
    accessors.forEach(this::accessorCall);
  }

  private static List<SyntaxNode> accessors(final SyntaxNode block) {
    final List<SyntaxNode> statements = new java.util.ArrayList<>();
    collectStatements(block, statements);
    final boolean allAccessors =
        !statements.isEmpty() && statements.stream().allMatch(SwiftScopeWalk::isAccessorCall);
    return allAccessors ? statements : List.of();
  }

  private static void collectStatements(final SyntaxNode node, final List<SyntaxNode> out) {
    for (final SyntaxNode child : node.children()) {
      if ("statement".equals(child.kind())) {
        out.add(child);
      } else if ("statements".equals(child.kind()) || "statements_impl".equals(child.kind())) {
        collectStatements(child, out);
      }
    }
  }

  private static boolean isAccessorCall(final SyntaxNode statement) {
    return ACCESSORS.contains(text(statement).replaceAll("[({\\s].*", ""))
        && findKind(statement, "trailing_closure") != null;
  }

  private static SyntaxNode findKind(final SyntaxNode node, final String kind) {
    if (kind.equals(node.kind())) {
      return node;
    }
    for (final SyntaxNode child : node.children()) {
      final SyntaxNode found = findKind(child, kind);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  private void accessorCall(final SyntaxNode statement) {
    final String name = text(statement).replaceAll("[({\\s].*", "");
    builder.open(ScopeKind.FUNCTION, statement.span());
    final String implicit = "didSet".equals(name) ? "oldValue" : "newValue";
    final SyntaxNode argument = findKind(statement, "function_call_argument");
    final SyntaxNode explicit =
        argument == null
            ? null
            : plainName(argument.children().get(argument.children().size() - 1));
    if (!"get".equals(name)) {
      if (explicit == null) {
        builder.declare(
            implicit, BindingKind.PARAMETER, statement.span(), EnumSet.noneOf(BindingFlag.class));
      } else {
        declare(explicit, BindingKind.PARAMETER, false, false);
      }
    }
    final SyntaxNode closure = findKind(statement, "closure_expression");
    if (closure != null) {
      closure.children().stream().filter(c -> "statements".equals(c.kind())).forEach(this::visit);
    }
    builder.close();
  }

  private void patternInitializer(final SyntaxNode node) {
    node.children().stream().filter(c -> INITIALIZER.equals(c.kind())).forEach(this::visit);
    final SyntaxNode pattern = child(node, PATTERN);
    final boolean initialized = child(node, INITIALIZER) != null;
    if (pattern != null) {
      pattern(pattern, true, initialized);
    }
  }

  private void optionalBinding(final SyntaxNode node) {
    node.children().stream().filter(c -> INITIALIZER.equals(c.kind())).forEach(this::visit);
    final BindingKind outer = declaring;
    declaring =
        "var".equals(text(node.children().get(0))) ? BindingKind.VARIABLE : BindingKind.CONSTANT;
    pattern(child(node, PATTERN), true, true);
    declaring = outer;
  }

  private void forIn(final SyntaxNode node) {
    builder.open(ScopeKind.BLOCK, node.span());
    node.children().stream().filter(c -> "expression".equals(c.kind())).forEach(this::visit);
    final BindingKind outer = declaring;
    declaring = BindingKind.VARIABLE;
    pattern(child(node, PATTERN), true, true);
    declaring = outer;
    node.children().stream()
        .filter(c -> !PATTERN.equals(c.kind()) && !"expression".equals(c.kind()))
        .forEach(this::visit);
    builder.close();
  }

  private void catchClause(final SyntaxNode node) {
    builder.open(ScopeKind.BLOCK, node.span());
    if (child(node, PATTERN) == null) {
      builder.declare(
          "error", BindingKind.PARAMETER, node.span(), EnumSet.noneOf(BindingFlag.class));
    }
    generic(node);
    builder.close();
  }

  /** Walks a pattern: names are declared when {@code binding}, and read otherwise. */
  private void pattern(final SyntaxNode node, final boolean binding, final boolean written) {
    switch (node.kind()) {
      case "identifier_pattern" -> identifierPattern(node, binding, written, false);
      case "value_binding_pattern" -> valueBinding(node);
      case "tuple_pattern" -> node.children().forEach(child -> tuple(child, binding, written));
      default -> node.children().forEach(child -> patternPart(child, binding, written));
    }
  }

  private void tuple(final SyntaxNode node, final boolean binding, final boolean written) {
    if ("identifier_pattern".equals(node.kind())) {
      identifierPattern(node, binding, written, true);
    } else if (PATTERN.equals(node.kind()) || node.kind().startsWith("tuple_pattern")) {
      node.children().forEach(child -> tuple(child, binding, written));
    } else {
      pattern(node, binding, written);
    }
  }

  private void patternPart(final SyntaxNode node, final boolean binding, final boolean written) {
    if (PATTERN.equals(node.kind()) || node.kind().endsWith("_pattern")) {
      pattern(node, binding, written);
    } else {
      visit(node);
    }
  }

  private void valueBinding(final SyntaxNode node) {
    final BindingKind outer = declaring;
    declaring =
        "var".equals(text(node.children().get(0))) ? BindingKind.VARIABLE : BindingKind.CONSTANT;
    node.children().stream()
        .filter(child -> PATTERN.equals(child.kind()))
        .forEach(child -> pattern(child, true, true));
    declaring = outer;
  }

  private void identifierPattern(
      final SyntaxNode node, final boolean binding, final boolean written, final boolean tuple) {
    final SyntaxNode name = child(node, IDENTIFIER);
    if (name == null) {
      return;
    }
    if (binding) {
      declare(name, declaring, written, tuple);
    } else {
      builder.reference(text(name), name.span(), ReferenceAccess.READ);
    }
  }

  private void closure(final SyntaxNode node) {
    final boolean signature = child(node, "closure_signature") != null;
    final int scope = builder.open(ScopeKind.LAMBDA, node.span());
    if (!signature) {
      builder.markOpen(scope);
    }
    generic(node);
    builder.close();
  }

  private void closureParameter(final SyntaxNode node) {
    declare(node, BindingKind.PARAMETER, false, false);
  }

  private void closureIdentifiers(final SyntaxNode node) {
    node.children().stream()
        .filter(child -> IDENTIFIER.equals(child.kind()))
        .forEach(name -> declare(name, BindingKind.PARAMETER, false, false));
  }

  private void accessor(final SyntaxNode node, final String implicit) {
    builder.open(ScopeKind.FUNCTION, node.span());
    final SyntaxNode named = child(node, "setter_name");
    if (named == null) {
      builder.declare(
          implicit, BindingKind.PARAMETER, node.span(), EnumSet.noneOf(BindingFlag.class));
    } else {
      declare(child(named, IDENTIFIER), BindingKind.PARAMETER, false, false);
    }
    node.children().stream().filter(c -> "code_block".equals(c.kind())).forEach(this::visit);
    builder.close();
  }

  private void expression(final SyntaxNode node) {
    final SyntaxNode target = child(node, "prefix_expression");
    final SyntaxNode operators = child(node, "binary_expressions");
    if (target != null && operators != null && assignsTo(operators)) {
      final boolean compound = !"=".equals(assignment(operators));
      target(target, compound);
      node.children().stream().filter(c -> c != target).forEach(this::visit);
    } else {
      generic(node);
    }
  }

  private static String assignment(final SyntaxNode operators) {
    final SyntaxNode first = child(operators, "binary_expression");
    if (first == null || first.children().isEmpty()) {
      return "";
    }
    final SyntaxNode operator = first.children().get(0);
    return "assignment_operator".equals(operator.kind()) ? "=" : text(operator);
  }

  private static boolean assignsTo(final SyntaxNode operators) {
    final String operator = assignment(operators);
    return "=".equals(operator) || COMPOUND.contains(operator);
  }

  /** The name an expression is made of alone, or null. */
  private static SyntaxNode plainName(final SyntaxNode node) {
    SyntaxNode current = node;
    while (("prefix_expression".equals(current.kind())
            || "postfix_expression".equals(current.kind())
            || PRIMARY.equals(current.kind()))
        && current.children().size() == 1) {
      current = current.children().get(0);
    }
    return IDENTIFIER.equals(current.kind()) ? current : null;
  }

  private void target(final SyntaxNode expression, final boolean compound) {
    final SyntaxNode name = plainName(expression);
    if (name == null) {
      visit(expression);
      return;
    }
    if (compound) {
      builder.reference(text(name), name.span(), ReferenceAccess.READ);
    }
    builder.reference(text(name), name.span(), ReferenceAccess.WRITE);
  }

  private void primary(final SyntaxNode node) {
    final SyntaxNode name = node.children().size() == 1 ? child(node, IDENTIFIER) : null;
    if (name == null) {
      generic(node);
    } else {
      builder.reference(text(name), name.span(), ReferenceAccess.READ);
    }
  }

  /** In {@code a.b} the name after the dot is a member, not a reference. */
  private void postfix(final SyntaxNode node) {
    node.children().stream().filter(c -> !IDENTIFIER.equals(c.kind())).forEach(this::visit);
  }
}
