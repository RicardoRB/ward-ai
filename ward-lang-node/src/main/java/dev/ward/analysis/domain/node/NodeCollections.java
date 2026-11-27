package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the collections of a file that only the file can write to: a top-level {@code let} or
 * {@code const}, not exported, that starts as {@code null} or as an empty {@code new Map()} or
 * {@code new Set()}, and whose every mention in the file is one of these: a call of {@code has},
 * {@code get}, {@code keys}, {@code values}, {@code entries}, {@code delete}, {@code clear} or
 * {@code set} on it, {@code .size}, a comparison with {@code null}, an assignment of an empty
 * collection or {@code null}, or {@code return name}. The analysis then reads the keys that
 * {@code set} writes; the one way out of the file is the returned value, which the callers of the
 * returning function are checked for.
 *
 * <p>shortcut: the scan is by token and name, so a shadowing local of the same name is read as the
 * collection and only makes the proof stricter; upgrade when a project hides a collection behind a
 * wrapper class.
 */
final class NodeCollections {
  private static final Pattern DECLARATION =
      Pattern.compile(
          "^(?:const|let|var)([A-Za-z_$][\\w$]*)(?::[^=]+)?=(?:null|new(?:Map|Set)(?:<.*>)?\\(\\));?$"
      );
  private static final Set<String> METHODS =
      Set.of("has", "get", "keys", "values", "entries", "delete", "clear", "set");
  private static final Set<String> COMPARISONS = Set.of("==", "===", "!=", "!==");
  private static final Set<String> DECLARATORS = Set.of("const", "let", "var");
  private static final Set<String> FRESH = Set.of("Map", "Set");
  private static final Set<String> ENDS = Set.of(";", "}", "");
  private static final String DOT = ".";
  private static final String NULL = "null";
  private static final String NEW = "new";
  private static final String OPEN = "(";
  private static final String TYPE_OPEN = "<";
  private static final int AFTER_NAME = 2;

  private NodeCollections() {}

  /** The names of the collections that only the file writes to. */
  static Set<String> of(final SyntaxNode program) {
    final Set<String> candidates = new HashSet<>();
    for (final SyntaxNode statement : NodeFlowSyntax.statements(program)) {
      final Matcher declaration = DECLARATION.matcher(NodeFlowSyntax.text(statement));
      if (declaration.matches()) {
        candidates.add(declaration.group(1));
      }
    }
    if (candidates.isEmpty()) {
      return Set.of();
    }
    final NodeTokens tokens = new NodeTokens(NodeLeaves.of(program));
    final Set<String> closed = new HashSet<>(candidates);
    for (int index = 0; index < tokens.size(); index++) {
      final String name = tokens.text(index);
      if (candidates.contains(name)
          && !DOT.equals(tokens.text(index - 1))
          && !allowed(tokens, index)) {
        closed.remove(name);
      }
    }
    return closed;
  }

  private static boolean allowed(final NodeTokens tokens, final int index) {
    final String before = tokens.text(index - 1);
    final String after = tokens.text(index + 1);
    if (DECLARATORS.contains(before)) {
      return !"export".equals(tokens.text(index - AFTER_NAME));
    }
    if ("return".equals(before) && ENDS.contains(after)) {
      return true;
    }
    if (DOT.equals(after)) {
      return METHODS.contains(tokens.text(index + AFTER_NAME))
          && OPEN.equals(tokens.text(index + AFTER_NAME + 1))
          || "size".equals(tokens.text(index + AFTER_NAME));
    }
    if (COMPARISONS.contains(after)) {
      return NULL.equals(tokens.text(index + AFTER_NAME));
    }
    return "=".equals(after) && emptyCollection(tokens, index + AFTER_NAME);
  }

  private static long count(final String token, final char mark) {
    return token.chars().filter(character -> character == mark).count();
  }

  /** Whether the tokens from {@code from} are {@code null} or {@code new Map()}, with type arguments. */
  private static boolean emptyCollection(final NodeTokens tokens, final int from) {
    if (NULL.equals(tokens.text(from))) {
      return true;
    }
    if (!NEW.equals(tokens.text(from)) || !FRESH.contains(tokens.text(from + 1))) {
      return false;
    }
    int next = from + AFTER_NAME;
    if (TYPE_OPEN.equals(tokens.text(next))) {
      long depth = 0;
      do {
        depth += count(tokens.text(next), '<') - count(tokens.text(next), '>');
        next++;
      } while (depth > 0 && next < tokens.size());
    }
    return OPEN.equals(tokens.text(next)) && ")".equals(tokens.text(next + 1));
  }
}
