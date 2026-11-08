package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeRef;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LibraryCallCheckTest {

  private static final LibraryCallCheck DOH =
      new LibraryCallCheck(LibraryCallCheck.Mode.NULL_CHECK_OF_CONSTANT, "message");
  private static final LibraryCallCheck MOCKS =
      new LibraryCallCheck(LibraryCallCheck.Mode.EMPTY_MOCKS, "message");
  private static final TypeRef OBJECT = TypeRef.of("java.lang.Object");
  private static final String PRECONDITIONS = "com.google.common.base.Preconditions";

  private static SpanFacts call(
      final String owner, final String name, final List<TypeRef> parameters) {
    return new SpanFacts(
        null,
        null,
        new CallTarget(
            TypeRef.of(owner),
            name,
            parameters,
            null,
            true,
            false,
            List.of(),
            List.of(),
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

  private static List<String> doh(final String call, final SpanFacts facts) {
    return new ParsedFacts("class C {\n  void t(Object o) {\n    " + call + ";\n  }\n}\n")
        .at("expression", call.replace(" ", ""), facts)
        .run(DOH);
  }

  @Test
  void aStringConstantWhereTheCheckedValueBelongsIsReported() {
    final SpanFacts check = call(PRECONDITIONS, "checkNotNull", List.of(OBJECT));
    final SpanFacts check2 = call(PRECONDITIONS, "checkNotNull", List.of(OBJECT, OBJECT));
    final SpanFacts assertion = call("org.junit.Assert", "assertNotNull", List.of(OBJECT));
    final SpanFacts assertion2 = call("org.junit.Assert", "assertNotNull", List.of(OBJECT, OBJECT));
    assertThat(DOH.needs().facts()).containsExactly(FactKind.CALL);
    assertThat(doh("Preconditions.checkNotNull(\"o\")", check)).containsExactly("3:5");
    assertThat(doh("Preconditions.checkNotNull(\"o\", \"m\")", check2)).hasSize(1);
    assertThat(
            doh(
                "Strings.nullToEmpty(\"s\")",
                call("com.google.common.base.Strings", "nullToEmpty", List.of(OBJECT))))
        .hasSize(1);
    assertThat(doh("Assert.assertNotNull(\"o\")", assertion)).hasSize(1);
    assertThat(doh("Assert.assertNotNull(\"m\", \"o\")", assertion2)).hasSize(1);
  }

  @Test
  void aValueAnotherOrderAnotherMethodOrUnknownFactsAreNotReported() {
    final SpanFacts check2 = call(PRECONDITIONS, "checkNotNull", List.of(OBJECT, OBJECT));
    assertThat(doh("Preconditions.checkNotNull(o, \"m\")", check2)).isEmpty();
    assertThat(doh("Preconditions.checkNotNull(\"m\", o)", check2)).isEmpty();
    assertThat(doh("Preconditions.checkNotNull(\"o\" + o)", check2)).isEmpty();
    assertThat(
            doh(
                "Assert.assertNotNull(\"m\", o)",
                call("org.junit.Assert", "assertNotNull", List.of())))
        .isEmpty();
    assertThat(doh("Assert.assertNull(\"o\")", call("org.junit.Assert", "assertNull", List.of())))
        .isEmpty();
    assertThat(
            doh(
                "Strings.repeat(\"s\")",
                call("com.google.common.base.Strings", "repeat", List.of())))
        .isEmpty();
    assertThat(doh("Other.checkNotNull(\"o\")", call("a.Other", "checkNotNull", List.of())))
        .isEmpty();
    assertThat(doh("Preconditions.checkNotNull()", check2)).isEmpty();
    assertThat(doh("Preconditions.checkNotNull(\"o\")", SpanFacts.UNKNOWN)).isEmpty();
    assertThat(new ParsedFacts("class C {}").plain(DOH)).isEmpty();
  }

  private static List<String> mocks(final String call, final SpanFacts facts) {
    return new ParsedFacts("class C {\n  void t(Object m) {\n    " + call + ";\n  }\n}\n")
        .at("expression", call, facts)
        .run(MOCKS);
  }

  @Test
  void anEasyMockCallWithoutMocksIsReported() {
    final TypeRef array = new TypeRef("java.lang.Object", List.of(), 1, false);
    assertThat(mocks("EasyMock.replay()", call("org.easymock.EasyMock", "replay", List.of(array))))
        .containsExactly("3:5");
    assertThat(
            mocks(
                "EasyMock.resetToStrict()",
                call("org.easymock.EasyMock", "resetToStrict", List.of(array))))
        .hasSize(1);
    assertThat(mocks("EasyMock.replay(m)", call("org.easymock.EasyMock", "replay", List.of(array))))
        .isEmpty();
    assertThat(mocks("EasyMock.create()", call("org.easymock.EasyMock", "create", List.of(array))))
        .isEmpty();
    assertThat(mocks("EasyMock.replay()", call("org.easymock.EasyMock", "replay", List.of(OBJECT))))
        .isEmpty();
    assertThat(mocks("Mocks.replay()", call("a.Mocks", "replay", List.of(array)))).isEmpty();
  }
}
