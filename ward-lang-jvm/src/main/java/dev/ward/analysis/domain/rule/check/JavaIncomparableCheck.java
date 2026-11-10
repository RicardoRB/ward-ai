package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a sorted collection created with no comparator and an element type that is known not to
 * be {@code Comparable}, as Error Prone's Incomparable does. Only well-known JDK types are read; a
 * type whose facts are unknown, or a user type, is never reported.
 */
public final class JavaIncomparableCheck implements RuleCheck {

  private static final Set<String> SORTED =
      Set.of("TreeSet", "TreeMap", "ConcurrentSkipListSet", "ConcurrentSkipListMap");
  private static final Set<String> NOT_COMPARABLE =
      Set.of(
          "java.lang.Object",
          "java.lang.StringBuilder",
          "java.lang.StringBuffer",
          "java.lang.Thread",
          "java.lang.Runnable",
          "java.lang.Class",
          "java.util.List",
          "java.util.ArrayList",
          "java.util.LinkedList",
          "java.util.Map",
          "java.util.HashMap",
          "java.util.Set",
          "java.util.HashSet");
  private static final FactNeed NEED = new FactNeed(Set.of("typeType"), Set.of(FactKind.TYPE));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the element type
   */
  public JavaIncomparableCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public FactNeed needs() {
    return NEED;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root,
      final FlowView flow,
      final ScopeView scopes,
      final UnitTypeFacts types,
      final SourceUnit unit) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("creator"))) {
      elementType(visit.node(), types)
          .ifPresent(
              type ->
                  matches.add(
                      CheckSupport.at(
                          visit.node().span(), CheckSupport.fill(message, type.name(), ""))));
    }
    return List.copyOf(matches);
  }

  private static Optional<TypeRef> elementType(
      final SyntaxNode creator, final UnitTypeFacts types) {
    final String name =
        JavaNodes.kid(creator, "createdName")
            .flatMap(created -> JavaNodes.kid(created, "identifier"))
            .map(SyntaxWalk::text)
            .orElse("");
    final boolean comparator = !argumentsEmpty(creator);
    if (!SORTED.contains(name) || comparator) {
      return Optional.empty();
    }
    return SyntaxWalk.descendants(creator).stream()
        .filter(node -> "typeType".equals(node.kind()))
        .findFirst()
        .flatMap(type -> types.typeOf(type.span()))
        .filter(type -> type.arrayDepth() == 0 && NOT_COMPARABLE.contains(type.name()));
  }

  private static boolean argumentsEmpty(final SyntaxNode creator) {
    return JavaNodes.kid(creator, "classCreatorRest")
        .flatMap(rest -> JavaNodes.kid(rest, "arguments"))
        .map(arguments -> JavaNodes.kid(arguments, "expressionList").isEmpty())
        .orElse(false);
  }
}
