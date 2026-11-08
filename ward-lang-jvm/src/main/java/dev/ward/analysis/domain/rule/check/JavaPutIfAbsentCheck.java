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
 * Reports a {@code putIfAbsent} statement whose result is dropped while the value it passed is used
 * again later in the block, as SpotBugs' RV_RETURN_VALUE_OF_PUTIFABSENT_IGNORED does: the map may
 * hold another value than the one the code goes on to use.
 */
public final class JavaPutIfAbsentCheck implements RuleCheck {

  private static final Pattern PUT = Pattern.compile("^[\\w.]+\\.putIfAbsent\\(.*,(\\w+)\\);$");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the value variable
   */
  public JavaPutIfAbsentCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("block"))) {
      final List<SyntaxNode> statements = JavaNodes.statements(visit.node());
      for (int index = 0; index < statements.size(); index++) {
        final Matcher put = PUT.matcher(SyntaxWalk.text(statements.get(index)));
        if (put.matches()
            && usedAfter(statements.subList(index + 1, statements.size()), put.group(1))) {
          matches.add(
              CheckSupport.at(
                  statements.get(index).span(), CheckSupport.fill(message, put.group(1), "")));
        }
      }
    }
    return List.copyOf(matches);
  }

  private static boolean usedAfter(final List<SyntaxNode> later, final String name) {
    return later.stream()
        .flatMap(statement -> SyntaxWalk.descendants(statement).stream())
        .anyMatch(
            node -> JavaNodes.IDENTIFIER.equals(node.kind()) && name.equals(SyntaxWalk.text(node)));
  }
}
