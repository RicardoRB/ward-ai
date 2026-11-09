package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The argument-list helpers that the call checks share: thread calls and super arguments. */
final class SyntaxArgumentHelpersTest {

  @Test
  void threadCallNeedsTargetOrTwoPositionalArguments() {
    assertThat(ThreadInstantiationCheck.isThreadCall(Nodes.leaf("Thread()"))).isTrue();
    assertThat(ThreadInstantiationCheck.isThreadCall(Nodes.leaf("Thread(None)"))).isTrue();
    assertThat(ThreadInstantiationCheck.isThreadCall(Nodes.leaf("Thread(None,f)"))).isFalse();
    assertThat(ThreadInstantiationCheck.isThreadCall(Nodes.leaf("Thread(target=f)"))).isFalse();
  }

  @Test
  void superArgumentsStopAtTheMatchingParenthesis() {
    assertThat(BadSuperCallCheck.superArguments("super(A,self).f()")).containsExactly("A", "self");
    assertThat(BadSuperCallCheck.superArguments("super(type(self),self).f()"))
        .containsExactly("type(self)", "self");
    assertThat(BadSuperCallCheck.superArguments("super()")).isEmpty();
  }
}
