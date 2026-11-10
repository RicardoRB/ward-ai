package dev.ward.infrastructure.finding.node;

import dev.ward.infrastructure.finding.GateFindingSource;
import java.util.List;
import javax.inject.Inject;

/** The finding sources of the Node toolchain's gates. */
public final class NodeFindingSources {
  private final List<GateFindingSource> sources;

  /** Groups the Node finding sources that Dagger injects. */
  @Inject
  public NodeFindingSources(final TscOutputFindings tsc) {
    this.sources = List.of(tsc);
  }

  /** Returns the finding sources of the Node gates. */
  public List<GateFindingSource> sources() {
    return sources;
  }
}
