package dev.ward.analysis.domain.rule;

import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The {@code ward:architecture/layers} rule: reports every dependency edge that leaves a file in a
 * {@code from} layer for a file in a {@code forbidden} layer, once per boundary it breaks, at the
 * reference that creates the edge. Java and Swift test sources are not checked.
 */
public final class LayerDependencyRule implements CompiledRule {

  /** The id of the rule, the same in every language. */
  public static final String ID = "ward:architecture/layers";

  private static final String JAVA_TEST_ROOT = "/src/test/";
  private static final String SWIFT_TEST_FOLDER = "Tests";

  private final RuleDefinition definition;
  private final List<LayerBoundary> boundaries;

  /**
   * Creates the rule that enforces {@code boundaries}, described by {@code definition}.
   *
   * @throws NullPointerException if a field is missing
   */
  public LayerDependencyRule(
      final RuleDefinition definition, final List<LayerBoundary> boundaries) {
    this.definition = Objects.requireNonNull(definition, "definition");
    this.boundaries = List.copyOf(boundaries);
  }

  @Override
  public RuleDefinition definition() {
    return definition;
  }

  /** Reads no syntax: the rule needs the dependency view, so it reports nothing without one. */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root, final DependencyView dependencies) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final DependencyEdge edge : dependencies.outgoing()) {
      if (!isTestSource(edge.from())) {
        boundaries.forEach(boundary -> violation(edge, boundary).ifPresent(matches::add));
      }
    }
    return List.copyOf(matches);
  }

  private static Optional<RuleMatch> violation(
      final DependencyEdge edge, final LayerBoundary boundary) {
    final String source = firstLayer(edge.from(), boundary.from());
    final String target = firstLayer(edge.to(), boundary.forbidden());
    if (source == null || target == null) {
      return Optional.empty();
    }
    return Optional.of(
        new RuleMatch(
            edge.reference().span(),
            "Layer " + source + " must not depend on " + target + ": " + edge.to()));
  }

  private static String firstLayer(final String path, final List<String> layers) {
    return layers.stream()
        .filter(layer -> LayerMembership.inLayer(path, layer))
        .findFirst()
        .orElse(null);
  }

  private static boolean isTestSource(final String path) {
    final String rooted = "/" + path;
    return (path.endsWith(".java") && rooted.contains(JAVA_TEST_ROOT))
        || (path.endsWith(".swift") && rooted.contains("/" + SWIFT_TEST_FOLDER + "/"));
  }
}
