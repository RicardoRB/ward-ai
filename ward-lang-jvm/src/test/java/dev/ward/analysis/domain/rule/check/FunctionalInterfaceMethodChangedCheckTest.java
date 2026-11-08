package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.TypeRef;
import java.util.List;
import org.junit.jupiter.api.Test;

class FunctionalInterfaceMethodChangedCheckTest {

  private static final FunctionalInterfaceMethodChangedCheck CHECK =
      new FunctionalInterfaceMethodChangedCheck("message");
  private static final String SOURCE =
      "@FunctionalInterface\ninterface Task extends Handler {\n  void run2();\n"
          + "  default void handle() {\n    log();\n  }\n}\n";
  private static final String METHOD = "defaultvoidhandle(){log();}";

  private static CallTarget target(
      final String owner, final String name, final Boolean isAbstract) {
    return new CallTarget(
        TypeRef.of(owner),
        name,
        List.of(),
        null,
        false,
        false,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null,
        isAbstract);
  }

  private static SpanFacts overrides(final CallTarget... targets) {
    return new SpanFacts(
        null, null, null, List.of(), List.of(), List.of(targets), List.of(), null, null);
  }

  private static SpanFacts handler(final String annotation) {
    return new SpanFacts(
        TypeRef.of("Handler"),
        null,
        null,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null,
        new TypeMembers(List.of(), List.of(), List.of(), List.of(annotation)));
  }

  private static SpanFacts call(final String owner, final String name) {
    return new SpanFacts(
        null,
        null,
        target(owner, name, null),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null,
        null);
  }

  private static List<String> run(
      final String source,
      final String method,
      final SpanFacts overridden,
      final SpanFacts supertype) {
    return new ParsedFacts(source)
        .at("interfaceMethodDeclaration", method, overridden)
        .at("typeType", "Handler", supertype)
        .run(CHECK);
  }

  @Test
  void aDefaultMethodThatDoesMoreThanCallTheAbstractMethodIsReported() {
    final SpanFacts overridden = overrides(target("Handler", "handle", true));
    assertThat(run(SOURCE, METHOD, overridden, handler("java.lang.FunctionalInterface")))
        .containsExactly("4:3");
  }

  @Test
  void aDefaultMethodThatOnlyCallsTheAbstractMethodIsNotReported() {
    final SpanFacts overridden = overrides(target("Handler", "handle", true));
    final String source = SOURCE.replace("log()", "run2()");
    final ParsedFacts parsed =
        new ParsedFacts(source)
            .at("interfaceMethodDeclaration", "defaultvoidhandle(){run2();}", overridden)
            .at("typeType", "Handler", handler("java.lang.FunctionalInterface"))
            .at("expression", "run2()", call("Task", "run2"));
    assertThat(parsed.run(CHECK)).isEmpty();
    final String boxed =
        source
            .replace("default void", "default Void")
            .replace("run2();\n", "run2();\n    return null;\n");
    assertThat(
            new ParsedFacts(boxed)
                .at(
                    "interfaceMethodDeclaration",
                    "defaultVoidhandle(){run2();returnnull;}",
                    overridden)
                .at("typeType", "Handler", handler("java.lang.FunctionalInterface"))
                .at("expression", "run2()", call("Task", "run2"))
                .run(CHECK))
        .isEmpty();
    assertThat(
            new ParsedFacts(boxed.replace("    return null;\n", ""))
                .at("interfaceMethodDeclaration", "defaultVoidhandle(){run2();}", overridden)
                .at("typeType", "Handler", handler("java.lang.FunctionalInterface"))
                .at("expression", "run2()", call("Task", "run2"))
                .run(CHECK))
        .hasSize(1);
  }

  @Test
  void unknownFactsAndOtherShapesAreNotReported() {
    final SpanFacts functional = handler("java.lang.FunctionalInterface");
    assertThat(run(SOURCE, METHOD, overrides(target("Handler", "handle", false)), functional))
        .isEmpty();
    assertThat(run(SOURCE, METHOD, overrides(target("Handler", "handle", null)), functional))
        .isEmpty();
    assertThat(run(SOURCE, METHOD, overrides(target("Handler", "toString", true)), functional))
        .isEmpty();
    assertThat(run(SOURCE, METHOD, overrides(target("Other", "handle", true)), functional))
        .isEmpty();
    assertThat(
            run(SOURCE, METHOD, overrides(target("Handler", "handle", true)), handler("a.Other")))
        .isEmpty();
    assertThat(run(SOURCE, METHOD, SpanFacts.UNKNOWN, functional)).isEmpty();
    assertThat(
            run(
                SOURCE.replace("@FunctionalInterface\n", ""),
                METHOD,
                overrides(target("Handler", "handle", true)),
                functional))
        .isEmpty();
    assertThat(
            run(
                SOURCE.replace("  void run2();\n", "  void run2();\n  void run3();\n"),
                METHOD,
                overrides(target("Handler", "handle", true)),
                functional))
        .isEmpty();
    assertThat(new ParsedFacts(SOURCE).plain(CHECK)).isEmpty();
  }
}
