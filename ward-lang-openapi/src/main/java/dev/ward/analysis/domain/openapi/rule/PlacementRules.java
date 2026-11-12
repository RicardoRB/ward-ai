package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.OpenApiDocument.View;
import dev.ward.analysis.domain.openapi.OpenApiParts.Operation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The rules about where an object may appear: callbacks, webhooks and examples. */
final class PlacementRules {

  private static final String CALLBACKS = "callbacks";
  private static final String EXAMPLES = "examples";
  private static final String SERVERS = "servers";
  private static final String VALUE = "value";
  private static final String EXTERNAL_VALUE = "externalValue";

  private PlacementRules() {}

  /** {@code oas3-callbacks-in-callbacks}: a callback defines no callbacks of its own. */
  static List<OpenApiFinding> callbacksInCallbacks(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final Operation operation : document.parts().operations()) {
      for (final DocNode callback : children(document, operation.node(), CALLBACKS)) {
        for (final DocNode expression : children(document, callback, null)) {
          for (final DocNode item : children(document, expression, null)) {
            document
                .get(item, CALLBACKS, View.RESOLVED)
                .ifPresent(
                    nested ->
                        findings.add(
                            Findings.atValue(
                                nested, "Callbacks should not be defined within a callback")));
          }
        }
      }
    }
    return findings;
  }

  /** {@code oas3-1-servers-in-webhook}: a webhook defines no servers. */
  static List<OpenApiFinding> serversInWebhook(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    final Optional<DocNode> webhooks = webhooks(document);
    if (webhooks.isPresent()) {
      webhooks
          .get()
          .get(SERVERS)
          .ifPresent(
              servers ->
                  findings.add(
                      Findings.atValue(servers, "Servers should not be defined in a webhook.")));
      for (final DocNode item : children(document, webhooks.get(), null)) {
        for (final DocNode operation : children(document, item, null)) {
          document
              .get(operation, SERVERS, View.RESOLVED)
              .ifPresent(
                  servers ->
                      findings.add(
                          Findings.atValue(
                              servers, "Servers should not be defined in a webhook.")));
        }
      }
    }
    return findings;
  }

  /** {@code oas3-1-callbacks-in-webhook}: a webhook defines no callbacks. */
  static List<OpenApiFinding> callbacksInWebhook(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    final Optional<DocNode> webhooks = webhooks(document);
    if (webhooks.isPresent()) {
      for (final DocNode item : children(document, webhooks.get(), null)) {
        for (final DocNode operation : children(document, item, null)) {
          document
              .get(operation, CALLBACKS, View.RESOLVED)
              .ifPresent(
                  callbacks ->
                      findings.add(
                          Findings.atValue(
                              callbacks, "Callbacks should not be defined in a webhook.")));
        }
      }
    }
    return findings;
  }

  private static Optional<DocNode> webhooks(final OpenApiDocument document) {
    return document.isVersion31()
        ? document.get(document.root(), "webhooks", View.RESOLVED)
        : Optional.empty();
  }

  /** {@code oas3-examples-value-or-external-value}: an example has one of the two, not both. */
  static List<OpenApiFinding> exampleValue(final OpenApiDocument document) {
    final List<DocNode> examples = new ArrayList<>();
    for (final DocNode.Entry component : document.parts().component(EXAMPLES)) {
      examples.add(document.resolve(component.value()));
    }
    for (final String section : List.of("parameters", "headers")) {
      for (final DocNode.Entry component : document.parts().component(section)) {
        examples.addAll(examplesOf(document, component.value()));
      }
    }
    examples.addAll(pathExamples(document));
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode example : examples) {
      if (example.kind() == DocNode.Kind.OBJECT) {
        xor(example).ifPresent(message -> findings.add(Findings.atValue(example, message)));
      }
    }
    return findings;
  }

  private static List<DocNode> pathExamples(final OpenApiDocument document) {
    final List<DocNode> examples = new ArrayList<>();
    for (final DocNode.Entry path : document.parts().paths()) {
      for (final DocNode item : children(document, document.resolve(path.value()), null)) {
        for (final String container : List.of("content", "parameters", "headers")) {
          examples.addAll(holders(document, item, container));
        }
      }
    }
    return examples;
  }

  private static List<DocNode> holders(
      final OpenApiDocument document, final DocNode item, final String container) {
    final List<DocNode> examples = new ArrayList<>();
    for (final DocNode found : Nodes.named(document, item, container)) {
      for (final DocNode holder : children(document, found, null)) {
        examples.addAll(examplesOf(document, holder));
      }
    }
    return examples;
  }

  private static List<DocNode> examplesOf(final OpenApiDocument document, final DocNode holder) {
    return document
        .get(holder, EXAMPLES, View.RESOLVED)
        .map(map -> children(document, map, null))
        .orElse(List.of());
  }

  private static Optional<String> xor(final DocNode example) {
    final boolean value = example.get(VALUE).isPresent();
    final boolean external = example.get(EXTERNAL_VALUE).isPresent();
    return value == external
        ? Optional.of("Examples must have either \"value\" or \"externalValue\" field.")
        : Optional.empty();
  }

  /** The values of an object or the items of an array, each read as its target. */
  private static List<DocNode> children(
      final OpenApiDocument document, final DocNode node, final String key) {
    final List<DocNode> values = new ArrayList<>();
    final DocNode resolved = document.resolve(node);
    if (key == null) {
      resolved.children().forEach(child -> values.add(document.resolve(child)));
    } else {
      document
          .get(resolved, key, View.RESOLVED)
          .ifPresent(child -> child.children().forEach(item -> values.add(document.resolve(item))));
    }
    return values;
  }
}
