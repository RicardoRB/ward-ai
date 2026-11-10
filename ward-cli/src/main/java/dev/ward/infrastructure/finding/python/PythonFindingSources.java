package dev.ward.infrastructure.finding.python;

import dev.ward.infrastructure.finding.GateFindingSource;
import java.util.List;
import javax.inject.Inject;

/** The finding sources of the Python toolchain's gates. */
public final class PythonFindingSources {
  private final List<GateFindingSource> sources;

  /** Groups the Python finding sources that Dagger injects. */
  @Inject
  public PythonFindingSources(final MypyJsonFindings mypy) {
    this.sources = List.of(mypy);
  }

  /** Returns the finding sources of the Python gates. */
  public List<GateFindingSource> sources() {
    return sources;
  }
}
