package dev.ward.application.gate.apicompat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BaselineResolverTest {
  private static final Path ROOT = Path.of("/project");

  private final FakeRevisions git = new FakeRevisions().ref("HEAD", "head");

  private Baseline resolve(final String base) {
    return new BaselineResolver(git).resolve(ROOT, base);
  }

  @Test
  void latestReleaseTagIsTheDefault() {
    git.tag("v1.2.0").tag("v1.10.0").tag("v1.10.0-rc1").tag("v1.9.3");

    assertThat(resolve(null)).isEqualTo(new Baseline("v1.10.0", "commit-v1.10.0"));
  }

  @Test
  void tagOnHeadIsNotTheBaseline() {
    git.tag("v2.0.0").tag("v1.4.0");
    git.atHead.add("v2.0.0");

    assertThat(resolve(null).ref()).isEqualTo("v1.4.0");
  }

  @Test
  void tagsWithoutLeadingVAndMoreSegmentsAreReleases() {
    git.tag("1.2").tag(String.join(".", "1", "2", "0", "1")).tag("not-a-version").tag("v1");

    assertThat(resolve(null).ref()).isEqualTo(String.join(".", "1", "2", "0", "1"));
  }

  @Test
  void configuredRefWinsAndNoTagIsLookedFor() {
    git.tag("v1.0.0").ref("origin/main", "main-commit");

    assertThat(resolve("origin/main")).isEqualTo(new Baseline("origin/main", "main-commit"));
  }

  @Test
  void noReleaseTagIsAnErrorThatNamesBase() {
    git.tag("v1.0.0-rc1");

    assertThatThrownBy(() -> resolve(null))
        .isInstanceOf(ApiCompatException.class)
        .hasMessageContaining("no release tag")
        .hasMessageContaining("'base'");
  }

  @Test
  void unknownRefNamesTheRef() {
    assertThatThrownBy(() -> resolve("v9.9.9"))
        .isInstanceOf(ApiCompatException.class)
        .hasMessageContaining("v9.9.9");
  }

  @Test
  void shallowCloneWithoutTagsSaysToFetch() {
    git.shallow = true;

    assertThatThrownBy(() -> resolve(null))
        .isInstanceOf(ApiCompatException.class)
        .hasMessageContaining("shallow clone")
        .hasMessageContaining("Fetch tags and history");
  }

  @Test
  void notARepositoryIsAnError() {
    assertThatThrownBy(() -> new BaselineResolver(new FakeRevisions()).resolve(ROOT, null))
        .isInstanceOf(ApiCompatException.class)
        .hasMessageContaining("Git repository");
  }
}
