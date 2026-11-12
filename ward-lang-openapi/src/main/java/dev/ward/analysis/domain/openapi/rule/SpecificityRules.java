package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** {@code paths-specificity-order}: a path with a static segment comes before a templated one. */
final class SpecificityRules {

  private static final List<String> ORDER =
      List.of("get", "post", "put", "delete", "options", "head", "patch", "trace");

  private static final int INCOMPATIBLE = Integer.MIN_VALUE;

  private SpecificityRules() {}

  /**
   * Reports a path declared after a templated path that its static segment is more specific than.
   */
  static List<OpenApiFinding> order(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    final List<DocNode.Entry> paths = document.parts().paths();
    for (int earlier = 0; earlier < paths.size(); earlier++) {
      for (int later = earlier + 1; later < paths.size(); later++) {
        findings.addAll(pair(document, paths.get(earlier), paths.get(later)));
      }
    }
    return findings;
  }

  private static List<OpenApiFinding> pair(
      final OpenApiDocument document, final DocNode.Entry earlier, final DocNode.Entry later) {
    final DocNode earlierItem = document.resolve(earlier.value());
    final DocNode laterItem = document.resolve(later.value());
    final List<String> shared =
        ORDER.stream()
            .filter(
                method ->
                    AmbiguousPathRules.methods(earlierItem).contains(method)
                        && AmbiguousPathRules.methods(laterItem).contains(method))
            .map(method -> method.toUpperCase(Locale.ROOT))
            .toList();
    if (shared.isEmpty() || !shouldPrecede(later.key(), laterItem, earlier.key(), earlierItem)) {
      return List.of();
    }
    return List.of(
        Findings.in(
            later.value(),
            later.keySpan(),
            "path `"
                + later.key()
                + "` should be declared before `"
                + earlier.key()
                + "` for method(s) "
                + String.join(", ", shared)
                + " because a static segment at the first differing position is more specific than"
                + " a templated segment"));
  }

  private static boolean shouldPrecede(
      final String candidate,
      final DocNode candidateItem,
      final String current,
      final DocNode currentItem) {
    final List<String> candidateSegments = PathRules.segments(candidate);
    final List<String> currentSegments = PathRules.segments(current);
    if (candidateSegments.isEmpty() || candidateSegments.size() != currentSegments.size()) {
      return false;
    }
    int firstDifference = 0;
    for (int index = 0; index < candidateSegments.size(); index++) {
      final int verdict =
          compare(
              candidateSegments.get(index), candidateItem, currentSegments.get(index), currentItem);
      if (verdict == INCOMPATIBLE) {
        return false;
      }
      firstDifference = firstDifference == 0 ? verdict : firstDifference;
    }
    return firstDifference == 1;
  }

  /** 0 when the segments agree, 1 or -1 when a static one meets a variable, else incompatible. */
  private static int compare(
      final String one, final DocNode oneItem, final String other, final DocNode otherItem) {
    final boolean oneVariable = AmbiguousPathRules.isVariable(one);
    final boolean otherVariable = AmbiguousPathRules.isVariable(other);
    if (oneVariable && otherVariable) {
      return AmbiguousPathRules.compatible(one, oneItem, other, otherItem) ? 0 : INCOMPATIBLE;
    }
    if (!oneVariable && !otherVariable) {
      return one.equals(other) ? 0 : INCOMPATIBLE;
    }
    return oneVariable
        ? staticAgainstVariable(other, one, oneItem, -1)
        : staticAgainstVariable(one, other, otherItem, 1);
  }

  private static int staticAgainstVariable(
      final String literal, final String variable, final DocNode item, final int verdict) {
    final String type = AmbiguousPathRules.parameterType(item, AmbiguousPathRules.name(variable));
    return type.isEmpty() || literalMatches(literal, type) ? verdict : INCOMPATIBLE;
  }

  private static boolean literalMatches(final String literal, final String type) {
    return switch (type) {
      case "integer" -> literal.matches("-?[0-9]+");
      case "number" -> literal.matches("-?([0-9]+\\.?[0-9]*|\\.[0-9]+)");
      case "boolean" -> Set.of("true", "false").contains(literal);
      default -> true;
    };
  }
}
