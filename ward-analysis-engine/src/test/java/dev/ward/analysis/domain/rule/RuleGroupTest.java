package dev.ward.analysis.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RuleGroupTest {

  @Test
  void familyMatchesItselfAndItsSubcategories() {
    assertThat(RuleGroup.matches("security", "security")).isTrue();
    assertThat(RuleGroup.matches("security", "security/injection")).isTrue();
    assertThat(RuleGroup.matches("security", "security/crypto")).isTrue();
  }

  @Test
  void subcategoryMatchesOnlyItself() {
    assertThat(RuleGroup.matches("security/injection", "security/injection")).isTrue();
    assertThat(RuleGroup.matches("security/injection", "security/crypto")).isFalse();
    assertThat(RuleGroup.matches("security/injection", "security")).isFalse();
  }

  @Test
  void familyDoesNotMatchAnotherFamilyWithSharedPrefix() {
    assertThat(RuleGroup.matches("style", "styled")).isFalse();
  }

  @Test
  void validCategoryHasOneOrTwoKebabCaseLevels() {
    assertThat(RuleGroup.isValidCategory("naming")).isTrue();
    assertThat(RuleGroup.isValidCategory("style/imports")).isTrue();
    assertThat(RuleGroup.isValidCategory("style/imports/order")).isFalse();
    assertThat(RuleGroup.isValidCategory("Style")).isFalse();
    assertThat(RuleGroup.isValidCategory("style/")).isFalse();
  }

  @Test
  void namesListFamiliesAndSubcategoriesWithoutEmptyCategory() {
    final List<String> categories =
        List.of("security/injection", "security/crypto", "", "style", "security");

    assertThat(RuleGroup.names(categories))
        .containsExactly("security", "security/crypto", "security/injection", "style");
  }
}
