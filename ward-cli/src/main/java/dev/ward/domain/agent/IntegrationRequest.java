package dev.ward.domain.agent;

import java.util.Objects;

/**
 * What an integration run should do.
 *
 * @param remove whether to remove every Ward-owned integration file
 * @param projectPath the {@code /}-separated path of the Ward project below the install root, or
 *     empty when the install root is the project root
 */
public record IntegrationRequest(boolean remove, String projectPath) {
  public static final IntegrationRequest INSTALL = new IntegrationRequest(false);
  public static final IntegrationRequest REMOVE = new IntegrationRequest(true);

  /** Creates a request for a project whose root is the install root. */
  public IntegrationRequest(final boolean remove) {
    this(remove, "");
  }

  /**
   * Validates the project path.
   *
   * @throws NullPointerException if {@code projectPath} is missing
   */
  public IntegrationRequest {
    Objects.requireNonNull(projectPath, "projectPath");
  }

  /** Returns this request for the Ward project at {@code path} below the install root. */
  public IntegrationRequest forProject(final String path) {
    return new IntegrationRequest(remove, path);
  }
}
