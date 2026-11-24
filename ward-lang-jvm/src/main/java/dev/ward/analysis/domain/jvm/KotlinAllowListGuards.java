package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the checks of a Kotlin value against an allow-list: {@code if (x !in ALLOWED) throw ...},
 * {@code if (!ALLOWED.contains(x)) return}, and {@code require(x in ALLOWED)}, where {@code ALLOWED}
 * is a {@code val} that holds {@code setOf(...)} or {@code listOf(...)} of the file. Past such a
 * check {@code x} is one of the constants of the collection, so the lowering gives it a clean value
 * there, as {@link JavaAllowListGuards} does for Java.
 *
 * <p>shortcut: a bare collection name and a bare local; the allow-list is found by name across the
 * file; upgrade when a project needs {@code containsKey} or a positive test with an else branch.
 */
final class KotlinAllowListGuards {
  private static final Set<String> CHECKS = Set.of("require", "check");
  private static final Pattern NOT_CONTAINS =
      Pattern.compile("^! (\\w+) \\. contains \\( (\\w+) \\)$");
  private static final Pattern NOT_IN = Pattern.compile("^(\\w+) !in (\\w+)$");
  private static final Pattern NEGATED_IN = Pattern.compile("^! \\( (\\w+) in (\\w+) \\)$");
  private static final Pattern CONTAINS = Pattern.compile("^(\\w+) \\. contains \\( (\\w+) \\)$");
  private static final Pattern IN = Pattern.compile("^(\\w+) in (\\w+)$");
  private static final Pattern REQUIRE =
      Pattern.compile("^(?:require|check) \\( (.*?)(?: , .*?)? \\)(?: \\{.*\\})?$");
  private static final int VALUE_GROUP = 1;
  private static final int COLLECTION_GROUP = 2;

  private final Set<String> allowLists;

  /**
   * Creates the finder.
   *
   * @param allowLists the names of the allow-list properties of the file
   */
  KotlinAllowListGuards(final Set<String> allowLists) {
    this.allowLists = allowLists;
  }

  /** The variable that an {@code if} without an else leaves clean past it, when it is a guard. */
  Optional<String> ofIf(final SyntaxNode condition, final SyntaxNode then) {
    return exits(then) ? negated(tokens(KotlinFlowSyntax.unwrap(condition))) : Optional.empty();
  }

  /** The variable that a statement {@code require(x in ALLOWED)} leaves clean past it. */
  Optional<String> ofCheck(final SyntaxNode statement) {
    final Matcher call = REQUIRE.matcher(String.join(" ", tokens(statement)));
    if (!call.matches()) {
      return Optional.empty();
    }
    final String condition = call.group(1);
    return match(CONTAINS.matcher(condition), true).or(() -> match(IN.matcher(condition), false));
  }

  private Optional<String> negated(final List<String> tokens) {
    final String text = String.join(" ", withoutParentheses(tokens));
    return match(NOT_CONTAINS.matcher(text), true)
        .or(() -> match(NOT_IN.matcher(text), false))
        .or(() -> match(NEGATED_IN.matcher(text), false));
  }

  /** The value of a match, given whether the collection is written first. */
  private Optional<String> match(final Matcher found, final boolean collectionFirst) {
    if (!found.matches()) {
      return Optional.empty();
    }
    final String value = found.group(collectionFirst ? COLLECTION_GROUP : VALUE_GROUP);
    final String collection = found.group(collectionFirst ? VALUE_GROUP : COLLECTION_GROUP);
    return allowLists.contains(collection) ? Optional.of(value) : Optional.empty();
  }

  /** Drops one pair of parentheses that wraps the whole condition. */
  private static List<String> withoutParentheses(final List<String> tokens) {
    if (tokens.size() < 2 || !"(".equals(tokens.get(0))) {
      return tokens;
    }
    int depth = 0;
    for (int index = 0; index < tokens.size(); index++) {
      depth += "(".equals(tokens.get(index)) ? 1 : 0;
      depth -= ")".equals(tokens.get(index)) ? 1 : 0;
      if (depth == 0) {
        return index == tokens.size() - 1 ? tokens.subList(1, index) : tokens;
      }
    }
    return tokens;
  }

  /** A body exits when it is a throw or return, or a block whose last statement is one. */
  private static boolean exits(final SyntaxNode body) {
    final SyntaxNode block = KotlinFlowSyntax.child(body, "block");
    if (block == null) {
      return exitsStatement(body);
    }
    final SyntaxNode list = KotlinFlowSyntax.child(block, "statements");
    final List<SyntaxNode> all =
        list == null ? List.of() : KotlinFlowSyntax.children(list, "statement");
    return !all.isEmpty() && exitsStatement(all.get(all.size() - 1));
  }

  private static boolean exitsStatement(final SyntaxNode statement) {
    final List<String> tokens = tokens(statement);
    return !tokens.isEmpty()
        && ("throw".equals(tokens.get(0))
            || "return".equals(tokens.get(0))
            || tokens.get(0).startsWith("return@"));
  }

  /** The leaf tokens of a node, in order. */
  static List<String> tokens(final SyntaxNode node) {
    final List<String> found = new ArrayList<>();
    collect(node, found);
    return found;
  }

  private static void collect(final SyntaxNode node, final List<String> found) {
    if (node.children().isEmpty()) {
      final String text = KotlinFlowSyntax.tokenText(node).strip();
      if (!text.isEmpty()) {
        found.add(text);
      }
      return;
    }
    node.children().forEach(child -> collect(child, found));
  }
}
