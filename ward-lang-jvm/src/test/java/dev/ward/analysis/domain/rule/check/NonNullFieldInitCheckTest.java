package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class NonNullFieldInitCheckTest {

  private static final NonNullFieldInitCheck CHECK = new NonNullFieldInitCheck("message");

  private static List<String> run(final String body) {
    return new ParsedFacts("class C {\n" + body + "}\n")
        .plain(CHECK).stream()
            .map(match -> match.span().startLine() + ":" + match.span().startColumn())
            .toList();
  }

  @Test
  void aConstructorThatLeavesAFieldThatMustNotBeNullUnsetIsReported() {
    assertThat(run("  @Nonnull String name;\n  C() {\n  }\n")).containsExactly("3:3");
    assertThat(run("  @NonNull String name;\n  C() {\n  }\n  C(int x) {\n    name = \"x\";\n  }\n"))
        .containsExactly("3:3");
    assertThat(run("  @Nonnull String name;\n")).containsExactly("1:1");
  }

  @Test
  void aFieldTheClassSetsAnotherWayOrNeverNeedsIsNotReported() {
    assertThat(run("  @Nonnull String name;\n  C() {\n    this.name = \"x\";\n  }\n")).isEmpty();
    assertThat(run("  @Nonnull String name;\n  C() {\n    name = \"x\";\n  }\n")).isEmpty();
    assertThat(run("  @Nonnull String name = \"x\";\n  C() {\n  }\n")).isEmpty();
    assertThat(run("  @Nonnull String name;\n  {\n    name = \"x\";\n  }\n  C() {\n  }\n"))
        .isEmpty();
    assertThat(run("  @Nonnull final String name;\n  C() {\n    this.name = \"x\";\n  }\n"))
        .isEmpty();
    assertThat(run("  @Nonnull static String name;\n  C() {\n  }\n")).isEmpty();
    assertThat(run("  @Nonnull int size;\n  C() {\n  }\n")).isEmpty();
    assertThat(run("  String name;\n  C() {\n  }\n")).isEmpty();
    assertThat(
            run(
                "  @Nonnull String name;\n  C() {\n    this(1);\n  }\n  C(int x) {\n    name = \"x\";\n  }\n"))
        .isEmpty();
    assertThat(run("  @Nonnull String name;\n  C(String name) {\n    name = name;\n  }\n"))
        .containsExactly("3:3");
  }
}
