package dev.ward.infrastructure.finding;

import java.util.List;
import javax.inject.Inject;

/** The finding sources of the JVM toolchain's gates. */
public final class JvmFindingSources {
  private final List<GateFindingSource> sources;

  /** Groups the JVM finding sources that Dagger injects. */
  @Inject
  public JvmFindingSources(
      final CheckstyleXmlFindings checkstyle,
      final PmdXmlFindings pmd,
      final JavacOutputFindings javac,
      final SpotlessOutputFindings spotless) {
    this.sources = List.of(checkstyle, pmd, javac, spotless);
  }

  List<GateFindingSource> sources() {
    return sources;
  }
}
