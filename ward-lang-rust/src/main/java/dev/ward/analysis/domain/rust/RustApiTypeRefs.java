package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.api.ApiTypeRef;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns the type nodes of one Rust file into API type references. */
final class RustApiTypeRefs {
  private static final Pattern SPACES = Pattern.compile("\\s+");
  private static final Pattern AROUND_SYMBOL = Pattern.compile("(?<=\\W)\\s+|\\s+(?=\\W)");
  private static final String VARIABLE = "$";

  private final String source;
  private final List<String> variables;

  RustApiTypeRefs(final String source, final List<String> variables) {
    this.source = source;
    this.variables = List.copyOf(variables);
  }

  /** Returns references that also treat {@code more} as type variables. */
  RustApiTypeRefs with(final List<String> more) {
    final List<String> all = new ArrayList<>(variables);
    all.addAll(more);
    return new RustApiTypeRefs(source, all);
  }

  /** Returns the source text of {@code node} with each run of white space as one space. */
  String text(final SyntaxNode node) {
    final Span span = node.span();
    return SPACES.matcher(source.substring(span.startOffset(), span.endOffset())).replaceAll(" ")
        .trim();
  }

  /** Returns the reference for the type written in {@code node}. */
  ApiTypeRef of(final SyntaxNode node) {
    return ref(text(node));
  }

  /** Returns the reference for a type written as {@code text}. */
  ApiTypeRef ref(final String text) {
    String key = AROUND_SYMBOL.matcher(text).replaceAll("");
    for (int index = 0; index < variables.size(); index++) {
      key = key.replaceAll(
          "\\b" + Pattern.quote(variables.get(index)) + "\\b",
          Matcher.quoteReplacement(VARIABLE + index)
      );
    }
    final int generics = text.indexOf('<');
    return new ApiTypeRef(text, key, generics < 0 ? text : text.substring(0, generics), false);
  }
}
