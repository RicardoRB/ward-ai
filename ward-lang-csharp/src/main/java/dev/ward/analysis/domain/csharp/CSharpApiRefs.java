package dev.ward.analysis.domain.csharp;

import dev.ward.analysis.domain.api.ApiParameter;
import dev.ward.analysis.domain.api.ApiTypeParameter;
import dev.ward.analysis.domain.api.ApiTypeRef;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads types, parameters and type parameters of an API signature from the tree. A name is read as
 * written, because no semantic model resolves it: the keyword aliases are mapped to their System
 * names, and a type variable is replaced by its position so that a renamed one is the same type.
 */
final class CSharpApiRefs {
  private static final Map<String, String> ALIASES =
      Map.ofEntries(
          Map.entry("int", "System.Int32"),
          Map.entry("uint", "System.UInt32"),
          Map.entry("long", "System.Int64"),
          Map.entry("ulong", "System.UInt64"),
          Map.entry("short", "System.Int16"),
          Map.entry("ushort", "System.UInt16"),
          Map.entry("byte", "System.Byte"),
          Map.entry("sbyte", "System.SByte"),
          Map.entry("bool", "System.Boolean"),
          Map.entry("char", "System.Char"),
          Map.entry("float", "System.Single"),
          Map.entry("double", "System.Double"),
          Map.entry("decimal", "System.Decimal"),
          Map.entry("string", "System.String"),
          Map.entry("object", "System.Object"),
          Map.entry("void", "System.Void")
      );
  private static final String VOID = "void";

  private final Map<String, String> variables;

  /** Creates the reader with the type variables in scope, by name to position key. */
  CSharpApiRefs(final Map<String, String> variables) {
    this.variables = Map.copyOf(variables);
  }

  /** Returns a reader that also knows the given method type parameters, as {@code !!n}. */
  CSharpApiRefs withMethodVariables(final List<String> names) {
    final Map<String, String> all = new HashMap<>(variables);
    for (int at = 0; at < names.size(); at++) {
      all.put(names.get(at), "!!" + at);
    }
    return new CSharpApiRefs(all);
  }

  /** Returns the names of the type parameters a {@code type_parameter_list} declares. */
  static List<String> names(final Optional<SyntaxNode> list) {
    return list.map(node -> CSharpSyntax.children(node, "type_parameter_declaration"))
        .orElse(List.of())
        .stream()
        .map(declaration -> CSharpSyntax.child(declaration, "type_parameter").orElse(declaration))
        .map(CSharpSyntax::text)
        .toList();
  }

  /** Reads a {@code type_}, {@code return_type} or {@code type_name} node. */
  ApiTypeRef type(final SyntaxNode node) {
    final List<String> tokens = new ArrayList<>();
    leaves(node, tokens);
    final String text = join(tokens, false);
    final String key = join(tokens, true);
    return new ApiTypeRef(text, key, erase(key), false);
  }

  /** The type of a void method. */
  static ApiTypeRef voidType() {
    return ApiTypeRef.of(VOID);
  }

  private void leaves(final SyntaxNode node, final List<String> tokens) {
    if (node.children().isEmpty()) {
      tokens.add(node.fields().getOrDefault("text", ""));
    }
    node.children().forEach(child -> leaves(child, tokens));
  }

  private String join(final List<String> tokens, final boolean normalized) {
    final StringBuilder out = new StringBuilder();
    for (final String token : tokens) {
      final String piece = normalized ? normalize(token) : token;
      if (!out.isEmpty() && wordChar(out.charAt(out.length() - 1)) && wordChar(piece.charAt(0))) {
        out.append(' ');
      }
      out.append(piece);
    }
    return out.toString();
  }

  private String normalize(final String token) {
    final String variable = variables.get(token);
    return variable != null ? variable : ALIASES.getOrDefault(token, token);
  }

  private static boolean wordChar(final char c) {
    return Character.isLetterOrDigit(c) || c == '_' || c == '!';
  }

  /** The key without type arguments. */
  private static String erase(final String key) {
    final StringBuilder out = new StringBuilder();
    int depth = 0;
    for (final char c : key.toCharArray()) {
      if (c == '<') {
        depth++;
      } else if (c == '>') {
        depth--;
      } else if (depth == 0) {
        out.append(c);
      }
    }
    return out.toString();
  }

  /** Reads the parameters of a {@code parameter_list}, if any. */
  List<ApiParameter> parameters(final Optional<SyntaxNode> list) {
    return list.map(node -> CSharpSyntax.children(node, "parameter")).orElse(List.of()).stream()
        .map(this::parameter)
        .toList();
  }

  private ApiParameter parameter(final SyntaxNode parameter) {
    final ApiTypeRef plain = type(CSharpSyntax.child(parameter, "type_").orElse(parameter));
    final String passing = passing(parameter);
    final ApiTypeRef declared =
        passing.isEmpty()
            ? plain
            : new ApiTypeRef(
                passing + " " + plain.text(),
                passing + " " + plain.key(),
                passing + " " + plain.erasure(),
                false
            );
    return new ApiParameter(
        declared,
        CSharpSyntax.child(parameter, "default_argument").isPresent()
    );
  }

  private static String passing(final SyntaxNode parameter) {
    final List<String> words =
        CSharpSyntax.children(parameter, "parameter_modifier").stream()
            .map(CSharpSyntax::text)
            .filter(word -> "ref".equals(word) || "out".equals(word) || "in".equals(word))
            .toList();
    return words.isEmpty() ? "" : words.getFirst();
  }

  /** Returns whether the last parameter is {@code params}. */
  static boolean isVarargs(final Optional<SyntaxNode> list) {
    return list.map(node -> CSharpSyntax.children(node, "parameter")).orElse(List.of()).stream()
        .reduce((first, second) -> second)
        .map(
            last ->
                CSharpSyntax.children(last, "parameter_modifier").stream()
                    .anyMatch(modifier -> "params".equals(CSharpSyntax.text(modifier)))
        )
        .orElse(false);
  }

  /** Reads the type parameters of a declaration with the constraints of its {@code where} clauses. */
  List<ApiTypeParameter> typeParameters(
      final List<String> names,
      final List<SyntaxNode> constraintClauses
  ) {
    return names.stream()
        .map(name -> new ApiTypeParameter(name, bounds(name, constraintClauses), ""))
        .toList();
  }

  private List<ApiTypeRef> bounds(final String name, final List<SyntaxNode> clauses) {
    final List<ApiTypeRef> found = new ArrayList<>();
    for (final SyntaxNode clause : clauses) {
      final boolean mine =
          CSharpSyntax.child(clause, "type_parameter")
              .map(parameter -> name.equals(CSharpSyntax.text(parameter)))
              .orElse(false);
      if (mine) {
        CSharpSyntax.children(clause, "type_parameter_constraint")
            .forEach(constraint -> found.add(type(constraint)));
      }
    }
    return found;
  }
}
