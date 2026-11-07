package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports each initializer that a type body declares after another member, when an earlier
 * initializer came before that member, like Checkstyle's {@code ConstructorsDeclarationGrouping}. A
 * member counts as an initializer when its text starts with its attributes and modifiers and then
 * {@code init}.
 */
public final class ConstructorGroupCheck implements RuleCheck {

  private static final Set<String> BODIES =
      Set.of("class_body", "struct_body", "extension_body", "protocol_body");
  private static final Pattern INITIALIZER =
      Pattern.compile(
          "^(@\\w+(\\([^)]*\\))?)*(public|open|internal|fileprivate|private|required|convenience"
              + "|override|final)*init[?!]?\\(");
  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding; {@code {value}} is the member's text
   */
  public ConstructorGroupCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit body : SyntaxWalk.visits(root, BODIES)) {
      matches.addAll(misplaced(body.node()));
    }
    return List.copyOf(matches);
  }

  private List<RuleMatch> misplaced(final SyntaxNode body) {
    final List<RuleMatch> matches = new ArrayList<>();
    boolean initialized = false;
    boolean leftGroup = false;
    for (final SyntaxNode member : body.children()) {
      final boolean initializer = INITIALIZER.matcher(SyntaxWalk.text(member)).find();
      if (initializer && leftGroup) {
        matches.add(new RuleMatch(member.span(), message.replace("{value}", "init")));
      }
      if (initializer) {
        initialized = true;
      } else if (initialized) {
        leftGroup = true;
      }
    }
    return matches;
  }
}
