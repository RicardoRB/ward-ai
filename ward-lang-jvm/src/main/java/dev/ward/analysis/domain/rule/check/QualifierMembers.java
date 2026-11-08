package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads what the methods and fields of a file declare for each type qualifier. */
final class QualifierMembers {

  private QualifierMembers() {}

  /** Returns what each method declares, by key. */
  static Map<String, QualifierScope.Method> methods(
      final SyntaxNode root, final Map<String, QualifierScope.Qualifier> known) {
    final Map<String, QualifierScope.Method> out = new HashMap<>();
    final Set<String> repeated = new HashSet<>();
    for (final SyntaxWalk.Visit visit :
        SyntaxWalk.visits(root, Set.of("methodDeclaration", "constructorDeclaration"))) {
      final String key = NullScope.key(visit);
      if (out.put(key, method(visit, known)) != null) {
        repeated.add(key);
      }
    }
    repeated.forEach(out::remove);
    return out;
  }

  private static QualifierScope.Method method(
      final SyntaxWalk.Visit visit, final Map<String, QualifierScope.Qualifier> known) {
    final SyntaxNode entry = QualifierReader.holder(visit.ancestors());
    final List<Map<String, QualWhen>> parameters =
        SyntaxWalk.descendants(visit.node()).stream()
            .filter(node -> "formalParameter".equals(node.kind()))
            .map(param -> QualifierReader.applied(NullSyntax.annotations(param), known))
            .toList();
    final Map<String, QualWhen> returns =
        entry == null ? Map.of() : QualifierReader.applied(NullSyntax.annotations(entry), known);
    return new QualifierScope.Method(returns, parameters);
  }

  /** Returns what each field declares, by {@code Type#name}. */
  static Map<String, Map<String, QualWhen>> fields(
      final SyntaxNode root, final Map<String, QualifierScope.Qualifier> known) {
    final Map<String, Map<String, QualWhen>> out = new HashMap<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("fieldDeclaration"))) {
      final SyntaxNode entry = QualifierReader.holder(visit.ancestors());
      final String type = NullScope.typeOf(visit.ancestors());
      final Map<String, QualWhen> applied =
          entry == null ? Map.of() : QualifierReader.applied(NullSyntax.annotations(entry), known);
      SyntaxWalk.descendants(visit.node()).stream()
          .filter(node -> "variableDeclaratorId".equals(node.kind()))
          .forEach(id -> out.put(type + "#" + SyntaxWalk.text(id), applied));
    }
    return out;
  }
}
