package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.TypeRef;
import java.util.List;
import org.junit.jupiter.api.Test;

class UnhashableKeyCheckTest {

  private static final String SOURCE =
      "class C {\n  java.util.Set<Key> keys;\n  Object make() {\n    return new HashSet<Key>();\n  }\n}\n";
  private static final CallTarget EQUALS =
      ParsedFacts.method("app.Key", "equals", List.of(ParsedFacts.OBJECT));
  private static final CallTarget HASH_CODE = ParsedFacts.method("app.Key", "hashCode", List.of());
  private static final CallTarget OBJECT_HASH_CODE =
      ParsedFacts.method("java.lang.Object", "hashCode", List.of());
  private static final CallTarget BASE_HASH_CODE =
      ParsedFacts.method("app.Base", "hashCode", List.of());

  private static List<String> run(
      final UnhashableKeyCheck.Use use, final String source, final TypeMembers key) {
    return new ParsedFacts(source)
        .at("typeType", "Key", ParsedFacts.members(key))
        .run(new UnhashableKeyCheck(use, "message"));
  }

  private static TypeMembers key(final List<CallTarget> declared, final CallTarget... inherited) {
    return ParsedFacts.withMethods(declared, List.of(inherited));
  }

  @Test
  void aKeyWithEqualsAndNoHashCodeIsReportedWhereTheCollectionIsCreatedOrDeclared() {
    final TypeMembers unhashable = key(List.of(EQUALS), OBJECT_HASH_CODE);

    assertThat(run(UnhashableKeyCheck.Use.CREATION, SOURCE, unhashable)).containsExactly("4:24");
    assertThat(run(UnhashableKeyCheck.Use.SIGNATURE, SOURCE, unhashable)).containsExactly("2:17");
    assertThat(new UnhashableKeyCheck(UnhashableKeyCheck.Use.CREATION, "message").needs().facts())
        .containsExactly(FactKind.MEMBERS);
  }

  @Test
  void aKeyThatHashesItselfOrInheritsAHashCodeIsNotReported() {
    assertThat(run(UnhashableKeyCheck.Use.CREATION, SOURCE, key(List.of(EQUALS, HASH_CODE))))
        .isEmpty();
    assertThat(run(UnhashableKeyCheck.Use.CREATION, SOURCE, key(List.of(EQUALS), BASE_HASH_CODE)))
        .isEmpty();
    assertThat(run(UnhashableKeyCheck.Use.CREATION, SOURCE, key(List.of(), OBJECT_HASH_CODE)))
        .isEmpty();
    assertThat(
            run(
                UnhashableKeyCheck.Use.CREATION,
                SOURCE,
                new TypeMembers(
                    List.of(
                        ParsedFacts.method("app.Key", "equals", List.of(TypeRef.of("app.Key")))),
                    List.of(),
                    List.of(),
                    List.of())))
        .isEmpty();
  }

  @Test
  void unknownFactsOtherCollectionsAndDiamondsAreNotReported() {
    final TypeMembers unhashable = key(List.of(EQUALS), OBJECT_HASH_CODE);

    assertThat(
            new ParsedFacts(SOURCE)
                .run(new UnhashableKeyCheck(UnhashableKeyCheck.Use.CREATION, "m")))
        .isEmpty();
    assertThat(
            run(
                UnhashableKeyCheck.Use.CREATION,
                "class C {\n  Object o = new ArrayList<Key>();\n}\n",
                unhashable))
        .isEmpty();
    assertThat(
            run(
                UnhashableKeyCheck.Use.CREATION,
                "class C {\n  Object o = new HashSet<>();\n}\n",
                unhashable))
        .isEmpty();
    assertThat(
            run(
                UnhashableKeyCheck.Use.SIGNATURE,
                "class C {\n  java.util.List<Key> keys;\n}\n",
                unhashable))
        .isEmpty();
    assertThat(
            new ParsedFacts(SOURCE)
                .plain(new UnhashableKeyCheck(UnhashableKeyCheck.Use.CREATION, "message")))
        .isEmpty();
  }
}
