package dev.ward.infrastructure.finding.node;

import dev.ward.infrastructure.finding.GateFindingSource;
import java.util.List;
import javax.inject.Inject;

/** The finding sources of the Node toolchain's gates. */
public final class NodeFindingSources {
  private final List<GateFindingSource> sources;

  /** Groups the Node finding sources that Dagger injects. */
  @Inject
  public NodeFindingSources(
      final EslintJsonFindings eslint, final TscOutputFindings tsc, final BiomeXmlFindings biome) {
    this.sources = List.of(eslint, tsc, biome);
  }

  /** Returns the finding sources of the Node gates. */
  public List<GateFindingSource> sources() {
    return sources;
  }
}
