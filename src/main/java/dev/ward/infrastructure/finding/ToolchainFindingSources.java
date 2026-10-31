package dev.ward.infrastructure.finding;

import java.util.List;
import java.util.stream.Stream;
import javax.inject.Inject;

/** The finding sources of every toolchain's own gates. */
public final class ToolchainFindingSources {
  private final List<GateFindingSource> sources;

  /** Groups the per-toolchain finding sources that Dagger injects. */
  @Inject
  public ToolchainFindingSources(
      final JvmFindingSources jvm,
      final NodeFindingSources node,
      final PythonFindingSources python,
      final SwiftFindingSources swift) {
    this.sources =
        Stream.of(jvm.sources(), node.sources(), python.sources(), swift.sources())
            .flatMap(List::stream)
            .toList();
  }

  List<GateFindingSource> sources() {
    return sources;
  }
}
