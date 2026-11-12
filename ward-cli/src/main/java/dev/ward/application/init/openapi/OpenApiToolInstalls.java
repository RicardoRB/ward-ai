package dev.ward.application.init.openapi;

import java.util.List;
import java.util.Set;

/** The CI install step for the pinned oasdiff binary the OpenAPI diff gates run. */
public final class OpenApiToolInstalls {
  private static final String OASDIFF_VERSION = "1.35.0";

  private OpenApiToolInstalls() {}

  /** Adds the pinned oasdiff download that the OpenAPI diff gates run. */
  public static void add(final Set<String> ids, final List<String> install) {
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
