package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.port.DependencyResolver;
import dev.ward.analysis.domain.port.DependencySupport;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Groups supports by their resolver, so a resolver shared by several languages is called once. */
final class ResolverGroups {

  private ResolverGroups() {}

  /**
   * Returns each distinct resolver with the languages it is registered for, in registration order.
   */
  static Map<DependencyResolver, Set<AnalysisLanguage>> of(final List<DependencySupport> supports) {
    return supports.stream()
        .collect(
            Collectors.groupingBy(
                DependencySupport::resolver,
                LinkedHashMap::new,
                Collectors.mapping(
                    DependencySupport::language,
                    Collectors.toCollection(() -> EnumSet.noneOf(AnalysisLanguage.class)))));
  }
}
