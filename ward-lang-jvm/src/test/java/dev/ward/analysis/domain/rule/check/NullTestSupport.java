package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeRef;
import java.util.List;
import java.util.Map;

/** Runs the null data-flow check over a class body, for the tests of its rules. */
final class NullTestSupport {

  private NullTestSupport() {}

  static List<String> run(final NullDataflowCheck.Mode mode, final String body) {
    return new ParsedFacts("class C {\n" + body + "}\n")
        .run(new NullDataflowCheck(mode, "message"));
  }

  static List<String> run(
      final NullDataflowCheck.Mode mode,
      final String body,
      final String call,
      final SpanFacts facts) {
    return new ParsedFacts("class C {\n" + body + "}\n")
        .at("expression", call, facts)
        .run(new NullDataflowCheck(mode, "message"));
  }

  static SpanFacts target(
      final String owner, final String name, final int params, final String... annotated) {
    final List<TypeRef> types = java.util.Collections.nCopies(params, ParsedFacts.OBJECT);
    return new SpanFacts(
        null,
        null,
        new CallTarget(
            TypeRef.of(owner),
            name,
            types,
            null,
            false,
            false,
            List.of(),
            java.util.Collections.nCopies(params, List.of(annotated)),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            Map.of(),
            Map.of()),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null,
        null);
  }
}
