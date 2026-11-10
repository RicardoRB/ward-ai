package dev.ward.infrastructure.format;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class EditorConfigGlobTest {

  @Test
  void starMatchesWithinOneFolderName() {
    assertThat(EditorConfigGlob.compile("src/*.java").matcher("src/Main.java").matches()).isTrue();
    assertThat(EditorConfigGlob.compile("src/*.java").matcher("src/a/Main.java").matches())
        .isFalse();
  }

  @Test
  void globWithoutSlashMatchesFileNameInAnyFolder() {
    final Pattern pattern = EditorConfigGlob.compile("*.java");
    assertThat(pattern.matcher("Main.java").matches()).isTrue();
    assertThat(pattern.matcher("a/b/Main.java").matches()).isTrue();
    assertThat(pattern.matcher("Main.javax").matches()).isFalse();
  }

  @Test
  void doubleStarCrossesFoldersAndMayBeEmpty() {
    final Pattern pattern = EditorConfigGlob.compile("src/**");
    assertThat(pattern.matcher("src/a/b/C.java").matches()).isTrue();
    assertThat(EditorConfigGlob.compile("**/Gen.java").matcher("Gen.java").matches()).isTrue();
    assertThat(EditorConfigGlob.compile("**/Gen.java").matcher("x/y/Gen.java").matches()).isTrue();
  }

  @Test
  void questionMarkMatchesOneCharacterExceptSlash() {
    final Pattern pattern = EditorConfigGlob.compile("?.ts");
    assertThat(pattern.matcher("a.ts").matches()).isTrue();
    assertThat(pattern.matcher("ab.ts").matches()).isFalse();
    assertThat(pattern.matcher("x/a.ts").matches()).isTrue();
    assertThat(EditorConfigGlob.compile("src/?.ts").matcher("src//.ts").matches()).isFalse();
  }

  @Test
  void bracketsMatchOneOfTheListedCharacters() {
    assertThat(EditorConfigGlob.compile("[ab].java").matcher("a.java").matches()).isTrue();
    assertThat(EditorConfigGlob.compile("[ab].java").matcher("c.java").matches()).isFalse();
    assertThat(EditorConfigGlob.compile("[a-c].java").matcher("b.java").matches()).isTrue();
  }

  @Test
  void negatedBracketsMatchAnyOtherCharacterButNotSlash() {
    final Pattern pattern = EditorConfigGlob.compile("[!ab].java");
    assertThat(pattern.matcher("c.java").matches()).isTrue();
    assertThat(pattern.matcher("a.java").matches()).isFalse();
    assertThat(EditorConfigGlob.compile("x[!a]y").matcher("x/y").matches()).isFalse();
  }

  @Test
  void leadingSlashAnchorsGlobToItsFolder() {
    assertThat(EditorConfigGlob.compile("/Main.java").matcher("Main.java").matches()).isTrue();
    assertThat(EditorConfigGlob.compile("/Main.java").matcher("x/Main.java").matches()).isFalse();
  }

  @Test
  void regexMetacharactersAreLiterals() {
    assertThat(EditorConfigGlob.compile("(a)+.java").matcher("(a)+.java").matches()).isTrue();
    assertThat(EditorConfigGlob.compile("(a)+.java").matcher("aa.java").matches()).isFalse();
  }
}
