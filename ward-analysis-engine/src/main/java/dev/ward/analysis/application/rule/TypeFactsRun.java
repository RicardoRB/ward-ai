package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.rule.RuleCapability;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The native type-fact sources of one run, by language. A language with a registered source gets
 * the {@code TYPES} capability; a language without one keeps its frontend, so its {@code TYPES}
 * rules are reported as not run, never as passed.
 */
public final class TypeFactsRun {

  private final Map<AnalysisLanguage, TypeFacts> sources;

  /** Creates the run over the native sources registered per language. */
  public TypeFactsRun(final Map<AnalysisLanguage, TypeFacts> sources) {
    this.sources = Map.copyOf(sources);
  }

  /** Returns the frontends with {@code TYPES} added for each language that has a source. */
  public List<FrontendSupport> withTypes(final List<FrontendSupport> frontends) {
    return frontends.stream().map(this::withTypes).toList();
  }

  /** Returns the type facts of {@code language}, or empty when no native resolver is registered. */
  public Optional<TypeFacts> facts(final AnalysisLanguage language) {
    return Optional.ofNullable(sources.get(language));
  }

  private FrontendSupport withTypes(final FrontendSupport support) {
    if (facts(support.language()).isEmpty()) {
      return support;
    }
    final Set<RuleCapability> capabilities = EnumSet.noneOf(RuleCapability.class);
    capabilities.addAll(support.capabilities());
    capabilities.add(RuleCapability.TYPES);
    return new FrontendSupport(
            support.language(),
            support.frontend(),
            capabilities,
            support.lowering(),
            support.scopes(),
            facts(support.language()))
        .withManifests(support.manifests());
  }
}
