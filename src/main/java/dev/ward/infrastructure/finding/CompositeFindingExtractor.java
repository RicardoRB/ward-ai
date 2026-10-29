package dev.ward.infrastructure.finding;

import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.FindingExtractor;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.project.Project;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.inject.Inject;

/** Routes a gate result to the finding source registered for its tool type. */
public final class CompositeFindingExtractor implements FindingExtractor {
  private static final Logger LOGGER = Logger.getLogger(CompositeFindingExtractor.class.getName());

  private final Map<String, GateFindingSource> sources;

  /** Creates the extractor that delegates to the extractor of each supported gate. */
  @Inject
  public CompositeFindingExtractor(
      final JvmFindingSources jvm,
      final NodeFindingSources node,
      final GitleaksSarifFindings gitleaks) {
    this(
        Stream.of(jvm.sources(), node.sources(), List.<GateFindingSource>of(gitleaks))
            .flatMap(List::stream)
            .toList());
  }

  CompositeFindingExtractor(final List<GateFindingSource> sources) {
    final Map<String, GateFindingSource> byGate = new LinkedHashMap<>();
    sources.forEach(source -> byGate.put(source.gateId(), source));
    this.sources = Map.copyOf(byGate);
  }

  /** Returns the gates that have a finding source, for the gate contract test. */
  Set<String> gates() {
    return sources.keySet();
  }

  @Override
  public List<Finding> extract(
      final Project project, final GateResult result, final Instant gateStart) {
    final GateFindingSource source = sources.get(result.type());
    if (source == null) {
      LOGGER.log(Level.FINE, () -> "event=findings-unsupported gate=" + result.name());
      return List.of();
    }
    LOGGER.log(
        Level.INFO,
        () ->
            "event=findings-extract gate="
                + result.name()
                + " type="
                + result.type()
                + " source="
                + source.getClass().getSimpleName()
                + " build-tool="
                + project.buildTool());
    return source.extract(project, result, gateStart);
  }
}
