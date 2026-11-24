package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.Span;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The sites of a call written on a receiver that a taint specification reads as a call with the
 * receiver as its first argument: a call of an extension function of the file, {@code value.f(x)}
 * for {@code fun T.f(y)}, and a conversion of a string to a number, {@code value.toInt()}, which
 * the specifications list as a sanitizer. The sanitizer must enclose the receiver it cleans, so the
 * conversion is also written as {@code toInt(value)} over the whole chain.
 */
final class KotlinReceiverCalls {
  private static final Set<String> CONVERSIONS =
      Set.of(
          "toInt",
          "toIntOrNull",
          "toLong",
          "toLongOrNull",
          "toShort",
          "toShortOrNull",
          "toByte",
          "toByteOrNull",
          "toDouble",
          "toDoubleOrNull",
          "toFloat",
          "toFloatOrNull",
          "toBoolean",
          "toBooleanStrict",
          "toBooleanStrictOrNull",
          "toBigDecimal",
          "toBigDecimalOrNull",
          "toBigInteger",
          "toBigIntegerOrNull",
          "toUInt",
          "toUIntOrNull",
          "toULong",
          "toULongOrNull"
      );

  private final Set<String> extensions;

  /**
   * Where the chain of a call starts and the span of its receiver, when it has one.
   *
   * @param start the start of the called name, where the call is reported
   * @param receiver the receiver, from the start of the whole chain
   */
  record Chain(Span start, Span receiver) {}

  /**
   * Creates the reader of one file.
   *
   * @param extensions the extension functions of the file, written {@code name/arity} with the
   *     receiver counted
   */
  KotlinReceiverCalls(final Set<String> extensions) {
    this.extensions = extensions;
  }

  /**
   * Adds the sites that a call on a receiver stands for.
   *
   * @param name the called name
   * @param chain the start of the call and its receiver
   * @param arguments the spans of the arguments
   * @param operation the span of the call's parentheses
   * @param found where the sites go
   * @return true when the call is one of an extension function, whose own site is replaced
   */
  boolean add(
      final String name,
      final Chain chain,
      final List<Span> arguments,
      final Span operation,
      final List<FlowSite> found
  ) {
    final Span receiver = chain.receiver();
    if (CONVERSIONS.contains(name) && arguments.isEmpty()) {
      found.add(call(name, List.of(receiver), KotlinFlowSyntax.between(receiver, operation)));
    }
    if (!extensions.contains(name + "/" + (arguments.size() + 1))) {
      return false;
    }
    final List<Span> all = new ArrayList<>();
    all.add(receiver);
    all.addAll(arguments);
    found.add(call(name, all, KotlinFlowSyntax.between(chain.start(), operation)));
    return true;
  }

  private static FlowSite call(final String name, final List<Span> arguments, final Span span) {
    return new FlowSite(FlowSiteKind.CALL, name, "", Optional.empty(), List.of(), arguments, span);
  }
}
