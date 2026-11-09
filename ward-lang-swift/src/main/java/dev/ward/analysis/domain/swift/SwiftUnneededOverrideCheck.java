package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reports an {@code override func} whose body only calls {@code super} with the same name and the
 * same arguments as its own parameters, like SwiftLint's {@code unneeded_override}. Attributes,
 * default parameters and {@code try!} or {@code try?} keep the override, as in SwiftLint.
 */
public final class SwiftUnneededOverrideCheck implements RuleCheck {

  private static final String FUNCTION = "function_declaration";
  private static final String HEAD = "function_head";
  private static final String NAME = "function_name";
  private static final String SIGNATURE = "function_signature";
  private static final String PARAMETER_CLAUSE = "parameter_clause";
  private static final String PARAMETER = "parameter";
  private static final String BODY = "function_body";
  private static final String CODE_BLOCK = "code_block";
  private static final String ATTRIBUTES = "attributes";
  private static final String OVERRIDE = "override";
  private static final String DEFAULT = "=";
  private static final String UNDERSCORE = "_";
  private static final String COLON = ":";
  private static final Set<String> LEADING_KEYWORDS = Set.of("return", "try", "await");
  private static final String STATEMENT = "statement";
  private static final Set<String> STATEMENT_LISTS = Set.of("statements", "statements_impl");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public SwiftUnneededOverrideCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxNode function : SwiftTrees.descendants(root, FUNCTION)) {
      if (isUnneeded(function)) {
        matches.add(SwiftTrees.at(function, message));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean isUnneeded(final SyntaxNode function) {
    final boolean overriding =
        SwiftTrees.child(function, HEAD).map(head -> SwiftTrees.hasToken(head, OVERRIDE)).orElse(false);
    if (!overriding || hasAttributes(function)) {
      return false;
    }
    final Optional<String> name = SwiftTrees.child(function, NAME).map(SwiftTrees::compact);
    final Optional<String> expected = name.flatMap(n -> superCall(function, n));
    return expected.isPresent() && onlyCallsSuper(function, expected.get());
  }

  /** The call that forwards every parameter to {@code super}, or empty when a default is present. */
  private static Optional<String> superCall(final SyntaxNode function, final String name) {
    final List<String> arguments = new ArrayList<>();
    for (final SyntaxNode parameter : parameters(function)) {
      if (SwiftTrees.hasToken(parameter, DEFAULT)) {
        return Optional.empty();
      }
      arguments.add(forwardedArgument(parameter));
    }
    return Optional.of("super." + name + "(" + String.join(",", arguments) + ")");
  }

  /** Attributes are children of the function head in the grammar, so both places are checked. */
  private static boolean hasAttributes(final SyntaxNode function) {
    return SwiftTrees.child(function, ATTRIBUTES).isPresent()
        || SwiftTrees.child(function, HEAD).flatMap(head -> SwiftTrees.child(head, ATTRIBUTES)).isPresent();
  }

  private static List<SyntaxNode> parameters(final SyntaxNode function) {
    return SwiftTrees.child(function, SIGNATURE)
        .flatMap(signature -> SwiftTrees.child(signature, PARAMETER_CLAUSE))
        .map(clause -> SwiftTrees.descendants(clause, PARAMETER))
        .orElse(List.of());
  }

  /** The argument text a parameter forwards: its label, if any, then its internal name. */
  private static String forwardedArgument(final SyntaxNode parameter) {
    final List<String> tokens = new ArrayList<>();
    for (final SyntaxNode leaf : SwiftTrees.leaves(parameter)) {
      final String text = leaf.fields().get("text");
      if (COLON.equals(text)) {
        break;
      }
      tokens.add(text);
    }
    if (tokens.size() == 1) {
      return tokens.get(0) + COLON + tokens.get(0);
    }
    final String label = UNDERSCORE.equals(tokens.get(0)) ? "" : tokens.get(0) + COLON;
    return label + tokens.get(1);
  }

  private static boolean onlyCallsSuper(final SyntaxNode function, final String expected) {
    final List<SyntaxNode> statements = bodyStatements(function);
    if (statements.size() != 1) {
      return false;
    }
    final String call = SwiftTrees.compact(statements.get(0));
    return call.equals(expected) || withoutLeadingKeywords(call).equals(expected);
  }

  private static String withoutLeadingKeywords(final String statement) {
    String text = statement;
    boolean stripped = true;
    while (stripped) {
      stripped = false;
      for (final String keyword : LEADING_KEYWORDS) {
        if (text.startsWith(keyword)) {
          text = text.substring(keyword.length());
          stripped = true;
        }
      }
    }
    return text;
  }

  /** The statements of the body block itself, not those nested inside them. */
  private static List<SyntaxNode> bodyStatements(final SyntaxNode function) {
    final List<SyntaxNode> statements = new ArrayList<>();
    SwiftTrees.child(function, BODY)
        .flatMap(body -> SwiftTrees.child(body, CODE_BLOCK))
        .ifPresent(block -> collectStatements(block, statements));
    return statements;
  }

  private static void collectStatements(final SyntaxNode node, final List<SyntaxNode> out) {
    for (final SyntaxNode child : node.children()) {
      if (STATEMENT.equals(child.kind())) {
        out.add(child);
      } else if (STATEMENT_LISTS.contains(child.kind())) {
        collectStatements(child, out);
      }
    }
  }
}
