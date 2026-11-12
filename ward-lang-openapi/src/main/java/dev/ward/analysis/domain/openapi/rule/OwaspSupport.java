package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.OpenApiParts.Operation;
import dev.ward.analysis.domain.openapi.OperationNodes;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the parts of the document the OWASP rules share: schemes, parameters, keys, URLs. */
final class OwaspSupport {

  private static final Pattern SCHEME = Pattern.compile("^([A-Za-z][A-Za-z0-9+.-]*):");

  private OwaspSupport() {}

  static List<DocNode> schemes(final OpenApiDocument document) {
    return document.parts().component("securitySchemes").stream()
        .map(entry -> document.resolve(entry.value()))
        .toList();
  }

  static List<DocNode> parameters(final OpenApiDocument document) {
    final List<DocNode> parameters = new ArrayList<>();
    document
        .parts()
        .component("parameters")
        .forEach(entry -> parameters.add(document.resolve(entry.value())));
    for (final DocNode.Entry path : document.parts().paths()) {
      OperationNodes.parameters(document.resolve(path.value()))
          .forEach(parameter -> parameters.add(document.resolve(parameter)));
    }
    for (final Operation operation : document.parts().operations()) {
      OperationNodes.parameters(operation.node())
          .forEach(parameter -> parameters.add(document.resolve(parameter)));
    }
    return parameters.stream().distinct().toList();
  }

  static Span key(final DocNode node, final String name) {
    return node.entries().stream()
        .filter(entry -> name.equals(entry.key()))
        .findFirst()
        .orElseThrow()
        .keySpan();
  }

  /** Tells whether a URL has a scheme other than https. */
  static boolean insecure(final String url) {
    final Matcher matcher = SCHEME.matcher(url);
    return matcher.find() && !"https".equalsIgnoreCase(matcher.group(1));
  }
}
