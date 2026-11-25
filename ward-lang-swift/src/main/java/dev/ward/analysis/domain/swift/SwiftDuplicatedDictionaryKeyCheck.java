package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports each later key of a dictionary literal that repeats an earlier key, like SwiftLint's
 * {@code duplicated_key_in_dictionary_literal}. Only literal, member and identifier keys are
 * compared; a key that is a call or any other expression is never a duplicate. A literal whose
 * declared type is a named type other than {@code Dictionary} ({@code let h: HTTPHeaders = [...]})
 * is not reported: such a type builds itself from the literal and may keep repeated keys.
 * shortcut: only a binding annotation gives the literal its type; an argument or an operand does
 * not, and an alias of a dictionary type counts as another type. Upgrade when the type facts
 * answer the contextual type of a literal.
 */
public final class SwiftDuplicatedDictionaryKeyCheck implements RuleCheck {
  private static final String LITERAL = "dictionary_literal";
  private static final String ITEMS = "dictionary_literal_items";
  private static final String ITEM = "dictionary_literal_item";
  private static final String EXPRESSION = "expression";
  private static final String BINDING = "pattern_initializer";
  private static final String DICTIONARY = "Dictionary";
  private static final Pattern SIMPLE_KEY =
      Pattern.compile("\"[^\"]*\"|[-+]?[0-9][0-9._]*|\\.?[A-Za-z_][A-Za-z0-9_.]*");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public SwiftDuplicatedDictionaryKeyCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    final Set<Span> retyped = retypedLiterals(root);
    for (final SyntaxNode literal : SwiftTrees.descendants(root, LITERAL)) {
      if (!retyped.contains(literal.span())) {
        repeatedKeys(literal, matches);
      }
    }
    return List.copyOf(matches);
  }

  /** The literals that initialize a binding annotated with a named type other than Dictionary. */
  private static Set<Span> retypedLiterals(final SyntaxNode root) {
    final Set<Span> spans = new HashSet<>();
    for (final SyntaxNode binding : SwiftTrees.descendants(root, BINDING)) {
      if (annotatedAsOtherType(binding)) {
        SwiftTrees.child(binding, "initializer")
            .flatMap(initializer -> SwiftTrees.child(initializer, EXPRESSION))
            .map(SwiftDuplicatedDictionaryKeyCheck::onlyLiteral)
            .ifPresent(literal -> literal.ifPresent(node -> spans.add(node.span())));
      }
    }
    return spans;
  }

  private static Optional<SyntaxNode> onlyLiteral(final SyntaxNode expression) {
    SyntaxNode node = expression;
    while (!LITERAL.equals(node.kind()) && node.children().size() == 1) {
      node = node.children().get(0);
    }
    return LITERAL.equals(node.kind()) ? Optional.of(node) : Optional.empty();
  }

  /** A type identifier, optional or not, that does not spell Dictionary. */
  private static boolean annotatedAsOtherType(final SyntaxNode binding) {
    final Optional<SyntaxNode> annotation =
        SwiftTrees.child(binding, "pattern").flatMap(p -> SwiftTrees.child(p, "type_annotation"));
    if (annotation.isEmpty() || !SwiftTrees.descendants(
        annotation.get(),
        "type_identifier"
    ).stream()
        .findFirst()
        .isPresent()) {
      return false;
    }
    final String type = SwiftTrees.compact(annotation.get());
    return !type.startsWith(":[") && !type.startsWith(":" + DICTIONARY + "<")
        && !type.startsWith(":" + DICTIONARY + "?") && !type.equals(":" + DICTIONARY);
  }

  private void repeatedKeys(final SyntaxNode literal, final List<RuleMatch> matches) {
    final Set<String> seen = new HashSet<>();
    final List<SyntaxNode> keys = new ArrayList<>();
    collectKeys(literal, keys);
    for (final SyntaxNode key : keys) {
      final String text = SwiftTrees.compact(key);
      if (SIMPLE_KEY.matcher(text).matches() && !seen.add(text)) {
        matches.add(SwiftTrees.at(key, message));
      }
    }
  }

  /** Collects the key of each item of the literal, in order, without entering nested literals. */
  private static void collectKeys(final SyntaxNode node, final List<SyntaxNode> keys) {
    for (final SyntaxNode child : node.children()) {
      if (ITEM.equals(child.kind())) {
        SwiftTrees.child(child, EXPRESSION).ifPresent(keys::add);
      } else if (ITEMS.equals(child.kind())) {
        collectKeys(child, keys);
      }
    }
  }
}
