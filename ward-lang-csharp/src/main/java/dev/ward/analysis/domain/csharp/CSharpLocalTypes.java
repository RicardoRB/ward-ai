package dev.ward.analysis.domain.csharp;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The types of the parameters and locals of one C# body that its source states: a declared type,
 * or {@code var} with an initializer that creates a type, as in {@code var cmd = new SqlCommand()}.
 * The type is the simple name of the declared type, with its generic arguments and nullable mark
 * left out. shortcut: fields and properties are not typed; upgrade with a type sidecar.
 */
final class CSharpLocalTypes {
  private static final Pattern TYPE_NAME = Pattern.compile("(?:[A-Za-z_]\\w*\\.)*([A-Z]\\w*)");
  private static final Pattern ARGUMENTS = Pattern.compile("<.*>|\\?");
  private static final String TYPE = "type_";
  private static final String VAR = "var";

  private CSharpLocalTypes() {}

  /**
   * Reads the types of a body.
   *
   * @param parameters the parameter list of the body, or null
   * @param body the body node
   * @return the type of each name that the source types
   */
  static Map<String, String> of(final SyntaxNode parameters, final SyntaxNode body) {
    final Map<String, String> types = new HashMap<>();
    if (parameters != null) {
      CSharpSyntax.children(parameters, "parameter")
          .forEach(parameter -> parameter(parameter, types));
    }
    declarations(body, types);
    return types;
  }

  private static void parameter(final SyntaxNode node, final Map<String, String> types) {
    final String name = CSharpFlowSyntax.name(node);
    CSharpSyntax.child(node, TYPE)
        .flatMap(type -> simple(CSharpSyntax.text(type)))
        .ifPresent(type -> types.put(name, type));
  }

  private static void declarations(final SyntaxNode node, final Map<String, String> types) {
    if (CSharpFlowSyntax.OTHER_BODIES.contains(node.kind())) {
      return;
    }
    if ("local_variable_declaration".equals(node.kind())
        || "non_ref_local_variable_declaration".equals(node.kind())) {
      final String declared =
          CSharpSyntax.child(node, "local_variable_type").map(CSharpSyntax::text).orElse("");
      CSharpSyntax.children(node, "local_variable_declarator")
          .forEach(declarator -> declarator(declarator, declared, types));
    }
    node.children().forEach(child -> declarations(child, types));
  }

  private static void declarator(
      final SyntaxNode declarator,
      final String declared,
      final Map<String,
      String> types
  ) {
    final Optional<String> type =
        VAR.equals(declared) ? created(declarator) : simple(declared);
    type.ifPresent(found -> types.put(CSharpFlowSyntax.name(declarator), found));
  }

  /** The type of an initializer that is one creation, {@code new T(...)}, and nothing else. */
  private static Optional<String> created(final SyntaxNode declarator) {
    return CSharpSyntax.child(declarator, "local_variable_initializer")
        .flatMap(
            initializer -> CSharpSyntax.soleDescendant(initializer, "object_creation_expression")
        )
        .flatMap(creation -> CSharpSyntax.child(creation, TYPE))
        .flatMap(type -> simple(CSharpSyntax.text(type)));
  }

  /** The simple name of a written type, or empty when it is not a type name. */
  static Optional<String> simple(final String written) {
    final Matcher matcher = TYPE_NAME.matcher(ARGUMENTS.matcher(written).replaceAll(""));
    return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
  }
}
