package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.FlowSites;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Finds the flow sites of one Swift statement or condition: calls, member reads and member writes.
 * Inner sites come before the sites that contain them, and a nested function or type is another
 * body, so it is not entered. A closure belongs to the statement that holds it.
 *
 * <p>A call is named by its last name. An initializer call, such as {@code URL(fileURLWithPath:
 * p)}, is named with the label of its first argument, {@code URL(fileURLWithPath:)}, as Apple's
 * documentation names it, so {@code String(x)} and {@code String(contentsOfFile: x)} differ. A
 * member read that is not called lists its receiver as its one argument, so a specification can
 * name a property, such as {@code lastPathComponent}, as a sanitizer of the receiver. The receiver
 * type is known for a type name, a shared instance of a type ({@code FileManager.default}), a
 * creation ({@code Process()}) and a local or parameter of a declared or created type.
 * shortcut: the type of any other receiver is unknown; upgrade with a type sidecar.
 */
final class SwiftFlowSites implements FlowSites {
  private static final String POSTFIX = "postfix_expression";
  private static final String EXPRESSION = "expression";
  private static final String CALL_CLAUSE = "function_call_argument_clause";
  private static final String TRAILING_CLOSURE = "trailing_closure";
  private static final String DOT_TOKEN = "'.'";
  private static final String BRACKET_TOKEN = "'['";
  private static final String SELF = "self";
  private static final String THIS = "this";

  private final SwiftReceivers receivers;

  /**
   * Creates the finder of one body.
   *
   * @param types the type of each local or parameter of the body whose type is declared or created
   */
  SwiftFlowSites(final Map<String, String> types) {
    this.receivers = new SwiftReceivers(types);
  }

  @Override
  public List<FlowSite> of(final SyntaxNode node) {
    final List<FlowSite> found = new ArrayList<>();
    walk(node, found);
    return found;
  }

  private void walk(final SyntaxNode node, final List<FlowSite> found) {
    if (SwiftFlowSyntax.OTHER_BODIES.contains(node.kind())) {
      return;
    }
    if (POSTFIX.equals(node.kind())) {
      chain(node, false, false, found);
    } else if (EXPRESSION.equals(node.kind())) {
      expression(node, found);
    } else {
      node.children().forEach(child -> walk(child, found));
    }
  }

  /** An assignment to a member is a write site whose argument is the assigned value. */
  private void expression(final SyntaxNode node, final List<FlowSite> found) {
    final SyntaxNode target = SwiftFlowSyntax.child(node, "prefix_expression");
    final SyntaxNode operators = SwiftFlowSyntax.child(node, "binary_expressions");
    final SyntaxNode targetChain = target == null ? null : SwiftFlowSyntax.child(target, POSTFIX);
    if (operators == null || targetChain == null || !SwiftScopeExpressions.assignsTo(operators)) {
      node.children().forEach(child -> walk(child, found));
      return;
    }
    final Chain written = chain(targetChain, false, true, found);
    node.children().stream().filter(child -> child != target).forEach(child -> walk(child, found));
    if (written.member && !written.qualifier.isEmpty()) {
      final SyntaxNode value =
          SwiftFlowSyntax.child(
              SwiftFlowSyntax.child(operators, "binary_expression"),
              "prefix_expression"
          );
      final Span span = target.span();
      found.add(
          new FlowSite(
              FlowSiteKind.MEMBER_WRITE,
              written.name,
              written.qualifier,
              receivers.of(written.qualifier),
              List.of(),
              List.of(SwiftFlowSyntax.between(value.span(), node.span())),
              SwiftFlowSyntax.between(span, node.span())
          )
      );
    }
  }

  /** A postfix chain: each {@code .name} not called is a read, each call a site. */
  private Chain chain(
      final SyntaxNode node,
      final boolean called,
      final boolean target,
      final List<FlowSite> found
  ) {
    final List<SyntaxNode> parts = node.children();
    final SyntaxNode first = parts.get(0);
    if (!POSTFIX.equals(first.kind())) {
      walk(first, found);
      return Chain.primary(first);
    }
    final SyntaxNode next = parts.size() > 1 ? parts.get(1) : null;
    final Chain inner = chain(first, SwiftCallArguments.isCallee(next, called), false, found);
    if (next != null) {
      follow(inner, parts, called, target, found);
    }
    return inner;
  }

  private void follow(
      final Chain inner,
      final List<SyntaxNode> parts,
      final boolean called,
      final boolean target,
      final List<FlowSite> found
  ) {
    final SyntaxNode next = parts.get(1);
    switch (next.kind()) {
      case CALL_CLAUSE -> call(inner, SwiftCallArguments.of(next), found, next);
      case TRAILING_CLOSURE -> trailing(inner, next, found);
      case DOT_TOKEN -> member(inner, parts, called, target, found);
      case BRACKET_TOKEN -> subscript(inner, parts, found);
      default -> inner.extend(next.span());
    }
  }

  private void member(
      final Chain chain,
      final List<SyntaxNode> parts,
      final boolean called,
      final boolean target,
      final List<FlowSite> found
  ) {
    final Span receiver = chain.span();
    final String qualifier = chain.path;
    final SyntaxNode word = parts.get(parts.size() - 1);
    chain.member(SwiftFlowSyntax.text(parts.size() > 2 ? parts.get(2) : word), word.span());
    if (!called && !target) {
      found.add(
          new FlowSite(
              FlowSiteKind.MEMBER_READ,
              chain.name,
              qualifier,
              receivers.of(qualifier),
              List.of(),
              List.of(receiver),
              chain.span()
          )
      );
    }
  }

  private void subscript(
      final Chain chain,
      final List<SyntaxNode> parts,
      final List<FlowSite> found
  ) {
    parts.stream().skip(2).forEach(part -> walk(part, found));
    chain.opaque(parts.get(parts.size() - 1).span(), "[]");
  }

  private void trailing(final Chain chain, final SyntaxNode closure, final List<FlowSite> found) {
    walk(closure, found);
    if (!chain.name.isEmpty()) {
      found.add(site(chain, List.of(), List.of(closure.span()), closure.span()));
    }
    chain.opaque(closure.span(), "()");
  }

  private void call(
      final Chain chain,
      final List<SyntaxNode> arguments,
      final List<FlowSite> found,
      final SyntaxNode clause
  ) {
    arguments.forEach(argument -> walk(argument, found));
    if (!chain.name.isEmpty()) {
      final List<Span> spans = arguments.stream().map(SyntaxNode::span).toList();
      found.add(site(chain, arguments, spans, clause.span()));
    }
    chain.opaque(clause.span(), "()");
  }

  private FlowSite site(
      final Chain chain,
      final List<SyntaxNode> arguments,
      final List<Span> spans,
      final Span end
  ) {
    return new FlowSite(
        FlowSiteKind.CALL,
        SwiftCallArguments.name(chain.name, chain.qualifier, arguments),
        chain.qualifier,
        receivers.of(chain.qualifier),
        List.of(),
        spans,
        SwiftFlowSyntax.between(chain.start, end)
    );
  }

  /** The running state of one primary expression and its postfixes. */
  private static final class Chain {
    private final Span start;
    private Span end;
    private String path;
    private String name;
    private String qualifier = "";
    private boolean member;

    private Chain(final Span span, final String path, final String name) {
      this.start = span;
      this.end = span;
      this.path = path;
      this.name = name;
    }

    static Chain primary(final SyntaxNode node) {
      final String text = SwiftFlowSyntax.text(node);
      final boolean plain = text.matches("[A-Za-z_]\\w*");
      return new Chain(
          node.span(),
          SELF.equals(text) ? THIS : text,
          plain && !SELF.equals(text) ? text : ""
      );
    }

    void member(final String word, final Span span) {
      qualifier = path;
      name = word;
      path = path + "." + word;
      end = span;
      member = true;
    }

    void opaque(final Span span, final String suffix) {
      path = path + suffix;
      name = "";
      qualifier = path;
      end = span;
      member = false;
    }

    void extend(final Span span) {
      end = SwiftFlowSyntax.between(start, span);
    }

    Span span() {
      return SwiftFlowSyntax.between(start, end);
    }
  }
}
