package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads the Lombok annotations of a class. Lombok writes members the source does not show: a
 * constructor that sets the fields, accessors that read them. A check that judges a field by the
 * code in the file asks here whether Lombok may write or read it.
 */
final class LombokClasses {
  private static final Set<String> CONSTRUCTORS =
      Set.of(
          "AllArgsConstructor",
          "RequiredArgsConstructor",
          "NoArgsConstructor",
          "Data",
          "Value",
          "Builder",
          "SuperBuilder"
      );
  private static final Set<String> WRITERS =
      Set.of(
          "AllArgsConstructor",
          "RequiredArgsConstructor",
          "Data",
          "Value",
          "Builder",
          "SuperBuilder",
          "Setter",
          "With"
      );
  private static final Set<String> READERS =
      Set.of("Getter", "Data", "Value", "ToString", "EqualsAndHashCode");

  private LombokClasses() {}

  /** Returns the simple names of the annotations of the class declaration of {@code visit}. */
  static Set<String> annotations(final SyntaxWalk.Visit visit) {
    if (visit.ancestors().isEmpty()) {
      return Set.of();
    }
    final List<SyntaxNode> chain = new ArrayList<>(visit.ancestors());
    chain.add(visit.node());
    final Set<String> names = new HashSet<>();
    NullModel.holder(chain, chain.size() - 1)
        .forEach(node -> names.add(NullSyntax.simpleName(node)));
    return names;
  }

  /** Tells whether Lombok generates a constructor for a class with these annotations. */
  static boolean generatesConstructor(final Set<String> annotations) {
    return annotations.stream().anyMatch(CONSTRUCTORS::contains);
  }

  /** Tells whether Lombok may write the fields of a class with these annotations. */
  static boolean writesFields(final Set<String> annotations) {
    return annotations.stream().anyMatch(WRITERS::contains);
  }

  /** Tells whether Lombok may write or read the fields of a class with these annotations. */
  static boolean usesFields(final Set<String> annotations) {
    return writesFields(annotations) || annotations.stream().anyMatch(READERS::contains);
  }
}
