package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * The sites that a call gives besides its own: what the call proves about its value (a line-break
 * strip, a number or boolean result, a JSON response body).
 */
final class JavaSyntheticSites {
  private JavaSyntheticSites() {}

  /**
   * The name of a call. {@code this(...)} and {@code super(...)} have no identifier, so their name
   * is the keyword that starts them.
   */
  static String calledName(final SyntaxNode call) {
    final SyntaxNode identifier = JavaSyntaxNames.identifierOf(call);
    return JavaFlowSyntax.text(identifier != null ? identifier : JavaFlowSyntax.parts(call).get(0));
  }

  /** Returns the extra sites of one call. */
  static List<FlowSite> of(
      final SyntaxNode call,
      final SyntaxNode receiver,
      final String qualifier,
      final boolean jsonResponse,
      final Function<String,
      Optional<String>> receiverType
  ) {
    final List<FlowSite> found = new ArrayList<>();
    final String name = calledName(call);
    final List<SyntaxNode> arguments = JavaCallArguments.of(call);
    if (receiver != null
        && JavaLineBreakStrips.removesAll(
            name,
            arguments.stream().map(JavaFlowSyntax::text).toList(),
            qualifier,
            receiverType::apply
        )) {
      found.add(synthetic(JavaLineBreakStrips.NAME, qualifier, List.of(receiver.span()), call));
    }
    if (receiver != null
        && JavaPrimitiveCalls.returnsPrimitive(name, receiverType.apply(qualifier))) {
      final List<Span> covered = new ArrayList<>(JavaCallArguments.spans(arguments));
      covered.add(0, receiver.span());
      found.add(synthetic(JavaPrimitiveCalls.NAME, qualifier, covered, call));
    }
    found.addAll(trusted(name, receiver, qualifier, call, receiverType));
    if (receiver != null) {
      JavaQueryBuilds.format(name, qualifier, JavaCallArguments.of(call), call.span())
          .ifPresent(found::add);
    }
    if (jsonResponse
        && JavaJsonResponses.writesBody(name, qualifier, receiverType.apply(qualifier))) {
      found.add(
          synthetic(JavaJsonResponses.NAME, qualifier, JavaCallArguments.spans(arguments), call)
      );
    }
    return found;
  }

  /** The sites of a standard system property and of the request URI, each covering its call. */
  private static List<FlowSite> trusted(
      final String name,
      final SyntaxNode receiver,
      final String qualifier,
      final SyntaxNode call,
      final Function<String,
      Optional<String>> receiverType
  ) {
    final List<String> texts = JavaCallArguments.of(call).stream().map(
        JavaFlowSyntax::text
    ).toList();
    final List<FlowSite> found = new ArrayList<>();
    if (JavaTrustedCalls.isStandardProperty(name, receiverType.apply(qualifier), texts)) {
      found.add(synthetic(JavaTrustedCalls.PROPERTY, qualifier, List.of(call.span()), call));
    }
    if (JavaTrustedCalls.isRequestUri(name, receiver != null, texts)) {
      found.add(synthetic(JavaTrustedCalls.REQUEST_URI, qualifier, List.of(call.span()), call));
    }
    return found;
  }

  /** A member read, and the number site of the {@code length} of a declared array. */
  static List<FlowSite> memberRead(
      final SyntaxNode node,
      final SyntaxNode receiver,
      final SyntaxNode member,
      final Optional<String> receiverType,
      final String declared
  ) {
    final String qualifier = JavaFlowSyntax.text(receiver);
    final String name = JavaFlowSyntax.text(member);
    final List<FlowSite> found = new ArrayList<>();
    found.add(
        new FlowSite(
            FlowSiteKind.MEMBER_READ,
            name,
            qualifier,
            receiverType,
            List.of(),
            List.of(),
            node.span()
        )
    );
    if (JavaPrimitiveCalls.isArrayLength(name, declared)) {
      found.add(arrayLength(receiver, qualifier, node.span()));
    }
    return found;
  }

  /** The site that says an array's length is a number: its argument is the array. */
  static FlowSite arrayLength(final SyntaxNode receiver, final String qualifier, final Span span) {
    return new FlowSite(
        FlowSiteKind.CALL,
        JavaPrimitiveCalls.NAME,
        qualifier,
        Optional.empty(),
        List.of(),
        List.of(receiver.span()),
        span
    );
  }

  private static FlowSite synthetic(
      final String name,
      final String qualifier,
      final List<Span> arguments,
      final SyntaxNode call
  ) {
    return new FlowSite(
        FlowSiteKind.CALL,
        name,
        qualifier,
        Optional.empty(),
        List.of(),
        arguments,
        call.span()
    );
  }
}
