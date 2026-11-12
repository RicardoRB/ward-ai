package dev.ward.analysis.domain.openapi;

import java.util.List;
import java.util.Optional;

/** Reads the parts of an operation, a path item and a response; each is empty when missing. */
public final class OperationNodes {

  private OperationNodes() {}

  /** Returns the items of {@code parameters} of a path item or an operation. */
  public static List<DocNode> parameters(final DocNode owner) {
    return owner.get("parameters").map(DocNode::items).orElse(List.of());
  }

  /** Returns the {@code requestBody} of an operation. */
  public static Optional<DocNode> requestBody(final DocNode operation) {
    return operation.get("requestBody");
  }

  /** Returns the response entries of an operation in file order. */
  public static List<DocNode.Entry> responses(final DocNode operation) {
    return operation.get("responses").map(DocNode::entries).orElse(List.of());
  }

  /** Returns the header entries of a response. */
  public static List<DocNode.Entry> headers(final DocNode response) {
    return response.get("headers").map(DocNode::entries).orElse(List.of());
  }
}
