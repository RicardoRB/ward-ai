package dev.ward.infrastructure.finding.jvm;

import dev.ward.infrastructure.finding.GateFindingSource;
import java.util.List;
import javax.inject.Inject;

/** The finding sources of the JVM toolchain's gates. */
public final class JvmFindingSources {
  private final List<GateFindingSource> sources;

  /** Groups the JVM finding sources that Dagger injects. */
  @Inject
  public JvmFindingSources(final JavacOutputFindings javac) {
    this.sources = List.of(javac);
  }

  /** Returns the finding sources of the JVM gates. */
  public List<GateFindingSource> sources() {
    return sources;
  }
}
