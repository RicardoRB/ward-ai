package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.port.TypeResolver;
import dev.ward.analysis.domain.port.TypeSidecar;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The type sidecars of one run. A language's sidecar starts at most once, and only when a selected
 * rule needs types for that language; {@link #close()} stops every sidecar that started.
 */
public final class TypeSidecarRun implements AutoCloseable {

  private final Map<AnalysisLanguage, TypeSidecar> sidecars;
  private final Map<AnalysisLanguage, Optional<TypeResolver>> started =
      new EnumMap<>(AnalysisLanguage.class);
  private final List<TypeSidecar> opened = new ArrayList<>();

  /** Creates the run over the sidecars registered per language. */
  public TypeSidecarRun(final Map<AnalysisLanguage, TypeSidecar> sidecars) {
    this.sidecars = Map.copyOf(sidecars);
  }

  /**
   * Returns the frontends with capability {@code TYPES} added for each language whose sidecar
   * starts and that a rule in {@code rules} needs types for. Other frontends are returned
   * unchanged, so their rules are reported as not run.
   */
  public List<FrontendSupport> withTypes(
      final List<FrontendSupport> frontends, final List<RuleDefinition> rules) {
    return frontends.stream().map(support -> withTypes(support, rules)).toList();
  }

  private FrontendSupport withTypes(
      final FrontendSupport support, final List<RuleDefinition> rules) {
    if (!needsTypes(support.language(), rules) || resolver(support.language()).isEmpty()) {
      return support;
    }
    final Set<RuleCapability> capabilities = EnumSet.noneOf(RuleCapability.class);
    capabilities.addAll(support.capabilities());
    capabilities.add(RuleCapability.TYPES);
    return new FrontendSupport(support.language(), support.frontend(), capabilities);
  }

  private static boolean needsTypes(
      final AnalysisLanguage language, final List<RuleDefinition> rules) {
    return rules.stream()
        .anyMatch(
            rule ->
                rule.capability() == RuleCapability.TYPES && rule.languages().contains(language));
  }

  /**
   * Returns the resolver of {@code language}, starting its sidecar on the first request. Empty when
   * no sidecar is registered or it could not start; a failed start is not retried in the run.
   */
  public Optional<TypeResolver> resolver(final AnalysisLanguage language) {
    return started.computeIfAbsent(language, this::start);
  }

  private Optional<TypeResolver> start(final AnalysisLanguage language) {
    final TypeSidecar sidecar = sidecars.get(language);
    if (sidecar == null) {
      return Optional.empty();
    }
    opened.add(sidecar);
    return sidecar.start();
  }

  /** Stops every sidecar that was started in this run. */
  @Override
  public void close() {
    opened.forEach(TypeSidecar::close);
    opened.clear();
  }
}
