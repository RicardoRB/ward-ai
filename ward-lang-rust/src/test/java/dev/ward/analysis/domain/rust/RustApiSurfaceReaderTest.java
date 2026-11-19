package dev.ward.analysis.domain.rust;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.api.ApiMember;
import dev.ward.analysis.domain.api.ApiSurfaceException;
import dev.ward.analysis.domain.api.ApiType;
import dev.ward.analysis.domain.api.ApiTypeRef;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RustApiSurfaceReaderTest {
  private static final String LIB = "src/lib.rs";
  private static final String TOP = "crate.<top-level>";

  @Test
  @DisplayName("a public function is a member of the module's top-level type")
  void publicFunctionIsTopLevelMember() {
    final List<ApiType> types = RustApiFixtures.read(
        RustApiFixtures.unit(LIB, "pub fn open(a: u8) {}\nfn hidden() {}\npub(crate) fn inner() {}")
    );

    assertThat(RustApiFixtures.names(types)).containsExactly(TOP);
    assertThat(RustApiFixtures.ids(types.getFirst())).containsExactly("open(u8)");
  }

  @Test
  @DisplayName("only public fields and public methods of a public struct are API")
  void structFieldsAndMethods() {
    final List<ApiType> types =
        RustApiFixtures.read(
            RustApiFixtures.unit(
                LIB,
                "pub struct Door { pub width: u32, depth: u32 }\n"
                    + "impl Door { pub fn lock(&self) {} fn secret(&self) {} pub fn new() -> Door { Door { width: 0, depth: 0 } } }\n"
                    + "struct Hidden;"
            )
        );

    assertThat(RustApiFixtures.names(types)).containsExactly("crate.Door");
    assertThat(RustApiFixtures.ids(types.getFirst())).containsExactlyInAnyOrder(
        "width",
        "lock()",
        "new()"
    );
    assertThat(RustApiFixtures.type(types, "crate.Door").members())
        .filteredOn(member -> "new".equals(member.name()))
        .allMatch(ApiMember::isStatic);
  }

  @Test
  @DisplayName("a trait method without a body is abstract and one with a body is a default")
  void traitMethods() {
    final ApiType draw =
        RustApiFixtures.read(
            RustApiFixtures.unit(
                LIB,
                "pub trait Draw: Clone { fn draw(&self); fn blink(&self) {} }"
            )
        ).getFirst();

    assertThat(draw.kind()).isEqualTo(ApiType.Kind.INTERFACE);
    assertThat(draw.supertypes()).extracting(ApiTypeRef::text).containsExactly("Clone");
    assertThat(draw.members())
        .extracting(ApiMember::name, ApiMember::isAbstract, ApiMember::isDefault)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("draw", true, false),
            org.assertj.core.groups.Tuple.tuple("blink", false, true)
        );
  }

  @Test
  @DisplayName("enum variants and public tuple fields are members")
  void variantsAndTupleFields() {
    final List<ApiType> types =
        RustApiFixtures.read(
            RustApiFixtures.unit(
                LIB,
                "pub enum Shape { Square, Round(u32), Box { w: u8 } }\npub struct Pair(pub u8, u8, pub i64);"
            )
        );

    assertThat(RustApiFixtures.ids(RustApiFixtures.type(types, "crate.Shape"))).containsExactly(
        "Square",
        "Round",
        "Box"
    );
    assertThat(RustApiFixtures.ids(RustApiFixtures.type(types, "crate.Pair"))).containsExactly(
        "0",
        "2"
    );
    assertThat(RustApiFixtures.type(types, "crate.Shape").kind()).isEqualTo(ApiType.Kind.ENUM);
  }

  @Test
  @DisplayName("an item is API only when every module on its path is public")
  void moduleVisibility() {
    final List<ApiType> types =
        RustApiFixtures.read(
            RustApiFixtures.unit(
                LIB,
                "pub mod open;\nmod closed;\npub mod inline { pub fn f() {} mod deep { pub fn g() {} } }"
            ),
            RustApiFixtures.unit("src/open.rs", "pub struct A;"),
            RustApiFixtures.unit("src/closed/mod.rs", "pub struct B;")
        );

    assertThat(RustApiFixtures.names(types)).containsExactly(
        "crate.inline.<top-level>",
        "crate.open.A"
    );
  }

  @Test
  @DisplayName("a public use of a known item adds it under the imported name")
  void reexport() {
    final List<ApiType> types =
        RustApiFixtures.read(
            RustApiFixtures.unit(
                LIB,
                "mod shapes;\npub use shapes::Circle;\npub use self::shapes::{area as surface, Ring};\n"
            ),
            RustApiFixtures.unit(
                "src/shapes.rs",
                "pub struct Circle { pub r: u32 }\npub struct Ring;\npub fn area() {}"
            )
        );

    assertThat(RustApiFixtures.names(types)).containsExactly(TOP, "crate.Circle", "crate.Ring");
    assertThat(RustApiFixtures.ids(RustApiFixtures.type(types, TOP))).containsExactly("surface()");
  }

  @Test
  @DisplayName("a workspace member is named after its directory and a binary is not read")
  void workspaceMember() {
    final List<ApiType> types =
        RustApiFixtures.read(
            RustApiFixtures.unit("crates/core/src/lib.rs", "pub struct Engine;"),
            RustApiFixtures.unit("crates/core/src/main.rs", "pub struct Binary;"),
            RustApiFixtures.unit("crates/core/src/bin/tool.rs", "pub struct Tool;")
        );

    assertThat(RustApiFixtures.names(types)).containsExactly("core.Engine");
  }

  @Test
  @DisplayName("generic parameters are compared by position and their bounds are kept")
  void generics() {
    final List<ApiType> types =
        RustApiFixtures.read(
            RustApiFixtures.unit(
                LIB,
                "pub struct Holder<T: Clone>(pub T);\nimpl<U> Holder<U> { pub fn get<V: Copy>(&self, v: V, u: Vec<U>) -> V { v } }"
            )
        );

    final ApiType holder = types.getFirst();
    assertThat(holder.typeParameters()).hasSize(1);
    assertThat(holder.typeParameters().getFirst().bounds()).extracting(
        ApiTypeRef::text
    ).containsExactly("Clone");
    final ApiMember get = holder.members().stream().filter(
        m -> "get".equals(m.name())
    ).findFirst().orElseThrow();
    assertThat(get.parameters()).extracting(p -> p.type().key()).containsExactly("$1", "Vec<$0>");
    assertThat(get.type().key()).isEqualTo("$1");
  }

  @Test
  @DisplayName("async and unsafe change the signature")
  void qualifiers() {
    final ApiMember plain = RustApiFixtures.read(
        RustApiFixtures.unit(LIB, "pub fn f() {}")
    ).getFirst().members().getFirst();
    final ApiMember unsafeFn = RustApiFixtures.read(
        RustApiFixtures.unit(LIB, "pub unsafe fn f() {}")
    ).getFirst().members().getFirst();

    assertThat(unsafeFn.type().key()).isNotEqualTo(plain.type().key());
  }

  @Test
  @DisplayName("a syntax error is reported with its file")
  void syntaxError() {
    assertThatThrownBy(() -> RustApiFixtures.read(RustApiFixtures.unit(LIB, "pub fn (")))
        .isInstanceOfSatisfying(
            ApiSurfaceException.class,
            error -> assertThat(error.path()).isEqualTo(LIB)
        );
    assertThat(Arrays.asList(AnalysisLanguage.values())).contains(
        new RustApiSurfaceReader().language()
    );
  }
}
