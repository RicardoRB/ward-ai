package dev.ward.analysis.domain.android.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.android.AndroidSyntax;
import dev.ward.analysis.domain.android.XmlParser;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.ProjectMatch;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** The whole-project facts of the check; the catalog fixture covers a values file. */
class UnusedResourcesCheckTest {

  private static final Span SPAN = new Span(1, 1, 1, 1, 0, 0);

  private final SortedMap<String, SyntaxNode> trees = new TreeMap<>();

  private void xml(final String path, final String text) {
    trees.put(path, XmlParser.parse(text).tree());
  }

  /** A code file as the frontends give it: leaf tokens with their text, in order. */
  private void code(final String path, final String... tokens) {
    trees.put(
        path,
        new SyntaxNode(
            "compilation_unit",
            SPAN,
            Map.of(),
            java.util.Arrays.stream(tokens)
                .map(token -> new SyntaxNode("token", SPAN, Map.of("text", token), List.of()))
                .toList()));
  }

  private List<String> unused() {
    return new UnusedResourcesCheck()
        .project(trees).stream()
            .map(match -> match.path() + " " + match.match().message())
            .toList();
  }

  @Test
  void aFileResourceIsUsedByAnXmlReferenceOrACodeReference() {
    xml("res/layout/main.xml", "<LinearLayout><include layout=\"@layout/row\"/></LinearLayout>");
    xml("res/layout-land/row.xml", "<TextView/>");
    xml("res/drawable/logo.xml", "<vector/>");
    xml("res/menu/top.xml", "<menu/>");
    xml("res/drawable-hdpi/icon.xml", "<vector/>");
    code("Main.kt", "setContentView", "(", "R", ".", "layout", ".", "main", ")");
    code("Menu.java", "inflate", "(", "R", ".", "menu", ".", "top", ")");

    assertThat(unused())
        .containsExactly(
            "res/drawable-hdpi/icon.xml Unused resource drawable/icon: nothing refers to it.",
            "res/drawable/logo.xml Unused resource drawable/logo: nothing refers to it.");
  }

  @Test
  void aLayoutIsUsedWhenItsViewBindingIsNamed() {
    xml("res/layout/activity_main.xml", "<LinearLayout/>");
    xml("res/layout/other_screen.xml", "<LinearLayout/>");
    code("Main.kt", "ActivityMainBinding", ".", "inflate", "(", ")");

    assertThat(unused()).singleElement().asString().contains("layout/other_screen");
  }

  @Test
  void aDottedStyleNameIsOneResourceInXmlAndInCode() {
    xml("res/values/styles.xml", "<resources><style name=\"Theme.App\"/></resources>");
    code("Main.kt", "R", ".", "style", ".", "Theme_App");

    assertThat(unused()).isEmpty();
  }

  @Test
  void aReferenceInAnotherValueOrAFrameworkReferenceIsRead() {
    xml(
        "res/values/values.xml",
        "<resources><string name=\"a\">@string/b</string><string name=\"b\">x</string>"
            + "<color name=\"c\">@android:color/white</color></resources>");

    assertThat(unused())
        .containsExactly(
            "res/values/values.xml Unused resource string/a: nothing refers to it.",
            "res/values/values.xml Unused resource color/c: nothing refers to it.");
  }

  @Test
  void aFrameworkRClassIsNotTheProjectsAndOtherFilesDefineNothing() {
    xml("res/values/strings.xml", "<resources><string name=\"ok\">x</string></resources>");
    xml("src/main/AndroidManifest.xml", "<manifest/>");
    xml("res/raw/keep.xml", "<resources/>");
    xml("res/values/broken.xml", "<resources>");
    code("Main.kt", "android", ".", "R", ".", "string", ".", "ok");

    assertThat(unused()).singleElement().asString().contains("string/ok");
  }

  @Test
  void theTreeOfABrokenFileIsADocumentAndDefinesNothing() {
    assertThat(trees.isEmpty()).isTrue();
    xml("res/values/broken.xml", "<a>");

    assertThat(trees.get("res/values/broken.xml").kind()).isEqualTo(AndroidSyntax.DOCUMENT);
    assertThat(new UnusedResourcesCheck().project(trees)).isEmpty();
    assertThat(new UnusedResourcesCheck().matches(trees.get("res/values/broken.xml"))).isEmpty();
  }

  @Test
  void matchesCarryThePathAndThePositionOfTheDefinition() {
    xml("res/values/strings.xml", "<resources>\n  <string name=\"x\">x</string>\n</resources>");

    final List<ProjectMatch> matches = new UnusedResourcesCheck().project(trees);

    assertThat(matches)
        .singleElement()
        .satisfies(
            match -> {
              assertThat(match.path()).isEqualTo("res/values/strings.xml");
              assertThat(
                      match.match().span().startLine() + ":" + match.match().span().startColumn())
                  .isEqualTo("2:3");
            });
  }
}
