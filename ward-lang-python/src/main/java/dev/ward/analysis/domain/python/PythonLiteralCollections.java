package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

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
    walk(tree, bound, fixed);
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
    return !tokens.isEmpty() && (isLiteral(tokens) || isDirectoryNames(tokens));
  }

  private static void walk(
      final SyntaxNode node,
      final Map<String,
      Integer> bound,
      final Set<String> fixed
  ) {
    if (EXPR_STMT.equals(node.kind())) {
      assignment(node, bound, fixed);
    } else if ("for_stmt".equals(node.kind()) || "with_item".equals(node.kind())) {
      words(node).stream()
          .filter(word -> IDENTIFIER.matcher(word).matches())
          .forEach(word -> bound.merge(word, 2, Integer::sum));
    }
    for (final SyntaxNode child : node.children()) {
      walk(child, bound, fixed);
    }
  }

  private static void assignment(
      final SyntaxNode node,
      final Map<String,
      Integer> bound,
      final Set<String> fixed
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
      if (single && index == parts.size() - 2 && isFixed(words(parts.get(parts.size() - 1)))) {
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

  private static boolean isLiteral(final List<String> tokens) {
    return tokens.stream()
        .allMatch(
            token ->
                PUNCTUATION.contains(token)
                    || WORDS.contains(token)
                    || CONSTANT.matcher(token).matches()
        )
        && ("[".equals(tokens.get(0)) || "(".equals(tokens.get(0)) || "{".equals(tokens.get(0))
            || WORDS.contains(tokens.get(0)));
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
