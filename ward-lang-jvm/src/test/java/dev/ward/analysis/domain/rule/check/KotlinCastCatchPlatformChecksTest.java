package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.SpanFacts;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests the unsafe cast, unreachable catch and platform type checks with hand-made facts. */
final class KotlinCastCatchPlatformChecksTest {
  private static final String MESSAGE = "message";
  private static final int UNREACHABLE_LINE = 4;
  private static final String OBJECT = "java.lang.Object";
  private static final String STRING = "java.lang.String";
  private static final String CLASS_KIND = "class";
  private static final String INTERFACE_KIND = "interface";
  private static final String NULLABLE = "org.jspecify.annotations.Nullable";

  @Test
  void unsafeCastReportsOnlyUnrelatedKnownClassesWhereOneIsFinal() {
    final String source = "fun f(s: String) {\n    val n = s as Target\n}\n";
    final SyntaxNode root = KotlinTypedCheckFixtures.parse(source);
    final SyntaxNode cast = KotlinTypedCheckFixtures.nodes(root, "typeRHS").get(0);
    final KotlinUnsafeCastCheck check = new KotlinUnsafeCastCheck(MESSAGE);
    final List<String> ancestors = List.of(OBJECT);
    final SpanFacts string = KotlinCheckFactBuilders.typed(STRING, CLASS_KIND, true, ancestors);
    assertThat(
            check.matches(
                root,
                null,
                null,
                KotlinCheckFactBuilders.casted(
                    cast,
                    string,
                    KotlinCheckFactBuilders.typed(
                        "java.lang.Integer",
                        CLASS_KIND,
                        true,
                        List.of("java.lang.Number", OBJECT))),
                KotlinTypedCheckFixtures.unit(source)))
        .hasSize(1);
    assertThat(
            check.matches(
                root,
                null,
                null,
                KotlinCheckFactBuilders.casted(
                    cast,
                    string,
                    KotlinCheckFactBuilders.typed(
                        "java.lang.CharSequence", INTERFACE_KIND, true, List.of())),
                KotlinTypedCheckFixtures.unit(source)))
        .isEmpty();
    assertThat(
            check.matches(
                root,
                null,
                null,
                KotlinCheckFactBuilders.casted(
                    cast,
                    string,
                    KotlinCheckFactBuilders.typed(OBJECT, CLASS_KIND, false, List.of())),
                KotlinTypedCheckFixtures.unit(source)))
        .isEmpty();
    assertThat(
            check.matches(
                root,
                null,
                null,
                KotlinCheckFactBuilders.casted(
                    cast,
                    KotlinCheckFactBuilders.typed("app.Base", CLASS_KIND, false, ancestors),
                    KotlinCheckFactBuilders.typed("app.Other", CLASS_KIND, false, ancestors)),
                KotlinTypedCheckFixtures.unit(source)))
        .isEmpty();
  }

  @Test
  void unreachableCatchReportsASubtypeOfAnEarlierCatchOnly() {
    final String source =
        "fun f() {\n    try {\n    } catch (e: Exception) {\n    } catch (e: IOException) {\n    }\n}\n";
    final SyntaxNode root = KotlinTypedCheckFixtures.parse(source);
    final KotlinUnreachableCatchCheck check = new KotlinUnreachableCatchCheck(MESSAGE);
    final List<String> ioAncestors = List.of("java.lang.Exception", "java.lang.Throwable", OBJECT);
    assertThat(
            check.matches(
                root,
                null,
                null,
                KotlinCheckFactBuilders.catches(
                    root,
                    "java.lang.Exception",
                    List.of("java.lang.Throwable", OBJECT),
                    "java.io.IOException",
                    ioAncestors),
                KotlinTypedCheckFixtures.unit(source)))
        .extracting(match -> match.span().startLine())
        .containsExactly(UNREACHABLE_LINE);
    assertThat(
            check.matches(
                root,
                null,
                null,
                KotlinCheckFactBuilders.catches(
                    root,
                    "java.lang.Exception",
                    List.of("java.lang.Throwable", OBJECT),
                    "java.io.IOException",
                    null),
                KotlinTypedCheckFixtures.unit(source)))
        .isEmpty();
  }

  @Test
  void platformTypeReportsAnUnannotatedJavaResultOfAPublicDeclaration() {
    final String source = "class App {\n    protected val name = System.getProperty(\"x\")\n}\n";
    final SyntaxNode root = KotlinTypedCheckFixtures.parse(source);
    final KotlinPlatformTypeCheck check = new KotlinPlatformTypeCheck(MESSAGE);
    final CallTarget javaCall = KotlinCheckFactBuilders.javaCall("java.lang.System", List.of());
    assertThat(
            check.matches(
                root,
                null,
                null,
                KotlinCheckFactBuilders.calledAt(root, javaCall),
                KotlinTypedCheckFixtures.unit(source)))
        .hasSize(1);
    final String privateSource =
        "class App {\n    private val name = System.getProperty(\"x\")\n}\n";
    final SyntaxNode privateRoot = KotlinTypedCheckFixtures.parse(privateSource);
    assertThat(
            check.matches(
                privateRoot,
                null,
                null,
                KotlinCheckFactBuilders.calledAt(privateRoot, javaCall),
                KotlinTypedCheckFixtures.unit(privateSource)))
        .isEmpty();
    assertThat(
            check.matches(
                root,
                null,
                null,
                KotlinCheckFactBuilders.calledAt(
                    root, KotlinCheckFactBuilders.javaCall("java.lang.System", List.of(NULLABLE))),
                KotlinTypedCheckFixtures.unit(source)))
        .isEmpty();
    assertThat(
            check.matches(
                root,
                null,
                null,
                KotlinCheckFactBuilders.calledAt(
                    root, KotlinCheckFactBuilders.javaCall("app.Repo", List.of())),
                KotlinTypedCheckFixtures.unit(source)))
        .isEmpty();
  }
}
