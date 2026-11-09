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
 * Reports a loop that iterates a name directly and changes that name in its body, like pylint
 * {@code modified-iterating-dict}, {@code modified-iterating-list} and {@code
 * modified-iterating-set}. The name must be bound in this module to a literal or a constructor call
 * of its kind; a name of unknown type is not judged.
 */
public final class ModifiedIterationCheck implements RuleCheck {

  /** The container kinds the check knows, one per rule. */
  public enum Kind {
    /** A dict: keys added, deleted or popped while iterating it. */
    DICT,
    /** A list: items added, removed or popped while iterating it. */
    LIST,
    /** A set: items added, removed or popped while iterating it. */
    SET
  }

  private static final Set<String> LOOPS = Set.of("for_stmt");
  private static final Pattern HEADER = Pattern.compile("^for[^:]*?in(\\w+):");
  private final Kind kind;
  private final String message;

  /**
   * Creates the check.
   *
   * @param kind the container kind to judge
   * @param message the message of each finding
   */
  public ModifiedIterationCheck(final Kind kind, final String message) {
    this.kind = Objects.requireNonNull(kind, "kind");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final String module = SyntaxWalk.text(root);
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, LOOPS)) {
      final String text = SyntaxWalk.text(visit.node());
      final Matcher header = HEADER.matcher(text);
      if (header.find()
          && isKind(module, header.group(1))
          && modifies(text.substring(header.end()), header.group(1))) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  /** True when the module binds the name to a literal or constructor of this kind. */
  private boolean isKind(final String module, final String name) {
    final Matcher bound =
        Pattern.compile("\\n?" + Pattern.quote(name) + "=(?!=)([^\\n]*)").matcher(module);
    boolean found = false;
    while (bound.find()) {
      found = found || valueIs(bound.group(1));
    }
    return found;
  }

  private boolean valueIs(final String value) {
    return switch (kind) {
      case DICT -> value.matches("\\{\\s*\\}.*|\\{[^}]*:.*|dict\\(.*");
      case LIST -> value.matches("\\[.*|list\\(.*");
      case SET -> value.matches("set\\(.*|\\{[^}:]+\\}.*");
    };
  }

  private boolean modifies(final String body, final String name) {
    final String n = Pattern.quote(name);
    final List<String> patterns =
        switch (kind) {
          case DICT ->
              List.of(
                  n + "\\[[^\\]]*\\]=(?!=)",
                  "del" + n + "\\[",
                  n + "\\.(?:pop|popitem|update|clear|setdefault)\\(");
          case LIST ->
              List.of(
                  n + "\\.(?:append|extend|insert|remove|pop|clear)\\(",
                  "del" + n + "\\[",
                  n + "\\[[^\\]]*\\]=(?!=)");
          case SET -> List.of(n + "\\.(?:add|discard|remove|pop|clear|update)\\(");
        };
    return patterns.stream().anyMatch(pattern -> Pattern.compile(pattern).matcher(body).find());
  }
}
