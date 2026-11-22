package dev.ward.analysis.domain.csharp;

import dev.ward.analysis.domain.api.ApiTypeRef;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Writes a type of an API signature as text, as a comparison key and as an erasure. A name is read
 * as written, because no semantic model resolves it: the keyword aliases are mapped to their System
 * names, and a type variable is replaced by its position so that a renamed one is the same type.
 */
final class CSharpApiTypeText {
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
  private static final char OPEN = '<';
  private static final char CLOSE = '>';

  private final Map<String, String> variables;

  CSharpApiTypeText(final Map<String, String> variables) {
    this.variables = Map.copyOf(variables);
  }

  /** Reads a {@code type_}, {@code return_type} or {@code type_name} node. */
  ApiTypeRef type(final SyntaxNode node) {
    final List<String> tokens = new ArrayList<>();
    leaves(node, tokens);
    final String text = join(tokens, false);
    final String key = join(tokens, true);
    return new ApiTypeRef(text, key, erase(key), false);
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
      if (c == OPEN) {
        depth++;
      } else if (c == CLOSE) {
        depth--;
      } else if (depth == 0) {
        out.append(c);
      }
    }
    return out.toString();
  }
}
