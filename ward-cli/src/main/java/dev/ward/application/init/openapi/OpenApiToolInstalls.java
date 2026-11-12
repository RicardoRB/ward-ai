package dev.ward.application.init.openapi;

import java.util.List;
import java.util.Set;

/** The CI install steps for the pinned vacuum binary the {@code openapi-lint} gate runs. */
public final class OpenApiToolInstalls {
  private static final String VACUUM_VERSION = "0.36.1";

  private OpenApiToolInstalls() {}

  /** Adds the pinned vacuum download that the {@code openapi-lint} gate runs. */
  public static void add(final Set<String> ids, final List<String> install) {
    if (ids.contains("openapi-lint")) {
      install.add(
          "          curl -sSfL"
              + " \"https://github.com/daveshanley/vacuum/releases/download/v"
              + VACUUM_VERSION
              + "/vacuum_"
              + VACUUM_VERSION
              + "_linux_x86_64.tar.gz\" | sudo tar -xz -C /usr/local/bin vacuum\n");
    }
  }
}
