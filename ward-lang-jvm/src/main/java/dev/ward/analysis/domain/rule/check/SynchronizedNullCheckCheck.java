package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a {@code synchronized} block on a field of the class that also tests that field against
 * {@code null} inside the block: the lock would have thrown if it were null.
 */
public final class SynchronizedNullCheckCheck implements RuleCheck {

  private static final String SYNCHRONIZED = "'synchronized'";
  private static final String THIS = "this.";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the field name
   */
  public SynchronizedNullCheckCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return SyntaxWalk.visits(root, Set.of("classDeclaration")).stream()
        .flatMap(visit -> inClass(visit.node()).stream())
        .toList();
  }

  private List<RuleMatch> inClass(final SyntaxNode clazz) {
    final List<String> fields =
        ClassMembers.fieldMembers(clazz).stream()
            .flatMap(field -> ClassMembers.fieldNames(ClassMembers.declarationOf(field)).stream())
            .toList();
    return SyntaxWalk.descendants(clazz).stream()
        .filter(node -> "statement".equals(node.kind()))
        .filter(node -> node.children().stream().anyMatch(c -> SYNCHRONIZED.equals(c.kind())))
        .filter(node -> testsLock(node, fields))
        .map(node -> CheckSupport.at(node.span(), CheckSupport.fill(message, lock(node), "")))
        .toList();
  }

  private static boolean testsLock(final SyntaxNode statement, final List<String> fields) {
    final String lock = lock(statement);
    final String text = SyntaxWalk.text(statement);
    final String name = Pattern.quote(lock);
    return fields.contains(lock)
        && Pattern.compile(
                "(?:\\b|this\\.)" + name + "[!=]=null|null[!=]=(?:this\\.)?" + name + "\\b")
            .matcher(text.substring(text.indexOf('{') < 0 ? 0 : text.indexOf('{')))
            .find();
  }

  private static String lock(final SyntaxNode statement) {
    final String text =
        statement.children().stream()
            .filter(c -> "parExpression".equals(c.kind()) || "expression".equals(c.kind()))
            .findFirst()
            .map(SyntaxWalk::text)
            .orElse("");
    final String bare = text.startsWith("(") ? text.substring(1, text.length() - 1) : text;
    return bare.startsWith(THIS) ? bare.substring(THIS.length()) : bare;
  }
}
