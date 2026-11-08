package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports uses of a local variable whose nullness the code has just established: a variable
 * declared as {@code null} or as a new object and never assigned again, or a variable inside the
 * branch of an {@code if} that tests it against {@code null}. Only a variable that the branch or
 * the rest of its block never assigns is followed; anything else means no finding.
 */
public final class KnownNullCheck implements RuleCheck {

  /** What the check reports. */
  public enum Mode {
    /** A known null value is read, for instance passed on. */
    LOAD,
    /** A value known to be null is compared with null again. */
    NULL_RECHECK,
    /** A value known to be non-null is compared with null. */
    NONNULL_COMPARISON
  }

  private static final Pattern CONDITION = Pattern.compile("^(\\w+)(==|!=)null$");
  private static final Pattern REVERSED = Pattern.compile("^null(==|!=)(\\w+)$");
  private static final Pattern NULL_INIT = Pattern.compile("^\\w+=null$");
  private static final Pattern NONNULL_INIT = Pattern.compile("^\\w+=(?:new[\\w.]+[(<\\[]|\"|\\d)");
  private static final String STATEMENT = "statement";
  private static final String BLOCK = "block";
  private static final String BLOCK_STATEMENT = "blockStatement";
  private static final String DECLARATION = "localVariableDeclaration";
  private static final String DECLARATOR = "variableDeclarator";
  private static final int CONDITION_AT = 2;
  private static final int ELSE_AT = 1;
  private static final String IF = "'if'";
  private static final String EQUALS = "==";

  private final Mode mode;
  private final String message;

  /**
   * Creates the check.
   *
   * @param mode what the check reports
   * @param message the message; {@code {value}} is the variable name
   */
  public KnownNullCheck(final Mode mode, final String message) {
    this.mode = Objects.requireNonNull(mode, "mode");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxNode node : SyntaxWalk.descendants(root)) {
      if (STATEMENT.equals(node.kind())) {
        ifBranches(node, matches);
      } else if (BLOCK.equals(node.kind())) {
        declarations(node, matches);
      }
    }
    return List.copyOf(matches);
  }

  private void ifBranches(final SyntaxNode statement, final List<RuleMatch> matches) {
    final List<SyntaxNode> parts = statement.children();
    if (parts.isEmpty() || !IF.equals(parts.get(0).kind())) {
      return;
    }
    final Optional<SyntaxNode> condition =
        parts.size() > CONDITION_AT ? Optional.of(parts.get(CONDITION_AT)) : Optional.empty();
    final List<SyntaxNode> branches =
        parts.stream().filter(part -> STATEMENT.equals(part.kind())).toList();
    if (condition.isEmpty() || branches.isEmpty()) {
      return;
    }
    final String text = SyntaxWalk.text(condition.get());
    final Matcher forward = CONDITION.matcher(text);
    final Matcher reversed = REVERSED.matcher(text);
    if (forward.matches()) {
      branches(forward.group(1), EQUALS.equals(forward.group(2)), branches, matches);
    } else if (reversed.matches()) {
      branches(reversed.group(2), EQUALS.equals(reversed.group(1)), branches, matches);
    }
  }

  /** The then-branch of {@code ==} sees a null value, its else-branch a non-null one. */
  private void branches(
      final String name,
      final boolean nullThen,
      final List<SyntaxNode> branches,
      final List<RuleMatch> matches) {
    follow(name, branches.get(0), nullThen, matches);
    if (branches.size() > ELSE_AT) {
      follow(name, branches.get(1), !nullThen, matches);
    }
  }

  private void declarations(final SyntaxNode block, final List<RuleMatch> matches) {
    final List<SyntaxNode> statements = block.children();
    for (int index = 0; index < statements.size(); index++) {
      for (final SyntaxNode declarator : SyntaxWalk.descendants(statements.get(index))) {
        if (isLocal(statements.get(index)) && DECLARATOR.equals(declarator.kind())) {
          final String text = SyntaxWalk.text(declarator);
          final boolean isNull = NULL_INIT.matcher(text).matches();
          if (isNull || NONNULL_INIT.matcher(text).find()) {
            final String name = text.substring(0, text.indexOf('='));
            follow(name, rest(statements, index), isNull, matches);
          }
        }
      }
    }
  }

  private static boolean isLocal(final SyntaxNode statement) {
    return BLOCK_STATEMENT.equals(statement.kind())
        && SyntaxWalk.descendants(statement).stream()
            .anyMatch(node -> DECLARATION.equals(node.kind()));
  }

  private static List<SyntaxNode> rest(final List<SyntaxNode> statements, final int index) {
    return statements.subList(index + 1, statements.size());
  }

  private void follow(
      final String name, final SyntaxNode branch, final boolean isNull, final List<RuleMatch> out) {
    follow(name, List.of(branch), isNull, out);
  }

  private void follow(
      final String name,
      final List<SyntaxNode> scope,
      final boolean isNull,
      final List<RuleMatch> out) {
    final List<SyntaxNode> tokens = NullTokens.tokens(scope);
    if (NullTokens.assigns(tokens, name)) {
      return;
    }
    for (final NullTokens.Occurrence use : NullTokens.uses(tokens, name)) {
      if (reports(use.use(), isNull)) {
        out.add(CheckSupport.at(use.token().span(), CheckSupport.fill(message, name, "")));
      }
    }
  }

  private boolean reports(final NullTokens.Use use, final boolean isNull) {
    return switch (mode) {
      case LOAD -> isNull && use == NullTokens.Use.LOAD;
      case NULL_RECHECK -> isNull && use == NullTokens.Use.NULL_COMPARISON;
      default -> !isNull && use == NullTokens.Use.NULL_COMPARISON;
    };
  }
}
