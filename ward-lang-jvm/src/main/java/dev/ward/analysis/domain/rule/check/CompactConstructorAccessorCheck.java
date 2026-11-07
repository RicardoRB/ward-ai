package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a call of a record's own accessor inside its compact constructor. The fields are not
 * assigned there yet, so the accessor returns the default value; the component parameter is meant.
 */
public final class CompactConstructorAccessorCheck implements RuleCheck {

  private static final String RECORD = "recordDeclaration";
  private static final String RECORD_BODY = "recordBody";
  private static final String RECORD_HEADER = "recordHeader";
  private static final String COMPACT = "compactConstructorDeclaration";
  private static final String COMPONENT = "recordComponent";
  private static final String IDENTIFIER = "identifier";
  private static final String EXPRESSION = "expression";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the call
   */
  public CompactConstructorAccessorCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(RECORD))) {
      final SyntaxNode record = visit.node();
      final List<String> components = componentNames(record);
      if (!components.isEmpty()) {
        final Pattern accessor =
            Pattern.compile("^(?:this\\.)?(?:" + String.join("|", components) + ")\\(\\)$");
        compactConstructors(record).forEach(compact -> report(compact, accessor, matches));
      }
    }
    return List.copyOf(matches);
  }

  private void report(
      final SyntaxNode compact, final Pattern accessor, final List<RuleMatch> matches) {
    SyntaxWalk.descendants(compact).stream()
        .filter(node -> EXPRESSION.equals(node.kind()))
        .filter(node -> accessor.matcher(SyntaxWalk.text(node)).matches())
        .forEach(node -> matches.add(CheckSupport.at(node.span(), message)));
  }

  private static List<SyntaxNode> compactConstructors(final SyntaxNode record) {
    return record.children().stream()
        .filter(body -> RECORD_BODY.equals(body.kind()))
        .flatMap(body -> body.children().stream())
        .filter(member -> COMPACT.equals(member.kind()))
        .toList();
  }

  private static List<String> componentNames(final SyntaxNode record) {
    return record.children().stream()
        .filter(header -> RECORD_HEADER.equals(header.kind()))
        .flatMap(header -> SyntaxWalk.descendants(header).stream())
        .filter(node -> COMPONENT.equals(node.kind()))
        .map(CompactConstructorAccessorCheck::lastIdentifier)
        .filter(name -> !name.isEmpty())
        .map(Pattern::quote)
        .toList();
  }

  private static String lastIdentifier(final SyntaxNode component) {
    final List<SyntaxNode> identifiers =
        component.children().stream().filter(child -> IDENTIFIER.equals(child.kind())).toList();
    return identifiers.isEmpty() ? "" : SyntaxWalk.text(identifiers.get(identifiers.size() - 1));
  }
}
