package dev.ward.domain.init;

import java.util.Map;
import java.util.Optional;

/**
 * Pure catalog of remediation links for gate executables. Init installs none of them: a gate whose
 * executable is missing is reported with the link, for a hand-written {@code semgrep} check or for
 * {@code zap}.
 */
public final class GateToolInstallCatalog {
  private static final Map<String, Entry> ENTRIES =
      Map.of(
          "semgrep",
          new Entry("semgrep", "https://semgrep.dev/docs/getting-started/"),
          "zap-baseline.py",
          new Entry("zap-baseline.py", "https://www.zaproxy.org/docs/docker/baseline-scan/"));

  private GateToolInstallCatalog() {}

  /** Returns the guidance for {@code executable}, if the catalog knows it. */
  public static Optional<Entry> forExecutable(final String executable) {
    return Optional.ofNullable(ENTRIES.get(executable));
  }

  /**
   * Returns the manual installation guidance for {@code executable}, or {@code null} when unknown.
   */
  public static String installHint(final String executable) {
    return forExecutable(executable).map(Entry::manualGuidance).orElse(null);
  }

  /**
   * Where to read how to install one gate executable.
   *
   * @param executable the executable name on the path
   * @param manualGuidance where to read installation instructions
   */
  public record Entry(String executable, String manualGuidance) {}
}
