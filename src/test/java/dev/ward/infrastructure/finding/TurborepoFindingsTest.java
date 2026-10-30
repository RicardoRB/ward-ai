package dev.ward.infrastructure.finding;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.Finding;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import dev.ward.domain.project.WorkspaceMember;
import dev.ward.domain.project.WorkspaceMembers;
import dev.ward.infrastructure.json.ForyJsonCodec;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TurborepoFindingsTest {
  private static final String WEB = "web";
  private static final String UI = "ui";
  private static final int ERROR_LINE = 3;
  private static final int ERROR_COLUMN = 7;

  @TempDir Path root;

  @Test
  void resolvesAMemberTypeErrorAgainstItsDirectory() {
    final Path web = root.resolve("apps/web");
    final CompositeFindingExtractor extractor =
        extractor(new WorkspaceMember(WEB, web, FindingFixtures.typescript(web)));

    final List<Finding> findings =
        extractor.extract(
            turbo(),
            FindingFixtures.failed(
                "tsc",
                "web:check-types: src/page.tsx(3,7): error TS2322: Type 'string' is not"
                    + " assignable to type 'number'.\n"),
            FindingFixtures.justBefore());

    assertThat(findings)
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.ruleId()).isEqualTo("TS2322");
              assertThat(finding.file()).isEqualTo("apps/web/src/page.tsx");
              assertThat(finding.line()).isEqualTo(ERROR_LINE);
              assertThat(finding.column()).isEqualTo(ERROR_COLUMN);
            });
  }

  @Test
  void keepsTheEslintFindingsOfTwoMembers() {
    final Path web = root.resolve("apps/web");
    final Path ui = root.resolve("packages/ui");
    final CompositeFindingExtractor extractor =
        extractor(
            new WorkspaceMember(WEB, web, FindingFixtures.typescript(web)),
            new WorkspaceMember(UI, ui, FindingFixtures.typescript(ui)));

    final String stdout =
        "web:lint: [{\"filePath\":\""
            + web.resolve("src/a.ts")
            + "\",\"messages\":[{\"ruleId\":\"no-unused-vars\",\"severity\":2,"
            + "\"message\":\"unused\",\"line\":1,\"column\":1}]}]\n"
            + "ui:lint: [{\"filePath\":\""
            + ui.resolve("src/b.ts")
            + "\",\"messages\":[{\"ruleId\":\"eqeqeq\",\"severity\":2,"
            + "\"message\":\"use ===\",\"line\":2,\"column\":3}]}]\n";

    final List<Finding> findings =
        extractor.extract(
            turbo(), FindingFixtures.failed("eslint", stdout), FindingFixtures.justBefore());

    assertThat(findings)
        .extracting(Finding::file, Finding::ruleId)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("apps/web/src/a.ts", "no-unused-vars"),
            org.assertj.core.groups.Tuple.tuple("packages/ui/src/b.ts", "eqeqeq"));
  }

  @Test
  void unprefixedOutputIsReadAsOneProject() {
    final CompositeFindingExtractor extractor = extractor();

    final List<Finding> findings =
        extractor.extract(
            turbo(),
            FindingFixtures.failed(
                "tsc", "src/page.tsx(3,7): error TS2322: Type 'string' is not assignable.\n"),
            FindingFixtures.justBefore());

    assertThat(findings).singleElement().extracting(Finding::file).isEqualTo("src/page.tsx");
  }

  private CompositeFindingExtractor extractor(final WorkspaceMember... members) {
    return new CompositeFindingExtractor(
        List.of(new TscOutputFindings(), new EslintJsonFindings(new ForyJsonCodec())),
        new FakeMembers(List.of(members)));
  }

  private Project turbo() {
    return FindingFixtures.typescript(root).withTaskRunner(TaskRunner.TURBO);
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
