package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports a local builder created with the default capacity whose literal appends, later in the
 * same block, add up to more than that capacity, as PMD's InsufficientStringBufferDeclaration does.
 *
 * <p>Shortcut: only appends of a string literal to the variable are counted, by the length of the
 * literal as written; appends of other values count as nothing, and nested blocks are not searched.
 */
public final class StringBufferCapacityCheck implements UnitCheck {

  /** The capacity of a StringBuilder or StringBuffer built with no argument. */
  static final int DEFAULT_CAPACITY = 16;

  private static final String BLOCK = "block";
  private static final Pattern DECLARATION =
      Pattern.compile(
          "^(?:java\\.lang\\.)?(StringBuilder|StringBuffer)(\\w+)=new(?:java\\.lang\\.)?\\1\\(\\);$");
  private static final String LITERAL_APPEND = "\\.append\\(\"((?:[^\"\\\\]|\\\\.)*)\"\\);$";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public StringBufferCapacityCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(BLOCK))) {
      final List<SyntaxNode> statements =
          visit.node().children().stream().filter(node -> !node.kind().startsWith("'")).toList();
      for (int index = 0; index < statements.size(); index++) {
        final Matcher declaration = DECLARATION.matcher(SyntaxWalk.text(statements.get(index)));
        if (declaration.matches()
            && appendedLength(
                    statements.subList(index + 1, statements.size()), declaration.group(2))
                > DEFAULT_CAPACITY) {
          matches.add(CheckSupport.at(statements.get(index).span(), message));
        }
      }
    }
    return List.copyOf(matches);
  }

  private static int appendedLength(final List<SyntaxNode> statements, final String variable) {
    final Pattern append = Pattern.compile("^" + Pattern.quote(variable) + LITERAL_APPEND);
    int length = 0;
    for (final SyntaxNode statement : statements) {
      final Matcher literal = append.matcher(SyntaxWalk.text(statement));
      if (literal.find()) {
        length += literal.group(1).length();
      }
    }
    return length;
  }
}
