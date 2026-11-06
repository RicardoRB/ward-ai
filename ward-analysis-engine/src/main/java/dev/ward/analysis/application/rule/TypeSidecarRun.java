package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.port.TypeSidecar;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The type sidecars of one run. A language's sidecar starts at most once, and only when a selected
 * rule needs types for that language; {@link #close()} stops every sidecar that started.
 */
public final class TypeSidecarRun implements AutoCloseable {

  private static final Logger LOGGER = Logger.getLogger(TypeSidecarRun.class.getName());

  private final Map<AnalysisLanguage, TypeSidecar> sidecars;
  private final Map<AnalysisLanguage, Optional<TypeFacts>> started =
      new EnumMap<>(AnalysisLanguage.class);
  private final List<TypeSidecar> opened = new ArrayList<>();
  private final Optional<Path> classpathFile;

  /**
   * Creates the run over the sidecars registered per language, for a project without a classpath.
   */
  public TypeSidecarRun(final Map<AnalysisLanguage, TypeSidecar> sidecars) {
    this(sidecars, Optional.empty());
  }

  /**
   * Creates the run over the sidecars registered per language; {@code classpathFile} is the
   * project's {@code java-classpath} file, handed to every sidecar that uses one.
   */
  public TypeSidecarRun(
      final Map<AnalysisLanguage, TypeSidecar> sidecars, final Optional<Path> classpathFile) {
    this.sidecars = Map.copyOf(sidecars);
    this.classpathFile = classpathFile;
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
    if (!needsTypes(support.language(), rules) || facts(support.language()).isEmpty()) {
      return support;
    }
    final Set<RuleCapability> capabilities = EnumSet.noneOf(RuleCapability.class);
    capabilities.addAll(support.capabilities());
    capabilities.add(RuleCapability.TYPES);
    return new FrontendSupport(
        support.language(), support.frontend(), capabilities, support.lowering());
  }

  private static boolean needsTypes(
      final AnalysisLanguage language, final List<RuleDefinition> rules) {
    return rules.stream()
        .anyMatch(
            rule ->
                rule.capability() == RuleCapability.TYPES && rule.languages().contains(language));
  }

  /**
   * Returns the type facts of {@code language}, starting its sidecar on the first request. Empty
   * when no sidecar is registered or it could not start; a failed start is not retried in the run.
   */
  public Optional<TypeFacts> facts(final AnalysisLanguage language) {
    return started.computeIfAbsent(language, this::start);
  }

  private Optional<TypeFacts> start(final AnalysisLanguage language) {
    final TypeSidecar sidecar = sidecars.get(language);
    if (sidecar == null) {
      return Optional.empty();
    }
    opened.add(sidecar);
    final Optional<TypeFacts> facts = sidecar.start(classpathFile);
    if (facts.isEmpty() && LOGGER.isLoggable(Level.WARNING)) {
      LOGGER.warning("event=type-sidecar-not-started language=" + language);
    }
    return facts;
  }

  /** Stops every sidecar that was started in this run. */
  @Override
  public void close() {
    opened.forEach(TypeSidecar::close);
    opened.clear();
  }
}
