package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.TypeRef;
import java.util.List;
import org.junit.jupiter.api.Test;

class InheritedSignatureCheckTest {

  private static final InheritedSignatureCheck WRONG_PACKAGE =
      new InheritedSignatureCheck(InheritedSignatureCheck.Mode.WRONG_PACKAGE, "message");
  private static final InheritedSignatureCheck CLONEABLE =
      new InheritedSignatureCheck(InheritedSignatureCheck.Mode.INDIRECT_CLONEABLE, "message");
  private static final String HANDLER = "class C extends B {\n  void handle(Event e) {\n  }\n}\n";
  private static final String SINGLETON =
      "class R extends B {\n  private static R instance = new R();\n  private R() {\n  }\n}\n";
  private static final TypeRef CLONEABLE_TYPE = TypeRef.of("java.lang.Cloneable");

  private static TypeMembers inheriting(final String parameter, final String method) {
    return ParsedFacts.withMethods(
        List.of(), List.of(ParsedFacts.method("app.B", method, List.of(TypeRef.of(parameter)))));
  }

  private static List<String> handler(final TypeMembers members, final String declared) {
    return new ParsedFacts(HANDLER)
        .at(
            "classDeclaration",
            "classCextendsB{voidhandle(Evente){}}",
            ParsedFacts.members(members))
        .at("typeType", "Event", ParsedFacts.type(TypeRef.of(declared)))
        .run(WRONG_PACKAGE);
  }

  private static List<String> singleton(final String source, final TypeRef... above) {
    final String text = source.replace("\n", "").replace(" ", "");
    return new ParsedFacts(source)
        .at(
            "classDeclaration",
            text.replace("classRextendsB", "classRextendsB"),
            ParsedFacts.members(new TypeMembers(List.of(), List.of(), List.of(above), List.of())))
        .run(CLONEABLE);
  }

  @Test
  void aMethodWithTheSimpleParameterNamesOfAnInheritedMethodFromOtherPackagesIsReported() {
    final TypeMembers inherited = inheriting("app.other.Event", "handle");

    assertThat(WRONG_PACKAGE.needs().facts())
        .containsExactlyInAnyOrder(FactKind.MEMBERS, FactKind.TYPE);
    assertThat(handler(inherited, "java.awt.Event")).containsExactly("2:3");
  }

  @Test
  void anExactOverrideADifferentNameOrUnknownFactsAreNotReported() {
    assertThat(handler(inheriting("app.other.Event", "handle"), "app.other.Event")).isEmpty();
    assertThat(handler(inheriting("app.other.Other", "handle"), "java.awt.Event")).isEmpty();
    assertThat(handler(inheriting("app.other.Event", "other"), "java.awt.Event")).isEmpty();
    assertThat(new ParsedFacts(HANDLER).run(WRONG_PACKAGE)).isEmpty();
    assertThat(
            new ParsedFacts(HANDLER)
                .at(
                    "classDeclaration",
                    "classCextendsB{voidhandle(Evente){}}",
                    ParsedFacts.members(inheriting("app.other.Event", "handle")))
                .run(WRONG_PACKAGE))
        .isEmpty();
    assertThat(new ParsedFacts(HANDLER).plain(WRONG_PACKAGE)).isEmpty();
  }

  @Test
  void aSingletonThatInheritsCloneableIsReported() {
    assertThat(CLONEABLE.needs().facts()).containsExactly(FactKind.MEMBERS);
    assertThat(singleton(SINGLETON, TypeRef.of("app.B"), CLONEABLE_TYPE)).containsExactly("1:1");
  }

  @Test
  void aClassThatImplementsCloneableItselfOrIsNotASingletonIsNotReported() {
    assertThat(singleton(SINGLETON, TypeRef.of("app.B"))).isEmpty();
    assertThat(
            singleton(
                SINGLETON.replace("extends B", "extends B implements Cloneable"), CLONEABLE_TYPE))
        .isEmpty();
    assertThat(
            singleton(
                "class R extends B {\n  public R() {\n  }\n}\n",
                TypeRef.of("app.B"),
                CLONEABLE_TYPE))
        .isEmpty();
    assertThat(new ParsedFacts(SINGLETON).run(CLONEABLE)).isEmpty();
    assertThat(new ParsedFacts(SINGLETON).plain(CLONEABLE)).isEmpty();
    assertThat(SpanFacts.UNKNOWN.members()).isNull();
  }
}
