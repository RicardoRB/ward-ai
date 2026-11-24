package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;

/**
 * The keyed reads of a body, {@code a[key]} and {@code map.get(key)}. The key picks a member of the
 * object or map, so the value is a member of it and the key adds nothing to the value: a call named
 * {@code <test>} over the key lets a specification sanitize it. A tainted object still taints the
 * value, and so does a parameter, whose members the body does not know.
 */
final class NodeKeyedReads {
  private static final String TEST = "<test>";
  private static final int KEY_PARTS = 4;
  private static final int KEY_INDEX = 2;

  private NodeKeyedReads() {}

  /** The site for the key of {@code a[key]}, when {@code a} is not a parameter. */
  static Optional<FlowSite> index(
      final NodeTypes types,
      final SyntaxNode node,
      final List<SyntaxNode> parts
  ) {
    if (parts.size() != KEY_PARTS
        || !"[".equals(NodeFlowSyntax.text(parts.get(1)))
        || !"]".equals(NodeFlowSyntax.text(parts.get(KEY_PARTS - 1)))
        || types.isParameter(NodeFlowSyntax.text(parts.get(0)))) {
      return Optional.empty();
    }
    return Optional.of(keyTest(parts.get(KEY_INDEX).span(), node.span()));
  }

  /** The site for the key of {@code map.get(key)} on a name declared as a {@code Map}. */
  static Optional<FlowSite> mapGet(
      final NodeTypes types,
      final List<SyntaxNode> callee,
      final List<SyntaxNode> values
  ) {
    final boolean get = "get".equals(NodeFlowSyntax.text(callee.get(callee.size() - 1)));
    if (!get || values.size() != 1 || !types.isMap(NodeFlowSyntax.text(callee.get(0)))) {
      return Optional.empty();
    }
    return Optional.of(keyTest(values.get(0).span(), values.get(0).span()));
  }

  private static FlowSite keyTest(final Span key, final Span whole) {
    return new FlowSite(
        FlowSiteKind.CALL,
        TEST,
        "",
        Optional.empty(),
        List.of(),
        List.of(key),
        whole
    );
  }
}
