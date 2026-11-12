package dev.ward.analysis.domain.openapi.breaking;

import dev.ward.analysis.domain.openapi.DocNode;

/**
 * A thing with a lifecycle that both documents have: an operation, a parameter or a property.
 *
 * @param base the node in the base document
 * @param revision the node in the revised document
 * @param method the upper-case method of the operation it belongs to
 * @param path the path of the operation it belongs to
 * @param what what it is, for a message
 */
record Subject(DocNode base, DocNode revision, String method, String path, String what) {

  /** A finding of {@code id} at {@code at}. */
  BreakingFinding finding(final String id, final DocNode at, final String message) {
    return new BreakingFinding(
        RowRule.PREFIX + id, method + " " + path + ": " + message, method, path, at);
  }
}
