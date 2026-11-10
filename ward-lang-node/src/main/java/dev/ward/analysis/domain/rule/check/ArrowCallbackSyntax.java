package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The tree walks of the arrow callback check: what a function uses in its own scope and its name.
 */
final class ArrowCallbackSyntax {

  /** The head of a function expression: the optional generator star and the optional name. */
  static final Pattern FUNCTION =
      Pattern.compile("^(?:async)?function(\\*)?([A-Za-z_$][\\w$]*)?\\(");

  private static final String ARGUMENTS = "arguments";
  private static final String ANONYMOUS = "anonymousFunction";
  private static final String DECLARATION = "functionDeclaration";

  private ArrowCallbackSyntax() {}

  /** What a function uses in its own scope; nested non-arrow functions own their own. */
  record Uses(boolean thisUsed, boolean arguments, boolean superOrMeta) {}

  /** Whether the name of a named function is used inside its own body. */
  static boolean referencesItself(final SyntaxNode function, final String name) {
    return leaves(function).stream().filter(name::equals).count() > 1;
  }

  static Uses ownUses(final SyntaxNode function) {
    final List<String> tokens = new ArrayList<>();
    collectOwn(function, tokens, true);
    boolean thisUsed = false;
    boolean arguments = false;
    boolean superOrMeta = false;
    for (int i = 0; i < tokens.size(); i++) {
      final String token = tokens.get(i);
      thisUsed |= "this".equals(token);
      arguments |= ARGUMENTS.equals(token);
      superOrMeta |=
          "super".equals(token)
              || ("target".equals(token)
                  && i >= 2
                  && ".".equals(tokens.get(i - 1))
                  && "new".equals(tokens.get(i - 2)));
    }
    return new Uses(thisUsed, arguments, superOrMeta);
  }

  private static void collectOwn(
      final SyntaxNode node, final List<String> tokens, final boolean root) {
    if (!root && nestedFunction(node)) {
      return;
    }
    if (node.children().isEmpty()) {
      tokens.add(node.fields().getOrDefault("text", ""));
      return;
    }
    node.children().forEach(child -> collectOwn(child, tokens, false));
  }

  /** A declaration or a function expression binds its own {@code this}; an arrow does not. */
  private static boolean nestedFunction(final SyntaxNode node) {
    if (DECLARATION.equals(node.kind())) {
      return true;
    }
    final String text = NodeSyntax.text(node);
    return ANONYMOUS.equals(node.kind())
        && (text.startsWith("function") || text.startsWith("asyncfunction"));
  }

  private static List<String> leaves(final SyntaxNode node) {
    final List<String> out = new ArrayList<>();
    collectLeaves(node, out);
    return out;
  }

  private static void collectLeaves(final SyntaxNode node, final List<String> out) {
    if (node.children().isEmpty()) {
      out.add(node.fields().getOrDefault("text", ""));
      return;
    }
    node.children().forEach(child -> collectLeaves(child, out));
  }
}
