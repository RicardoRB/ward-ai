package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.ScopeBuilder;
import dev.ward.analysis.domain.scope.ScopeKind;
import java.util.EnumSet;
import java.util.HashMap;
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
  private static final String PRIMARY = "primary_expression";
  private static final String PATTERN = "pattern";

  final ScopeBuilder builder;
  private final Map<String, Consumer<SyntaxNode>> handlers = new HashMap<>();
  BindingKind declaring = BindingKind.CONSTANT;
  final SwiftScopeTypes types;
  final SwiftScopeClosures closures;
  final SwiftScopePatterns patterns;
  final SwiftScopeExpressions expressions;

  SwiftScopeWalk(final ScopeBuilder builder) {
    this.types = new SwiftScopeTypes(this);
    this.closures = new SwiftScopeClosures(this);
    this.patterns = new SwiftScopePatterns(this);
    this.expressions = new SwiftScopeExpressions(this);
    this.builder = builder;
    handlers.put("import_declaration", types::importDeclaration);
    handlers.put("function_declaration", types::function);
    handlers.put("initializer_declaration", node -> scoped(node, ScopeKind.FUNCTION, false));
    handlers.put("deinitializer_declaration", node -> scoped(node, ScopeKind.FUNCTION, false));
    handlers.put("subscript_declaration", node -> scoped(node, ScopeKind.FUNCTION, false));
    handlers.put("parameter", types::parameter);
    handlers.put("typealias_declaration", types::typeAlias);
    handlers.put("union_style_enum_case", types::enumCase);
    handlers.put("raw_value_style_enum_case", types::enumCase);
    handlers.put("constant_declaration", node -> types.declaration(node, BindingKind.CONSTANT));
    handlers.put("variable_declaration", node -> types.declaration(node, BindingKind.VARIABLE));
    handlers.put("pattern_initializer", patterns::patternInitializer);
    handlers.put("optional_binding_condition", patterns::optionalBinding);
    handlers.put("for_in_statement", patterns::forIn);
    handlers.put("catch_clause", patterns::catchClause);
    handlers.put("closure_expression", closures::closure);
    handlers.put("closure_parameter_name", closures::closureParameter);
    handlers.put("closure_parameter_clause_identifier_list", closures::closureIdentifiers);
    handlers.put("setter_clause", node -> closures.accessor(node, "newValue"));
    handlers.put("willSet_clause", node -> closures.accessor(node, "newValue"));
    handlers.put("didSet_clause", node -> closures.accessor(node, "oldValue"));
    handlers.put("if_statement", expressions::ifStatement);
    handlers.put("expression", expressions::expression);
    handlers.put(PRIMARY, expressions::primary);
    handlers.put("postfix_expression", expressions::postfix);
    handlers.put(PATTERN, node -> patterns.pattern(node, false, false));
    TYPES.forEach(kind -> handlers.put(kind, types::typeLike));
    SCOPED.forEach(kind -> handlers.put(kind, node -> scoped(node, ScopeKind.BLOCK, false)));
    SKIPPED.forEach(kind -> handlers.put(kind, node -> {}));
  }

  /** Visits {@code node} and everything under it. */
  void visit(final SyntaxNode node) {
    handlers.getOrDefault(node.kind(), this::generic).accept(node);
  }

  void generic(final SyntaxNode node) {
    node.children().forEach(this::visit);
  }

  void scoped(final SyntaxNode node, final ScopeKind kind, final boolean open) {
    final int scope = builder.open(kind, node.span());
    if (open) {
      builder.markOpen(scope);
    }
    generic(node);
    builder.close();
  }

  static String text(final SyntaxNode node) {
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

  static SyntaxNode child(final SyntaxNode node, final String kind) {
    return node.children().stream().filter(c -> kind.equals(c.kind())).findFirst().orElse(null);
  }

  void declare(
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

  static SyntaxNode findKind(final SyntaxNode node, final String kind) {
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
}
