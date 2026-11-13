package dev.ward.analysis.domain.openapi.breaking;

import dev.ward.analysis.domain.openapi.DocNode;
import java.util.Objects;

/**
 * One breaking change found between a base and a revision document.
 *
 * @param ruleId the id of the rule, {@code ward:openapi/<oasdiff check id>}
 * @param message names the operation or component and says what changed
 * @param method the upper-case HTTP method of the operation, or the empty text when there is none
 * @param path the path of the operation, or the empty text when there is none
 * @param at the node of the revision document the finding points at; for a removal, the nearest
 *     enclosing node that still exists
 */
public record BreakingFinding(
    String ruleId, String message, String method, String path, DocNode at) {

  /**
   * Validates the parts.
   *
   * @throws NullPointerException if a part is missing
   */
  public BreakingFinding {
    Objects.requireNonNull(ruleId, "ruleId");
    Objects.requireNonNull(message, "message");
    Objects.requireNonNull(method, "method");
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(at, "at");
  }
}
