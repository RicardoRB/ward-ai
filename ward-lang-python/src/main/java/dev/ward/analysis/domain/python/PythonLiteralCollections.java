package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * Finds the names that a file binds exactly once, to a collection that the program itself fixes:
 * a literal of constants, or a list of the names in a directory. A value that is a member of such
 * a collection is not under the control of a request.
 *
 * <p>shortcut: the binding count reads assignments, loops, {@code with}/{@code as}, imports and
 * definitions of the whole file, so a name that a nested scope reuses is dropped, never kept;
 * upgrade when the scope analysis can answer which binding a name refers to.
 */
final class PythonLiteralCollections {
  private static final String EXPR_STMT = "expr_stmt";
  private static final String EQUALS = "=";
  private static final String LAMBDA = "lambda";
  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_]\\w*");
  private static final Pattern CONSTANT =
      Pattern.compile("(?:[rRbBuU]{0,2}(?:'[^{]*'|\"[^{]*\")|\\d[\\w.]*)");
  private static final Set<String> WORDS =
      Set.of("frozenset", "set", "tuple", "list", "True", "False", "None");
  private static final Set<String> PUNCTUATION =
      Set.of("[", "]", "(", ")", "{", "}", ",", ":", "-");
  private static final Set<String> BINDERS =
      Set.of("for", "as", "def", "class", "import", "global", "nonlocal");
  private static final Set<String> REQUEST_WORDS = Set.of("request", "args", "form", "json");

  private PythonLiteralCollections() {}

  /** The names that {@code tree} binds exactly once, to a collection the program fixes. */
  static Set<String> of(final SyntaxNode tree) {
    final Map<String, Integer> bound = new HashMap<>();
    final Set<String> fixed = new HashSet<>();
    final Set<String> definitions = new HashSet<>();
    definitions(tree, definitions);
    walk(tree, bound, fixed, definitions);
    final List<String> tokens = words(tree);
    for (int index = 0; index < tokens.size(); index++) {
      final String token = tokens.get(index);
      if (IDENTIFIER.matcher(token).matches() && rebinds(tokens, index)) {
        bound.merge(token, 2, Integer::sum);
      }
    }
    fixed.removeIf(name -> bound.getOrDefault(name, 0) != 1);
    return fixed;
  }

  /** Tells whether {@code tokens} are a collection that the program fixes. */
  static boolean isFixed(final List<String> tokens) {
    return isFixed(tokens, Set.of());
  }

  /**
   * Tells whether {@code tokens} are a collection that the program fixes; a name of {@code
   * definitions}, a function or class of the file, is a constant too.
   */
  private static boolean isFixed(final List<String> tokens, final Set<String> definitions) {
    return !tokens.isEmpty() && (isLiteral(tokens, definitions) || isDirectoryNames(tokens));
  }

  /** The names of the functions and classes that the file defines outside any other body. */
  private static void definitions(final SyntaxNode node, final Set<String> out) {
    for (final SyntaxNode child : node.children()) {
      if ("funcdef".equals(child.kind()) || "classdef".equals(child.kind())) {
        PythonSyntax.firstOfKind(child, "name")
            .ifPresent(name -> out.add(PythonSyntax.text(PythonSyntax.tokens(name).get(0))));
      } else {
        definitions(child, out);
      }
    }
  }

  private static void walk(
      final SyntaxNode node,
      final Map<String,
      Integer> bound,
      final Set<String> fixed,
      final Set<String> definitions
  ) {
    if (EXPR_STMT.equals(node.kind())) {
      assignment(node, bound, fixed, definitions);
    } else if ("for_stmt".equals(node.kind()) || "with_item".equals(node.kind())) {
      words(node).stream()
          .filter(word -> IDENTIFIER.matcher(word).matches())
          .forEach(word -> bound.merge(word, 2, Integer::sum));
    }
    for (final SyntaxNode child : node.children()) {
      walk(child, bound, fixed, definitions);
    }
  }

  private static void assignment(
      final SyntaxNode node,
      final Map<String,
      Integer> bound,
      final Set<String> fixed,
      final Set<String> definitions
  ) {
    final List<SyntaxNode> parts = node.children();
    for (int index = 1; index < parts.size() - 1; index++) {
      if (!EQUALS.equals(PythonSyntax.text(parts.get(index)))) {
        continue;
      }
      final List<String> target = words(parts.get(index - 1));
      final boolean single = target.size() == 1 && IDENTIFIER.matcher(target.get(0)).matches();
      for (final String name : target) {
        if (IDENTIFIER.matcher(name).matches()) {
          bound.merge(name, single ? 1 : 2, Integer::sum);
        }
      }
      if (single && index == parts.size() - 2 && isFixed(
          words(parts.get(parts.size() - 1)),
          definitions
      )) {
        fixed.add(target.get(0));
      }
    }
  }

  private static boolean rebinds(final List<String> tokens, final int index) {
    final String next = index + 1 < tokens.size() ? tokens.get(index + 1) : "";
    final String previous = index > 0 ? tokens.get(index - 1) : "";
    return BINDERS.contains(previous) || ":=".equals(next) || next.length() > 1
        && next.endsWith(EQUALS)
        && !"==".equals(next)
        && !"!=".equals(next)
        && !"<=".equals(next)
        && !">=".equals(next);
  }

  private static boolean isLiteral(final List<String> tokens, final Set<String> definitions) {
    return IntStream.range(0, tokens.size())
        .allMatch(index -> isConstant(tokens, index, definitions))
        && ("[".equals(tokens.get(0)) || "(".equals(tokens.get(0)) || "{".equals(tokens.get(0))
            || WORDS.contains(tokens.get(0)));
  }

  /**
   * A constant token: punctuation, a literal, a function or class of the file, or a lambda made of
   * those, whose {@code =} and the keyword before it are the way it passes a constant.
   */
  private static boolean isConstant(
      final List<String> tokens,
      final int index,
      final Set<String> definitions
  ) {
    final String token = tokens.get(index);
    return PUNCTUATION.contains(token)
        || WORDS.contains(token)
        || definitions.contains(token)
        || CONSTANT.matcher(token).matches()
        || LAMBDA.equals(token)
        || isLambdaKeyword(tokens, index);
  }

  /** The {@code =} of a keyword argument of a lambda body, or the keyword before it. */
  private static boolean isLambdaKeyword(final List<String> tokens, final int index) {
    final String token = tokens.get(index);
    final boolean keyword = index + 1 < tokens.size() && EQUALS.equals(tokens.get(index + 1));
    return (EQUALS.equals(token) || keyword && IDENTIFIER.matcher(token).matches())
        && lambdaBefore(tokens, index);
  }

  /** Whether a {@code lambda} opens the nearest enclosing element before the token. */
  private static boolean lambdaBefore(final List<String> tokens, final int index) {
    for (int back = index - 1; back >= 0; back--) {
      final String token = tokens.get(back);
      if (LAMBDA.equals(token)) {
        return true;
      }
      if (",".equals(token) || "{".equals(token)) {
        return false;
      }
    }
    return false;
  }

  private static boolean isDirectoryNames(final List<String> tokens) {
    return "[".equals(tokens.get(0))
        && tokens.contains("listdir")
        && tokens.stream().noneMatch(REQUEST_WORDS::contains);
  }

  static List<String> words(final SyntaxNode node) {
    return PythonSyntax.tokens(node).stream().map(PythonSyntax::text).toList();
  }
}
