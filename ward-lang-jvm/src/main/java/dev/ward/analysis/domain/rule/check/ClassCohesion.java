package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Measures tight class cohesion: the share of method pairs that use a common field. */
final class ClassCohesion {

  private static final String METHOD = "methodDeclaration";

  private ClassCohesion() {}

  /**
   * Returns the share of method pairs that use a common field of the class. A class with fewer than
   * two methods has no pairs, and its cohesion is zero.
   */
  static double of(final SyntaxNode clazz) {
    final Set<String> fields =
        ClassMembers.fieldMembers(clazz).stream()
            .flatMap(member -> ClassMembers.fieldNames(ClassMembers.declarationOf(member)).stream())
            .collect(Collectors.toUnmodifiableSet());
    final List<Set<String>> usage = new ArrayList<>();
    for (final SyntaxNode member : ClassMembers.callableMembers(clazz)) {
      final SyntaxNode declaration = ClassMembers.declarationOf(member);
      if (METHOD.equals(declaration.kind())) {
        usage.add(usedFields(declaration, fields));
      }
    }
    return share(usage);
  }

  private static double share(final List<Set<String>> usage) {
    final int methods = usage.size();
    final long pairs = (long) methods * (methods - 1) / 2;
    if (pairs == 0) {
      return 0;
    }
    long shared = 0;
    for (int first = 0; first < methods; first++) {
      for (int second = first + 1; second < methods; second++) {
        if (!Collections.disjoint(usage.get(first), usage.get(second))) {
          shared++;
        }
      }
    }
    return (double) shared / pairs;
  }

  private static Set<String> usedFields(final SyntaxNode method, final Set<String> fields) {
    return ClassMetrics.identifiers(method).stream()
        .filter(fields::contains)
        .collect(Collectors.toUnmodifiableSet());
  }
}
