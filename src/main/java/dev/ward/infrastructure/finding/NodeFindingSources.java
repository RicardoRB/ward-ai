package dev.ward.infrastructure.finding;

import java.util.List;
import javax.inject.Inject;

/** The finding sources of the Node toolchain's gates. */
public final class NodeFindingSources {
  private final List<GateFindingSource> sources;

  /** Groups the Node finding sources that Dagger injects. */
  @Inject
  public NodeFindingSources(
      final EslintJsonFindings eslint,
      final TscOutputFindings tsc,
      final BiomeCheckstyleFindings biome) {
    this.sources = List.of(eslint, tsc, biome);
  }

  List<GateFindingSource> sources() {
    return sources;
  }
}
