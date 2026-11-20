package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.FlowSites;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Finds the calls of one Rust statement or condition: a function call named by its last path
 * segment with the rest of the path as qualifier ({@code std::env::args()} is {@code args} on
 * {@code std::env}), and a method call named by its method with the receiver text as qualifier,
 * where {@code self} is {@code this}, and also a member read of the method name, whose argument is the receiver. Inner calls come before the calls that contain them; a nested
 * function or type is another body, a closure belongs to its statement. Macro calls are not sites:
 * their arguments are read by the statement that holds them.
 */
final class RustFlowSites implements FlowSites {
  private static final String PATH_SEPARATOR = "::";
  private static final String OPEN = "'('";
  private static final int MIN_PARTS = 3;
  private static final String SELF = "self";
  private static final String THIS = "this";
  private static final String PARAMS = "callParams";
  private static final Set<String> OTHER_BODIES =
      Set.of(
          RustKinds.FUNCTION,
          "structStruct",
          "tupleStruct",
          "enumeration",
          "union_",
          "trait_",
          "inherentImpl",
          "traitImpl"
      );

  private final Map<String, String> types;

  /**
   * Creates the finder of one body.
   *
   * @param types the type of each local or parameter of the body whose type is stated
   */
  RustFlowSites(final Map<String, String> types) {
    this.types = Map.copyOf(types);
  }

  @Override
  public List<FlowSite> of(final SyntaxNode node) {
    final List<FlowSite> found = new ArrayList<>();
    walk(node, found);
    return found;
  }

  private void walk(final SyntaxNode node, final List<FlowSite> found) {
    if (OTHER_BODIES.contains(node.kind())) {
      return;
    }
    node.children().forEach(child -> walk(child, found));
    if (RustKinds.EXPRESSION.equals(node.kind())) {
      found.addAll(sites(node));
    }
  }

  private List<FlowSite> sites(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    if (parts.size() < MIN_PARTS || !RustKinds.EXPRESSION.equals(parts.get(0).kind())) {
      return List.of();
    }
    if (RustTrees.isToken(parts.get(1), OPEN)) {
      return List.of(functionCall(node, parts));
    }
    final boolean method = parts.size() > 3
        && RustTrees.isToken(parts.get(1), RustKinds.DOT)
        && RustKinds.PATH_EXPR_SEGMENT.equals(parts.get(2).kind())
        && RustTrees.isToken(parts.get(3), OPEN);
    return method ? methodCall(node, parts) : List.of();
  }

  private FlowSite functionCall(final SyntaxNode node, final List<SyntaxNode> parts) {
    final String callee = RustTrees.text(parts.get(0)).replaceAll("<[^<>]*>", "");
    final int cut = callee.lastIndexOf(PATH_SEPARATOR);
    return new FlowSite(
        FlowSiteKind.CALL,
        cut < 0 ? callee : callee.substring(cut + PATH_SEPARATOR.length()),
        cut < 0 ? "" : callee.substring(0, cut),
        RustReceivers.calleeType(callee),
        List.of(),
        RustCallArguments.spans(RustTrees.child(node, PARAMS)),
        node.span()
    );
  }

  /**
   * The call, and a member read of the same name that lists the receiver as its argument, so a
   * specification can name a method as a sanitizer of its receiver, as {@code file_name} cleans a
   * path.
   */
  private List<FlowSite> methodCall(final SyntaxNode node, final List<SyntaxNode> parts) {
    final String receiver = RustTrees.text(parts.get(0));
    final String name = RustTrees.name(RustTrees.find(parts.get(2), RustKinds.IDENTIFIER));
    final String qualifier = SELF.equals(receiver) ? THIS : receiver;
    final Optional<String> type = RustReceivers.of(parts.get(0), types);
    return List.of(
        new FlowSite(
            FlowSiteKind.CALL,
            name,
            qualifier,
            type,
            List.of(),
            RustCallArguments.spans(RustTrees.child(node, PARAMS)),
            node.span()
        ),
        new FlowSite(
            FlowSiteKind.MEMBER_READ,
            name,
            qualifier,
            type,
            List.of(),
            List.of(parts.get(0).span()),
            node.span()
        )
    );
  }
}
