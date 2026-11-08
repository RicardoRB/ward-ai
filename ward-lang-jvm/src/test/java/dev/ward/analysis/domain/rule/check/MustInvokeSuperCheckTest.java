package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.SpanFacts;
import java.util.List;
import org.junit.jupiter.api.Test;

class MustInvokeSuperCheckTest {

  private static final MustInvokeSuperCheck CHECK = new MustInvokeSuperCheck("message");
  private static final String SILENT = "class C extends B {\n  void close() {\n  }\n}\n";
  private static final String MARKER = "javax.annotation.OverridingMethodsMustInvokeSuper";

  private static List<String> run(final String source, final SpanFacts facts) {
    return new ParsedFacts(source).at("methodDeclaration", "voidclose(){}", facts).run(CHECK);
  }

  private static SpanFacts overriding(final String... annotations) {
    return ExtraFacts.overriding(ParsedFacts.method("app.B", "close", List.of(), annotations));
  }

  @Test
  void anOverrideOfAMarkedMethodThatDoesNotCallSuperIsReported() {
    assertThat(CHECK.needs().facts()).containsExactly(FactKind.OVERRIDES);
    assertThat(run(SILENT, overriding(MARKER))).containsExactly("2:3");
    assertThat(run(SILENT, overriding("edu.umd.cs.findbugs.annotations.OverrideMustInvoke")))
        .hasSize(1);
  }

  @Test
  void anOverrideThatCallsSuperAnUnmarkedOneOrAnUnknownOneIsNotReported() {
    final String calling = "class C extends B {\n  void close() {\n    super.close();\n  }\n}\n";

    assertThat(
            new ParsedFacts(calling)
                .at("methodDeclaration", "voidclose(){super.close();}", overriding(MARKER))
                .run(CHECK))
        .isEmpty();
    assertThat(run(SILENT, overriding("javax.annotation.Nullable"))).isEmpty();
    assertThat(run(SILENT, overriding())).isEmpty();
    assertThat(new ParsedFacts(SILENT).run(CHECK)).isEmpty();
    assertThat(new ParsedFacts(SILENT).plain(CHECK)).isEmpty();
  }
}
