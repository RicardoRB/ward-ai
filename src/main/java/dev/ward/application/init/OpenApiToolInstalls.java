package dev.ward.application.init;

import java.util.List;
import java.util.Set;

/** The CI install steps for the pinned vacuum and oasdiff binaries the OpenAPI gates run. */
final class OpenApiToolInstalls {
  private static final String VACUUM_VERSION = "0.36.1";
  private static final String OASDIFF_VERSION = "1.35.0";

  private OpenApiToolInstalls() {}

  /** Adds the pinned vacuum and oasdiff downloads that the OpenAPI gates run. */
  static void add(final Set<String> ids, final List<String> install) {
    if (ids.contains("openapi-lint")) {
      install.add(
          "          curl -sSfL"
              + " \"https://github.com/daveshanley/vacuum/releases/download/v"
              + VACUUM_VERSION
              + "/vacuum_"
              + VACUUM_VERSION
              + "_linux_x86_64.tar.gz\" | sudo tar -xz -C /usr/local/bin vacuum\n");
    }
    if (ids.contains("openapi-diff") || ids.contains("openapi-compat")) {
      install.add(
          "          curl -sSfL"
              + " \"https://github.com/oasdiff/oasdiff/releases/download/v"
              + OASDIFF_VERSION
              + "/oasdiff_"
              + OASDIFF_VERSION
              + "_linux_amd64.tar.gz\" | sudo tar -xz -C /usr/local/bin oasdiff\n");
    }
  }
}
