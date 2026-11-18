package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The types of the parameters and locals of one Swift body that its source states: a type
 * annotation, or an initializer that creates a type, as in {@code let task = Process()}. The type
 * is the simple name of the declared type, with its optional marks and generic arguments left out.
 */
final class SwiftLocalTypes {
  private static final String TYPE_IDENTIFIER = "type_identifier";
  private static final String PARAMETER = "parameter";
  private static final String PATTERN_INITIALIZER = "pattern_initializer";
  private static final Pattern TYPE_NAME = Pattern.compile("[A-Z]\\w*");

  private SwiftLocalTypes() {}

  /**
   * Reads the types of a body.
   *
   * @param parameters the parameter clause of the body, or null
   * @param body the code block of the body
   * @return the type of each name that the source types
   */
  static Map<String, String> of(final SyntaxNode parameters, final SyntaxNode body) {
    final Map<String, String> types = new HashMap<>();
    if (parameters != null) {
      parameters(parameters, types);
    }
    declarations(body, types);
    return types;
  }

  private static void parameters(final SyntaxNode node, final Map<String, String> types) {
    if (PARAMETER.equals(node.kind())) {
      final SyntaxNode name = SwiftFlowSyntax.child(node, "local_parameter_name");
      final SyntaxNode annotation = SwiftFlowSyntax.child(node, "type_annotation");
      if (name != null && annotation != null) {
        declared(annotation).ifPresent(type -> types.put(SwiftFlowSyntax.text(name), type));
      }
      return;
    }
    node.children().forEach(child -> parameters(child, types));
  }

  private static void declarations(final SyntaxNode node, final Map<String, String> types) {
    if (SwiftFlowSyntax.OTHER_BODIES.contains(node.kind())) {
      return;
    }
    if (PATTERN_INITIALIZER.equals(node.kind())) {
      declaration(node, types);
    }
    node.children().forEach(child -> declarations(child, types));
  }

  private static void declaration(final SyntaxNode node, final Map<String, String> types) {
    final SyntaxNode pattern = SwiftFlowSyntax.child(node, "pattern");
    final SyntaxNode identifier =
        pattern == null ? null : SwiftFlowSyntax.child(pattern, "identifier_pattern");
    if (identifier == null) {
      return;
    }
    final String name = SwiftFlowSyntax.text(identifier);
    final SyntaxNode annotation = SwiftFlowSyntax.child(pattern, "type_annotation");
    final SyntaxNode initializer = SwiftFlowSyntax.child(node, "initializer");
    if (annotation != null) {
      declared(annotation).ifPresent(type -> types.put(name, type));
    } else if (initializer != null) {
      created(initializer).ifPresent(type -> types.put(name, type));
    }
  }

  private static java.util.Optional<String> declared(final SyntaxNode annotation) {
    final SyntaxNode type = SwiftFlowSyntax.find(annotation, TYPE_IDENTIFIER);
    return type == null
        ? java.util.Optional.empty()
        : java.util.Optional.of(SwiftFlowSyntax.text(SwiftFlowSyntax.find(type, "type_name")))
            .filter(name -> TYPE_NAME.matcher(name).matches());
  }

  /** The type of an initializer that is one creation, {@code Type(...)}, and nothing else. */
  private static java.util.Optional<String> created(final SyntaxNode initializer) {
    final SyntaxNode expression = SwiftFlowSyntax.child(initializer, "expression");
    final SyntaxNode prefix =
        expression == null || SwiftFlowSyntax.child(expression, "binary_expressions") != null
            ? null
            : SwiftFlowSyntax.child(expression, "prefix_expression");
    final SyntaxNode call = prefix == null ? null : SwiftFlowSyntax.child(
        prefix,
        "postfix_expression"
    );
    if (call == null
        || call.children().size() != 2
        || !"function_call_argument_clause".equals(call.children().get(1).kind())) {
      return java.util.Optional.empty();
    }
    final String callee = SwiftFlowSyntax.text(call.children().get(0));
    return java.util.Optional.of(callee).filter(name -> TYPE_NAME.matcher(name).matches());
  }
}
