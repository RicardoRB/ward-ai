package dev.ward.analysis.domain.csharp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.api.ApiMember;
import dev.ward.analysis.domain.api.ApiSurfaceException;
import dev.ward.analysis.domain.api.ApiType;
import dev.ward.analysis.domain.api.ApiVisibility;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import org.junit.jupiter.api.Test;

class CSharpApiSurfaceReaderTest {
  private static List<ApiType> read(final String source) {
    return new CSharpApiSurfaceReader()
        .read(
            List.of(new SourceUnit("a.cs", AnalysisLanguage.CSHARP, source)),
            new AntlrLanguageFrontend(),
            name -> false
        );
  }

  private static ApiType type(final String source, final String name) {
    return read(source).stream().filter(t -> t.name().equals(name)).findFirst().orElseThrow();
  }

  private static List<String> members(final ApiType type) {
    return type.members().stream().map(ApiMember::signature).toList();
  }

  @Test
  void publicMethodsAreApiAndPrivateOnesAreNot() {
    final ApiType client =
        type("namespace N { public class C { public int F(string s) => 1; void G() {} } }", "N.C");

    assertThat(client.visibility()).isEqualTo(ApiVisibility.PUBLIC);
    assertThat(client.members())
        .filteredOn(m -> m.name().equals("F"))
        .singleElement()
        .extracting(ApiMember::visibility)
        .isEqualTo(ApiVisibility.PUBLIC);
    assertThat(client.members()).filteredOn(m -> m.name().equals("G")).allMatch(
        m -> !client.exposes(m)
    );
  }

  @Test
  void aTopLevelTypeWithoutModifierIsInternal() {
    assertThat(type("class C {}", "C").visibility()).isEqualTo(ApiVisibility.PACKAGE);
  }

  @Test
  void nestedAndGenericTypesAreNamedLikeMetadata() {
    final List<ApiType> found =
        read("namespace N; public class Outer { public class Inner {} } public class G<T> {}");

    assertThat(found).extracting(ApiType::name).contains("N.Outer", "N.Outer.Inner", "N.G`1");
  }

  @Test
  void propertiesAreAccessorMethods() {
    final ApiType c =
        type("public class C { public int A { get; set; } public int B { get; } }", "C");

    assertThat(c.members()).extracting(ApiMember::name).contains("get_A", "set_A", "get_B");
    assertThat(c.members()).extracting(ApiMember::name).doesNotContain("set_B");
  }

  @Test
  void aClassWithoutConstructorHasTheDefaultOne() {
    final ApiType c = type("public class C {}", "C");

    assertThat(c.members())
        .singleElement()
        .satisfies(m -> assertThat(m.kind()).isEqualTo(ApiMember.Kind.CONSTRUCTOR));
    assertThat(c.extendable()).isTrue();
  }

  @Test
  void protectedMembersOfASealedClassAreNotApi() {
    final ApiType c = type("public sealed class C { protected void P() {} }", "C");

    assertThat(c.members()).filteredOn(m -> m.name().equals("P")).noneMatch(c::exposes);
  }

  @Test
  void typeVariablesAreComparedByPosition() {
    final ApiMember f =
        type("public class C { public T F<T>(T x) => x; }", "C").members().stream()
            .filter(m -> m.name().equals("F"))
            .findFirst()
            .orElseThrow();
    final ApiMember g =
        type("public class C { public U F<U>(U x) => x; }", "C").members().stream()
            .filter(m -> m.name().equals("F"))
            .findFirst()
            .orElseThrow();

    assertThat(f.parameters().getFirst().type().key())
        .isEqualTo(g.parameters().getFirst().type().key());
  }

  @Test
  void interfaceMembersArePublicAndAbstract() {
    final ApiType i = type("public interface I { void M(); }", "I");

    assertThat(members(i)).singleElement().asString().contains("abstract").contains("M");
  }

  @Test
  void aSyntaxErrorIsReported() {
    assertThatThrownBy(() -> read("public class C { void ( }"))
        .isInstanceOf(ApiSurfaceException.class);
  }
}
