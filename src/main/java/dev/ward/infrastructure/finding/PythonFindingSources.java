package dev.ward.infrastructure.finding;

import java.util.List;
import javax.inject.Inject;

/** The finding sources of the Python toolchain's gates. */
public final class PythonFindingSources {
  private final List<GateFindingSource> sources;

  /** Groups the Python finding sources that Dagger injects. */
  @Inject
  public PythonFindingSources(final RuffJsonFindings ruff, final MypyJsonFindings mypy) {
    this.sources = List.of(ruff, mypy);
  }

  List<GateFindingSource> sources() {
    return sources;
  }
}
