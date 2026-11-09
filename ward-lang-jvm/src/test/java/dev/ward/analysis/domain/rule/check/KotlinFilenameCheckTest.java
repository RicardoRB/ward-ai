package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import org.junit.jupiter.api.Test;

/**
 * Runs the ktlint filename check over real Kotlin sources, for the file name against its
 * declarations.
 */
class KotlinFilenameCheckTest {

  private static final String MESSAGE = "m";
  private static final String FILE_NAME = "Store.kt";

  @Test
  void aSingleClassFileIsNamedAfterTheClass() {
    final KotlinFilenameCheck check = new KotlinFilenameCheck(MESSAGE);
    final SyntaxNode tree = KotlinSourceTrees.parse("class Store\n");

    assertThat(check.check(tree, new SourceFile(FILE_NAME, "class Store\n"))).isEmpty();
    assertThat(check.check(tree, new SourceFile("Other.kt", "class Store\n"))).hasSize(1);
    assertThat(check.check(tree, new SourceFile("/src/store.kt", "class Store\n"))).hasSize(1);
  }

  @Test
  void otherFilesAreNamedInPascalCase() {
    final KotlinFilenameCheck check = new KotlinFilenameCheck(MESSAGE);
    final SyntaxNode properties = KotlinSourceTrees.parse("val a = 1\nval b = 2\n");

    assertThat(check.check(properties, new SourceFile("Constants.kt", ""))).isEmpty();
    assertThat(check.check(properties, new SourceFile("constants.kt", ""))).hasSize(1);
    assertThat(check.check(properties, new SourceFile("package.kt", ""))).isEmpty();
    assertThat(check.check(properties, new SourceFile("notes.txt", ""))).isEmpty();
  }

  @Test
  void aSingleObjectIsNamedAfterTheObject() {
    final KotlinFilenameCheck check = new KotlinFilenameCheck(MESSAGE);
    final SyntaxNode object = KotlinSourceTrees.parse("object Registry\n");

    assertThat(check.check(object, new SourceFile("Registry.kt", ""))).isEmpty();
    assertThat(check.check(object, new SourceFile("Other.kt", ""))).hasSize(1);
  }

  @Test
  void aFileWithAPrivateClassIsNamedInPascalCase() {
    final KotlinFilenameCheck check = new KotlinFilenameCheck(MESSAGE);
    final SyntaxNode tree = KotlinSourceTrees.parse("private class Hidden\n");

    assertThat(check.check(tree, new SourceFile("Anything.kt", ""))).isEmpty();
    assertThat(check.check(tree, new SourceFile("lower.kt", ""))).hasSize(1);
  }
}
