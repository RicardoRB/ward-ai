package dev.ward.infrastructure.cli.init;

import dev.ward.domain.config.WardException;
import java.util.List;

/** The {@code --install-tool} values, which init accepts but does not act on. */
final class InstallToolOption {
  private InstallToolOption() {}

  /**
   * Returns the values, after checking that each names a selected gate, as init always did.
   *
   * @throws WardException if a value is not a selected gate
   */
  static List<String> resolve(final List<String> requested, final List<String> gates) {
    final List<String> installs = requested == null ? List.of() : List.copyOf(requested);
    installs.forEach(
        install -> {
          if (!gates.contains(install)) {
            throw new WardException(
                "--install-tool " + install + " requires --gate " + install + ".");
          }
        });
    return installs;
  }
}
