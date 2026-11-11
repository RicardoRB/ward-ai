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
 * Reports an except clause whose tuple names a builtin exception class together with one of its
 * base classes or an alias of itself, like Pylint's {@code overlapping-except}: the narrower class
 * is already caught by the wider one. Shortcut: only plain names of builtin exceptions are read, a
 * class of the module or a dotted name gives no finding.
 */
public final class OverlappingExceptCheck implements RuleCheck {

  private static final Set<String> CLAUSES = Set.of("except_clause");
  private static final Pattern TUPLE =
      Pattern.compile("^except\\(([A-Za-z_][\\w]*(?:,[\\w]+)+)\\)");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public OverlappingExceptCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, CLAUSES)) {
      final Matcher tuple = TUPLE.matcher(SyntaxWalk.text(visit.node()));
      if (tuple.find() && overlaps(List.of(tuple.group(1).split(",")))) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  private static boolean overlaps(final List<String> names) {
    final List<String> canonical = names.stream().map(ExceptionHierarchy::canonical).toList();
    for (int index = 0; index < canonical.size(); index++) {
      final String name = canonical.get(index);
      final Set<String> ancestors = ExceptionHierarchy.ancestors(name, java.util.Map.of());
      for (int other = 0; other < canonical.size(); other++) {
        if (other != index
            && (ancestors.contains(canonical.get(other))
                || other < index && name.equals(canonical.get(other)))) {
          return true;
        }
      }
    }
    return false;
  }
}
