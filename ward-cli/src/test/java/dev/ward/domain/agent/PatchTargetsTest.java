package dev.ward.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.agent.PatchTarget.Kind;
import java.util.List;
import org.junit.jupiter.api.Test;

class PatchTargetsTest {

  private static List<PatchTarget> targets(final String patch) {
    return PatchTargets.parse(patch).orElseThrow();
  }

  @Test
  void everyFileInARecordedMultiFilePatchIsATarget() {
    assertThat(
            targets(
                "*** Begin Patch\n*** Update File: docs/foo/proposal.md\n@@\n-a\n+b\n*** Update File: docs/x/guide.md\n@@\n-a\n+b\n*** End Patch\n"))
        .containsExactly(
            new PatchTarget("docs/foo/proposal.md", Kind.WRITE),
            new PatchTarget("docs/x/guide.md", Kind.WRITE));
  }

  @Test
  void addUpdateAndDeleteHeadersMapToOperations() {
    assertThat(
            targets(
                "*** Begin Patch\n*** Add File: docs/foo/design.md\n+# Design\n*** Update File: docs/foo/tasks.md\n@@\n-a\n+b\n*** Delete File: docs/foo/notes.md\n*** End Patch\n"))
        .containsExactly(
            new PatchTarget("docs/foo/design.md", Kind.WRITE),
            new PatchTarget("docs/foo/tasks.md", Kind.WRITE),
            new PatchTarget("docs/foo/notes.md", Kind.DELETE));
  }

  @Test
  void moveDestinationAndSourceAreBothTargets() {
    assertThat(
            targets(
                """
                *** Begin Patch
                *** Update File: docs/foo/x/guide.md
                *** Move to: docs/x/guide.md
                @@
                -a
                +b
                *** End Patch
                """))
        .containsExactly(
            new PatchTarget("docs/foo/x/guide.md", Kind.WRITE),
            new PatchTarget("docs/foo/x/guide.md", Kind.MOVE_FROM),
            new PatchTarget("docs/x/guide.md", Kind.MOVE_TO));
  }

  @Test
  void unifiedAndGitDiffHeadersAreUnderstood() {
    assertThat(
            targets(
                """
                diff --git a/docs/x/guide.md b/docs/x/guide.md
                --- a/docs/x/guide.md\t2026-10-13 10:00:00
                +++ b/docs/x/guide.md\t2026-10-13 10:01:00
                @@ -1 +1 @@
                --- removed line that looks like a header
                --- /dev/null
                +++ b/docs/foo/new.md
                --- "a/docs/foo/old.md"
                +++ /dev/null
                --- a/one.md
                +++ b/two.md
                rename from docs/foo
                rename to docs/archive/2026-10-13-foo
                copy to docs/y/guide.md
                """))
        .containsExactly(
            new PatchTarget("docs/x/guide.md", Kind.WRITE),
            new PatchTarget("docs/foo/new.md", Kind.WRITE),
            new PatchTarget("docs/foo/old.md", Kind.DELETE),
            new PatchTarget("one.md", Kind.MOVE_FROM),
            new PatchTarget("two.md", Kind.MOVE_TO),
            new PatchTarget("docs/foo", Kind.MOVE_FROM),
            new PatchTarget("docs/archive/2026-10-13-foo", Kind.MOVE_TO),
            new PatchTarget("docs/y/guide.md", Kind.WRITE));
  }

  @Test
  void unrecognizedTextIsUninspectableAndEmptyTextHasNoTargets() {
    assertThat(PatchTargets.parse("please change docs/x/guide.md")).isEmpty();
    assertThat(PatchTargets.parse("--- only an old header")).isEmpty();
    assertThat(PatchTargets.parse("  \n")).contains(List.of());
    assertThat(PatchTargets.parse(null)).contains(List.of());
    assertThat(targets("*** Begin Patch\n*** Add File:   \n*** End Patch")).isEmpty();
  }
}
