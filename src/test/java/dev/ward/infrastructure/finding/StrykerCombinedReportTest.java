package dev.ward.infrastructure.finding;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.MutationCounts;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import dev.ward.domain.project.WorkspaceMember;
import dev.ward.domain.project.WorkspaceMembers;
import dev.ward.infrastructure.json.ForyJsonCodec;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A Turborepo root's Stryker score adds up the reports of its members. */
class StrykerCombinedReportTest {
  private static final int WEB_KILLED = 8;
  private static final int WEB_SURVIVED = 2;
  private static final int COMBINED_HALF = 10;
  private static final int PERCENT_HALF = 50;
  private static final int MINIMUM_PERCENT = 60;

  @TempDir Path root;

  @Test
  void combinesTheReportsOfEveryMemberAtATurborepoRoot() {
    final Path web = root.resolve("apps/web");
    final Path ui = root.resolve("packages/ui");
    FindingFixtures.write(
        web.resolve(StrykerJsonMutationReport.REPORT), report(WEB_KILLED, WEB_SURVIVED));
    FindingFixtures.write(
        ui.resolve(StrykerJsonMutationReport.REPORT), report(WEB_SURVIVED, WEB_KILLED));

    final MutationCounts counts =
        combined(
                new WorkspaceMember("web", web, FindingFixtures.typescript(web)),
                new WorkspaceMember("ui", ui, FindingFixtures.typescript(ui)))
            .read(turbo(), FindingFixtures.justBefore());

    assertThat(counts.detected()).isEqualTo(COMBINED_HALF);
    assertThat(counts.undetected()).isEqualTo(COMBINED_HALF);
    assertThat(counts.score()).isEqualTo(PERCENT_HALF);
    assertThat(counts.below(MINIMUM_PERCENT)).isTrue();
  }

  @Test
  void skipsAMemberWithoutAReport() {
    final Path web = root.resolve("apps/web");
    final Path ui = root.resolve("packages/ui");
    FindingFixtures.write(
        web.resolve(StrykerJsonMutationReport.REPORT), report(WEB_KILLED, WEB_SURVIVED));

    final MutationCounts counts =
        combined(
                new WorkspaceMember("web", web, FindingFixtures.typescript(web)),
                new WorkspaceMember("ui", ui, FindingFixtures.typescript(ui)))
            .read(turbo(), FindingFixtures.justBefore());

    assertThat(counts.detected()).isEqualTo(WEB_KILLED);
    assertThat(counts.undetected()).isEqualTo(WEB_SURVIVED);
  }

  private StrykerJsonMutationReport combined(final WorkspaceMember... members) {
    return new StrykerJsonMutationReport(new ForyJsonCodec(), new FakeMembers(List.of(members)));
  }

  private Project turbo() {
    return FindingFixtures.typescript(root).withTaskRunner(TaskRunner.TURBO);
  }

  private static String report(final int killed, final int survived) {
    final String mutants =
        Stream.concat(
                Collections.nCopies(killed, "{\"status\":\"Killed\"}").stream(),
                Collections.nCopies(survived, "{\"status\":\"Survived\"}").stream())
            .reduce((first, second) -> first + "," + second)
            .orElse("");
    return "{\"files\":{\"src/a.ts\":{\"mutants\":[" + mutants + "]}}}";
  }

  private record FakeMembers(List<WorkspaceMember> listed) implements WorkspaceMembers {
    @Override
    public List<WorkspaceMember> members(final Path ignored) {
      return listed;
    }

    @Override
    public Optional<String> packageOf(final Path ignored, final Path path) {
      return listed.stream()
          .filter(member -> path.startsWith(member.directory()))
          .map(WorkspaceMember::name)
          .findFirst();
    }
  }
}
