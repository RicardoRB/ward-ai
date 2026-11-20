package dev.ward.analysis.domain.csharp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.api.ApiMember;
import dev.ward.analysis.domain.api.ApiSurfaceException;
import dev.ward.analysis.domain.api.ApiType;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import org.junit.jupiter.api.Test;

class CSharpApiSurfaceReaderSignaturesTest {
  private static List<ApiType> read(final String source) {
    return new CSharpApiSurfaceReader()
        .read(
            List.of(new SourceUnit("a.cs", AnalysisLanguage.CSHARP, source)),
            new AntlrLanguageFrontend(),
            name -> false
        );
  }

  private static ApiType type(final String source, final String name) {
    return read(source).stream().filter(t -> name.equals(t.name())).findFirst().orElseThrow();
  }

  private static List<String> members(final ApiType type) {
    return type.members().stream().map(ApiMember::signature).toList();
  }

  @Test
  void protectedMembersOfASealedClassAreNotApi() {
    final ApiType c = type("public sealed class C { protected void P() {} }", "C");

    assertThat(c.members()).filteredOn(m -> "P".equals(m.name())).noneMatch(c::exposes);
  }

  @Test
  void typeVariablesAreComparedByPosition() {
    final ApiMember f =
        type("public class C { public T F<T>(T x) => x; }", "C").members().stream()
            .filter(m -> "F".equals(m.name()))
            .findFirst()
            .orElseThrow();
    final ApiMember g =
        type("public class C { public U F<U>(U x) => x; }", "C").members().stream()
            .filter(m -> "F".equals(m.name()))
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
