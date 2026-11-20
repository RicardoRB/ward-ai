package dev.ward.analysis.domain.csharp;

import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.FlowSites;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Finds the flow sites of one C# statement or condition: calls, creations, member reads and member
 * writes. Inner sites come before the sites that contain them, and a local function or nested type
 * is another body, so it is not entered. A lambda belongs to the statement that holds it.
 *
 * <p>A call is named by its last name and a creation by the simple name of its type, so {@code new
 * SqlCommand(sql, conn)} is a call named {@code SqlCommand}. A member read that is not called lists
 * its receiver as its one argument. The receiver type is known for a type name used as a
 * qualifier, as in {@code File.ReadAllText(p)}, and for a local or parameter of a declared or
 * created type. shortcut: the type of any other receiver is unknown; upgrade with a type sidecar.
 */
final class CSharpFlowSites implements FlowSites {
  private static final String PRIMARY = "primary_expression";
  private static final String CREATION = "object_creation_expression";
  private static final String THIS = "this";
  private static final String SIMPLE_NAME = "simple_name";
  private static final String PAREN = "(";
  private static final String BRACKET = "[";
  private static final int AFTER_FIRST = 1;
  private static final Pattern PLAIN = Pattern.compile("[A-Za-z_]\\w*");
  private static final int TARGET = 0;
  private static final int VALUE = 2;

  private final CSharpReceivers receivers;

  /**
   * Creates the finder of one body.
   *
   * @param types the type of each local or parameter of the body whose type is declared or created
   */
  CSharpFlowSites(final Map<String, String> types) {
    this.receivers = new CSharpReceivers(types);
  }

  @Override
  public List<FlowSite> of(final SyntaxNode node) {
    final List<FlowSite> found = new ArrayList<>();
    walk(node, found);
    return found;
  }

  private void walk(final SyntaxNode node, final List<FlowSite> found) {
    if (CSharpFlowSyntax.OTHER_BODIES.contains(node.kind())) {
      return;
    }
    if (PRIMARY.equals(node.kind())) {
      chain(node, false, false, found);
    } else if (CSharpFlowSyntax.isAssignment(node)) {
      assignment(node, found);
    } else {
      node.children().forEach(child -> walk(child, found));
    }
  }

  /** An assignment to a member is a write site whose argument is the assigned value. */
  private void assignment(final SyntaxNode node, final List<FlowSite> found) {
    final SyntaxNode target = node.children().get(TARGET);
    final SyntaxNode value = node.children().get(VALUE);
    final Optional<SyntaxNode> targetChain = CSharpSyntax.soleDescendant(target, PRIMARY);
    if (targetChain.isEmpty()) {
      node.children().forEach(child -> walk(child, found));
      return;
    }
    final Chain written = chain(targetChain.get(), false, true, found);
    walk(value, found);
    if (written.member && !written.qualifier.isEmpty()) {
      found.add(
          new FlowSite(
              FlowSiteKind.MEMBER_WRITE,
              written.name,
              written.qualifier,
              receivers.of(written.qualifier),
              List.of(),
              List.of(value.span()),
              CSharpFlowSyntax.between(target.span(), node.span())
          )
      );
    }
  }

  /** A primary expression: each {@code .name} not called is a read, each call a site. */
  private Chain chain(
      final SyntaxNode node,
      final boolean called,
      final boolean target,
      final List<FlowSite> found
  ) {
    final List<SyntaxNode> parts = node.children();
    final SyntaxNode first = parts.get(0);
    final boolean callee = parts.size() > AFTER_FIRST && PAREN.equals(
        CSharpSyntax.text(parts.get(1))
    );
    final Chain inner =
        PRIMARY.equals(first.kind()) ? chain(first, callee, false, found) : primary(first, found);
    if (parts.size() > AFTER_FIRST) {
      follow(inner, parts, called, target, found);
    }
    return inner;
  }

  private Chain primary(final SyntaxNode first, final List<FlowSite> found) {
    if (SIMPLE_NAME.equals(first.kind())) {
      return Chain.named(first, CSharpFlowSyntax.name(first));
    }
    if (CREATION.equals(first.kind())) {
      creation(first, found);
      return Chain.opaque(first, "()");
    }
    walk(first, found);
    return Chain.named(first, CSharpSyntax.text(first));
  }

  private void follow(
      final Chain inner,
      final List<SyntaxNode> parts,
      final boolean called,
      final boolean target,
      final List<FlowSite> found
  ) {
    final SyntaxNode next = parts.get(1);
    final String token = CSharpSyntax.text(next);
    if (PAREN.equals(token)) {
      call(inner, parts, found);
    } else if (BRACKET.equals(token)) {
      parts.stream().skip(2).forEach(part -> walk(part, found));
      inner.opaque(parts.get(parts.size() - 1).span(), "[]");
    } else if (".".equals(token) || "?".equals(token)) {
      member(inner, parts, called, target, found);
    } else {
      inner.extend(next.span());
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
    final SyntaxNode name =
        parts.stream()
            .filter(part -> "identifier".equals(part.kind()))
            .findFirst()
            .orElse(parts.get(parts.size() - 1));
    chain.member(CSharpSyntax.text(name), name.span());
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

  private void call(final Chain chain, final List<SyntaxNode> parts, final List<FlowSite> found) {
    final List<SyntaxNode> arguments = CSharpFlowSyntax.argumentsOf(parts);
    arguments.forEach(argument -> walk(argument, found));
    if (!chain.name.isEmpty()) {
      found.add(
          site(
              chain.name,
              chain.qualifier,
              arguments,
              CSharpFlowSyntax.between(chain.start, parts.get(parts.size() - 1).span())
          )
      );
    }
    chain.opaque(parts.get(parts.size() - 1).span(), "()");
  }

  private void creation(final SyntaxNode node, final List<FlowSite> found) {
    final List<SyntaxNode> arguments = CSharpFlowSyntax.argumentsOf(node.children());
    node.children().forEach(child -> walk(child, found));
    final String type =
        CSharpSyntax.child(node, "type_")
            .flatMap(written -> CSharpLocalTypes.simple(CSharpSyntax.text(written)))
            .orElse("");
    if (!type.isEmpty()) {
      found.add(site(type, "", arguments, node.span()));
    }
  }

  private FlowSite site(
      final String name,
      final String qualifier,
      final List<SyntaxNode> arguments,
      final Span span
  ) {
    return new FlowSite(
        FlowSiteKind.CALL,
        name,
        qualifier,
        receivers.of(qualifier),
        List.of(),
        arguments.stream().map(SyntaxNode::span).toList(),
        span
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

    static Chain named(final SyntaxNode node, final String text) {
      final boolean plain = PLAIN.matcher(text).matches() && !THIS.equals(text);
      return new Chain(node.span(), text, plain ? text : "");
    }

    static Chain opaque(final SyntaxNode node, final String text) {
      return new Chain(node.span(), text, "");
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
      end = CSharpFlowSyntax.between(start, span);
    }

    Span span() {
      return CSharpFlowSyntax.between(start, end);
    }
  }
}
