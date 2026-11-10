package dev.ward.analysis.domain.node;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

/** The names that global comments declare, read as ESLint reads them. */
final class NodeGlobalCommentsTest {

  @Test
  void globalCommentDeclaresEachName() {
    assertThat(NodeGlobalComments.of("/* global test, expect */\ntest(1);"))
        .containsExactlyInAnyOrder("test", "expect");
  }

  @Test
  void globalsCommentAcceptsEslintValuesAndLeavesOffOut() {
    assertThat(NodeGlobalComments.of("/* globals x: writable, y: readonly, z: off */"))
        .containsExactlyInAnyOrder("x", "y");
  }

  @Test
  void descriptionAfterDoubleDashIsNotAName() {
    assertThat(NodeGlobalComments.of("/* global a -- the test runner */")).containsExactly("a");
  }

  @Test
  void commentWithoutSpaceAfterOpeningIsRead() {
    assertThat(NodeGlobalComments.of("/*global b*/")).containsExactly("b");
  }

  @Test
  void lineCommentAndOtherCommentsDeclareNothing() {
    assertThat(NodeGlobalComments.of("// global c\n/* eslint-env node */ const d = 1;")).isEmpty();
  }

  @Test
  void nonIdentifierItemsAreSkipped() {
    assertThat(NodeGlobalComments.of("/* global 1x, ok */")).isEqualTo(Set.of("ok"));
  }
}
