package dev.ward.analysis.domain.openapi;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The standard places of an {@link OpenApiDocument}: {@code info}, {@code servers}, {@code tags},
 * {@code security}, {@code paths}, {@code components} and the operations. Each accessor returns the
 * nodes themselves and is empty, never null, when the document lacks the part.
 */
public final class OpenApiParts {

  private static final List<String> METHODS =
      List.of("get", "put", "post", "delete", "options", "head", "patch", "trace");

  private final OpenApiDocument document;

  OpenApiParts(final OpenApiDocument document) {
    this.document = document;
  }

  /** Returns the {@code info} object. */
  public Optional<DocNode> info() {
    return document.root().get("info");
  }

  /** Returns the items of {@code servers}. */
  public List<DocNode> servers() {
    return items("servers");
  }

  /** Returns the items of {@code tags}. */
  public List<DocNode> tags() {
    return items("tags");
  }

  /** Returns the items of the top-level {@code security}. */
  public List<DocNode> security() {
    return items("security");
  }

  /** Returns the path entries of {@code paths} in file order. */
  public List<DocNode.Entry> paths() {
    return document.root().get("paths").map(DocNode::entries).orElse(List.of());
  }

  /** Returns the entries of {@code webhooks} (OpenAPI 3.1) in file order. */
  public List<DocNode.Entry> webhooks() {
    return document.root().get("webhooks").map(DocNode::entries).orElse(List.of());
  }

  /** Returns the {@code components} object. */
  public Optional<DocNode> components() {
    return document.root().get("components");
  }

  /** Returns the entries of one components section, such as {@code schemas}, in file order. */
  public List<DocNode.Entry> component(final String section) {
    return components()
        .flatMap(components -> components.get(section))
        .map(DocNode::entries)
        .orElse(List.of());
  }

  /** Returns every operation of every path item, in file order of the paths and the methods. */
  public List<Operation> operations() {
    final List<Operation> operations = new ArrayList<>();
    for (final DocNode.Entry path : paths()) {
      final DocNode item = document.resolve(path.value());
      for (final DocNode.Entry method : item.entries()) {
        if (METHODS.contains(method.key())) {
          operations.add(
              new Operation(path.key(), method.key(), item, document.resolve(method.value())));
        }
      }
    }
    return operations;
  }

  private List<DocNode> items(final String key) {
    return document.root().get(key).map(DocNode::items).orElse(List.of());
  }

  /**
   * One operation of a path item.
   *
   * @param path the path key, such as {@code /pets}
   * @param method the lower-case HTTP method
   * @param pathItem the (resolved) path item the operation is in
   * @param node the (resolved) operation object
   */
  public record Operation(String path, String method, DocNode pathItem, DocNode node) {}
}
