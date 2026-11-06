package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The import and module checks on small sources; their options and boundary cases. */
class ImportChecksTest {

  private static final String MESSAGE = "{value}";
  private static final String FILE = "M.java";
  private static final String PACKAGE_LINE = "package app;\n";

  /** A qualified type reference on {@code line}, as the grammar builds it. */
  private static SyntaxNode qualifiedType(final String pkg, final String name, final int line) {
    final Span span = new Span(line, 1, line, 1 + pkg.length() + name.length(), 0, 0);
    final SyntaxNode packageName =
        new SyntaxNode(
            "packageName",
            span,
            Map.of(),
            List.of(new SyntaxNode("identifier", span, Map.of("text", pkg), List.of())));
    final SyntaxNode type = new SyntaxNode("typeIdentifier", span, Map.of("text", name), List.of());
    return new SyntaxNode("classType", span, Map.of(), List.of(packageName, type));
  }

  /** The unit of {@code source} with the given type references added after its declarations. */
  private static SyntaxNode withTypes(final String source, final SyntaxNode... types) {
    final SyntaxNode unit = ImportTrees.unit(source);
    final List<SyntaxNode> children = new ArrayList<>(unit.children());
    children.addAll(List.of(types));
    return new SyntaxNode(unit.kind(), unit.span(), unit.fields(), children);
  }

  @Test
  void staticImportsAreReportedUnlessExcluded() {
    final SyntaxNode root =
        ImportTrees.unit(
            "import static java.lang.Math.max;\n"
                + "import static java.util.Collections.*;\n"
                + "import java.util.List;\n");
    assertThat(MatchPositions.at(new StaticImportCheck(Set.of(), MESSAGE).matches(root)))
        .containsExactly(MatchPositions.col1(1), MatchPositions.col1(2));
    assertThat(
            MatchPositions.at(
                new StaticImportCheck(Set.of("java.lang.Math.*"), MESSAGE).matches(root)))
        .containsExactly(MatchPositions.col1(2));
    assertThat(
            MatchPositions.at(
                new StaticImportCheck(Set.of("java.util.Collections.*"), MESSAGE).matches(root)))
        .containsExactly(MatchPositions.col1(1));
    assertThat(
            MatchPositions.at(
                new StaticImportCheck(Set.of("java.lang.Math.max"), MESSAGE).matches(root)))
        .containsExactly(MatchPositions.col1(2));
  }

  @Test
  void illegalImportsMatchPackagesClassesAndPatterns() {
    final SyntaxNode root = ImportTrees.unit("import sun.misc.Unsafe;\nimport java.util.List;\n");
    assertThat(
            MatchPositions.at(
                new IllegalImportCheck(List.of("sun"), List.of(), false, MESSAGE).matches(root)))
        .containsExactly(MatchPositions.col1(1));
    assertThat(
            MatchPositions.at(
                new IllegalImportCheck(
                        List.of("sun.misc"), List.of("java.util.List"), false, MESSAGE)
                    .matches(root)))
        .containsExactly(MatchPositions.col1(1), MatchPositions.col1(2));
    assertThat(
            MatchPositions.at(
                new IllegalImportCheck(List.of(), List.of("java.util.List"), false, MESSAGE)
                    .matches(root)))
        .containsExactly(MatchPositions.col1(2));
    assertThat(
            MatchPositions.at(
                new IllegalImportCheck(List.of("sun\\..*"), List.of(), true, MESSAGE)
                    .matches(root)))
        .containsExactly(MatchPositions.col1(1));
    assertThatThrownBy(() -> new IllegalImportCheck(List.of("("), List.of(), true, MESSAGE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void redundantImportsRepeatJavaLangOrTheirOwnPackage() {
    final SyntaxNode root =
        ImportTrees.unit(
            PACKAGE_LINE
                + "import java.lang.String;\n"
                + "import java.util.List;\n"
                + "import java.util.List;\n"
                + "import app.Helper;\n"
                + "import static java.util.Map.of;\n"
                + "import static java.util.Map.of;\n"
                + "import java.util.*;\n");
    assertThat(MatchPositions.at(new RedundantImportCheck(MESSAGE).matches(root)))
        .containsExactly(
            MatchPositions.col1(2),
            MatchPositions.col1(MatchPositions.LINE_4),
            MatchPositions.col1(MatchPositions.LINE_5),
            MatchPositions.col1(MatchPositions.LINE_7));
  }

  @Test
  void unnecessaryQualifiedTypesAreReportedWhenTheSimpleNameResolves() {
    final String source = PACKAGE_LINE + "import java.util.List;\n";
    final SyntaxNode root =
        withTypes(
            source,
            qualifiedType("java.util", "List", 3),
            qualifiedType("java.lang", "String", 4),
            qualifiedType("app", "Helper", 5),
            qualifiedType("org.other", "Thing", 6));
    assertThat(MatchPositions.at(new UnnecessaryFullyQualifiedTypeCheck(MESSAGE).matches(root)))
        .containsExactly(
            MatchPositions.col1(MatchPositions.LINE_3),
            MatchPositions.col1(MatchPositions.LINE_4),
            MatchPositions.col1(MatchPositions.LINE_5));
  }

  @Test
  void importOrderChecksGroupsSortingAndSeparation() {
    final String source =
        "import java.util.Map;\nimport java.util.List;\nimport app.Z;\n\nimport java.io.File;\n";
    final SyntaxNode root = ImportTrees.unit(source);
    assertThat(
            MatchPositions.at(
                new ImportOrderCheck(List.of(), true, false, true, MESSAGE).matches(root)))
        .containsExactly(MatchPositions.col1(2), MatchPositions.col1(MatchPositions.LINE_3));
    assertThat(
            MatchPositions.at(
                new ImportOrderCheck(List.of(), false, false, true, MESSAGE).matches(root)))
        .isEmpty();
    assertThat(
            MatchPositions.at(
                MatchPositions.textMatches(
                    new ImportOrderCheck(List.of("java", "app"), false, true, true, MESSAGE),
                    FILE,
                    source)))
        .containsExactly(
            MatchPositions.col1(MatchPositions.LINE_3), MatchPositions.col1(MatchPositions.LINE_5));
    assertThat(
            MatchPositions.at(
                new ImportOrderCheck(List.of("/^app\\..*/", "java"), false, false, true, MESSAGE)
                    .matches(root)))
        .containsExactly(MatchPositions.col1(MatchPositions.LINE_3));
    assertThat(
            MatchPositions.at(
                MatchPositions.textMatches(
                    new ImportOrderCheck(List.of("java"), true, false, false, MESSAGE),
                    FILE,
                    "import java.util.Set;\nimport java.util.map;\n")))
        .containsExactly(MatchPositions.col1(2));
    assertThatThrownBy(() -> new ImportOrderCheck(List.of("/(/"), true, false, true, MESSAGE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void customImportOrderChecksTokenGroupsSortingAndBlankLines() {
    final CustomImportOrderCheck.Rules rules =
        new CustomImportOrderCheck.Rules(
            "STATIC###THIRD_PARTY_PACKAGE", "^$", "^(java|javax)\\.", ".*");
    final String ordered =
        "import static org.x.A.b;\n\nimport java.util.List;\nimport org.example.Zed;\n"
            + "import org.example.Alpha;\n";
    assertThat(
            MatchPositions.at(
                MatchPositions.textMatches(
                    new CustomImportOrderCheck(rules, true, true, MESSAGE), FILE, ordered)))
        .containsExactly(MatchPositions.col1(MatchPositions.LINE_5));
    assertThat(
            MatchPositions.at(
                MatchPositions.textMatches(
                    new CustomImportOrderCheck(rules, false, true, MESSAGE), FILE, ordered)))
        .isEmpty();
    assertThat(
            MatchPositions.at(
                MatchPositions.textMatches(
                    new CustomImportOrderCheck(rules, true, true, MESSAGE),
                    FILE,
                    "import static org.x.A.b;\nimport java.util.List;\n")))
        .containsExactly(MatchPositions.col1(2));
    assertThat(
            MatchPositions.at(
                MatchPositions.textMatches(
                    new CustomImportOrderCheck(rules, true, true, MESSAGE),
                    FILE,
                    "import org.example.Alpha;\n\nimport org.example.Zed;\n")))
        .containsExactly(MatchPositions.col1(MatchPositions.LINE_3));
    final CustomImportOrderCheck.Rules onlyStatic =
        new CustomImportOrderCheck.Rules("STATIC", "^$", "^java\\.", ".*");
    assertThat(
            MatchPositions.at(
                MatchPositions.textMatches(
                    new CustomImportOrderCheck(onlyStatic, true, true, MESSAGE),
                    FILE,
                    "import java.util.List;\n")))
        .containsExactly(MatchPositions.col1(1));
    final CustomImportOrderCheck.Rules grouped =
        new CustomImportOrderCheck.Rules(
            "SPECIAL_IMPORTS####STANDARD_JAVA_PACKAGE###THIRD_PARTY_PACKAGE",
            "^org\\.x\\..*",
            "^java\\.",
            ".*");
    assertThat(
            MatchPositions.at(
                MatchPositions.textMatches(
                    new CustomImportOrderCheck(grouped, true, true, MESSAGE),
                    FILE,
                    "import java.util.List;\nimport org.x.A;\n")))
        .isEmpty();
  }

  @Test
  void customImportOrderRejectsUnknownTokensAndBadPatterns() {
    assertThatThrownBy(
            () ->
                new CustomImportOrderCheck(
                    new CustomImportOrderCheck.Rules("STATIC###SAME_PACKAGE(3)", "^$", "^j", ".*"),
                    true,
                    true,
                    MESSAGE))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new CustomImportOrderCheck(
                    new CustomImportOrderCheck.Rules("STATIC", "(", "^j", ".*"),
                    true,
                    true,
                    MESSAGE))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
