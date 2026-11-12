package dev.ward.analysis.domain.openapi.breaking;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.OpenApiParts.Operation;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The operations of one document by path and method. Paths that differ only in the names of their
 * parameters are the same path, as in oasdiff.
 */
final class OperationIndex {

  private static final Pattern PARAMETER = Pattern.compile("\\{[^}]*}");

  private final Map<String, Operation> operations = new LinkedHashMap<>();
  private final Map<String, DocNode.Entry> paths = new LinkedHashMap<>();

  OperationIndex(final OpenApiDocument document) {
    document.parts().paths().forEach(path -> paths.putIfAbsent(normalize(path.key()), path));
    for (final Operation operation : document.parts().operations()) {
      operations.putIfAbsent(key(operation.path(), operation.method()), operation);
    }
  }

  /** The operations in file order. */
  Iterable<Operation> all() {
    return operations.values();
  }

  /** The operation with the same method on the same path as {@code other}, if any. */
  Optional<Operation> counterpart(final Operation other) {
    return Optional.ofNullable(operations.get(key(other.path(), other.method())));
  }

  /** The path entry written like {@code path} here, if any. */
  Optional<DocNode.Entry> path(final String path) {
    return Optional.ofNullable(paths.get(normalize(path)));
  }

  private static String key(final String path, final String method) {
    return method + " " + normalize(path);
  }

  private static String normalize(final String path) {
    return PARAMETER.matcher(path).replaceAll("{}");
  }

  /** The upper-case method of {@code operation}. */
  static String method(final Operation operation) {
    return operation.method().toUpperCase(Locale.ROOT);
  }
}
