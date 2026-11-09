package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.tomlj.TomlParseResult;

/**
 * Reports a binding whose initializer is a call that matches the callee and the argument pattern,
 * when its uses in scope include a call of the first method and no call of the last one. Used by
 * the Semgrep rule that pairs a decipher's {@code update} with its {@code final}.
 */
public final class NodeCallWithoutFollowUpCheck implements RuleCheck {

  private static final String VARIABLE_DECLARATION = "variableDeclaration";
  private static final Pattern INITIALIZER = Pattern.compile("\\s*[\\w$]+\\s*=\\s*([\\s\\S]+)");
  private static final char OPEN = '(';
  private static final char CLOSE = ')';
  private static final char COMMA = ',';

  private final Pattern call;
  private final int argumentIndex;
  private final Pattern argumentPattern;
  private final Pattern first;
  private final Pattern then;
  private final String message;

  /**
   * Creates the check.
   *
   * @param callee the called name, matched with any receiver before it
   * @param argumentIndex the argument of the call that the pattern must match
   * @param argumentPattern the pattern the argument's text must match
   * @param first the method whose call marks the binding as in use
   * @param then the method whose call must follow
   * @param message the message of each finding
   */
  public NodeCallWithoutFollowUpCheck(
      final String callee,
      final int argumentIndex,
      final String argumentPattern,
      final String first,
      final String then,
      final String message) {
    this.call = Pattern.compile("(?:[\\w$]+\\.)*" + Pattern.quote(callee) + "\\s*\\(");
    this.argumentIndex = argumentIndex;
    this.argumentPattern = Pattern.compile(argumentPattern);
    this.first = memberCall(first);
    this.then = memberCall(then);
    this.message = message;
  }

  /**
   * Builds the check from a rule body with the keys {@code callee}, {@code argument-index}, {@code
   * argument-pattern}, {@code first}, {@code then} and {@code message}.
   *
   * @param toml the parsed body of the rule
   * @return the check
   */
  public static RuleCheck fromToml(final TomlParseResult toml) {
    return new NodeCallWithoutFollowUpCheck(
        TomlValues.required(toml, "callee"),
        TomlValues.integer(toml, "argument-index"),
        TomlValues.required(toml, "argument-pattern"),
        TomlValues.required(toml, "first"),
        TomlValues.required(toml, "then"),
        TomlValues.required(toml, "message"));
  }

  private static Pattern memberCall(final String method) {
    return Pattern.compile("\\s*\\.\\s*" + Pattern.quote(method) + "\\s*\\(");
  }

  /**
   * Always fails: the check reads the scopes and the source text, which only {@link #matches(
   * SyntaxNode, FlowView, ScopeView, SourceUnit)} is given.
   *
   * @throws AnalysisFailedException always
   */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the call check needs the scopes and the source text");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final ScopeView scopes, final SourceUnit unit) {
    final UnitScopes unitScopes = scopes.scopes();
    final List<RuleMatch> found = new ArrayList<>();
    for (final Binding binding : unitScopes.bindings()) {
      if (unfinished(root, unitScopes, binding, unit.text())) {
        found.add(new RuleMatch(binding.declaration(), message));
      }
    }
    return found;
  }

  private boolean unfinished(
      final SyntaxNode root, final UnitScopes scopes, final Binding binding, final String text) {
    final Optional<String> initializer = initializer(root, binding.declaration(), text);
    if (initializer.isEmpty() || !initializes(initializer.get())) {
      return false;
    }
    final List<String> followers = followersOf(scopes.usesOf(binding), text);
    final boolean updated = followers.stream().anyMatch(s -> first.matcher(s).lookingAt());
    final boolean finished = followers.stream().anyMatch(s -> then.matcher(s).lookingAt());
    return updated && !finished;
  }

  private boolean initializes(final String initializer) {
    final Matcher callee = call.matcher(initializer);
    if (!callee.lookingAt()) {
      return false;
    }
    final Optional<String> argument = argumentAt(initializer, callee.end());
    return argument.isPresent() && argumentPattern.matcher(argument.get()).find();
  }

  private static List<String> followersOf(final List<NameReference> uses, final String text) {
    final List<String> followers = new ArrayList<>();
    for (final NameReference use : uses) {
      followers.add(text.substring(Math.min(use.span().endOffset(), text.length())));
    }
    return followers;
  }

  private static Optional<String> initializer(
      final SyntaxNode root, final Span declaration, final String text) {
    final Optional<SyntaxNode> node = declarationOf(root, declaration);
    if (node.isEmpty()) {
      return Optional.empty();
    }
    final String whole = text.substring(node.get().span().startOffset(), node.get().span().endOffset());
    final Matcher matcher = INITIALIZER.matcher(whole);
    return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
  }

  private static Optional<SyntaxNode> declarationOf(final SyntaxNode node, final Span declaration) {
    if (VARIABLE_DECLARATION.equals(node.kind()) && contains(node.span(), declaration)) {
      return Optional.of(node);
    }
    for (final SyntaxNode child : node.children()) {
      final Optional<SyntaxNode> found = declarationOf(child, declaration);
      if (found.isPresent()) {
        return found;
      }
    }
    return Optional.empty();
  }

  private static boolean contains(final Span outer, final Span inner) {
    return outer.startOffset() <= inner.startOffset() && inner.endOffset() <= outer.endOffset();
  }

  /** The text of the argument at {@link #argumentIndex}, given the offset just after the opening parenthesis. */
  private Optional<String> argumentAt(final String initializer, final int open) {
    final List<String> arguments = new ArrayList<>();
    final StringBuilder current = new StringBuilder();
    char quote = 0;
    int depth = 0;
    for (int i = open; i < initializer.length(); i++) {
      final char c = initializer.charAt(i);
      if (quote != 0) {
        current.append(c);
        quote = c == quote ? 0 : quote;
      } else if (c == '\'' || c == '"' || c == '`') {
        quote = c;
        current.append(c);
      } else if (c == OPEN || c == '[' || c == '{') {
        depth++;
        current.append(c);
      } else if ((c == CLOSE || c == ']' || c == '}') && depth > 0) {
        depth--;
        current.append(c);
      } else if (c == COMMA && depth == 0) {
        arguments.add(current.toString().trim());
        current.setLength(0);
      } else if (c == CLOSE) {
        arguments.add(current.toString().trim());
        break;
      } else {
        current.append(c);
      }
    }
    return argumentIndex < arguments.size()
        ? Optional.of(arguments.get(argumentIndex))
        : Optional.empty();
  }
}
