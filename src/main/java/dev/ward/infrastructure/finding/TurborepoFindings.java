package dev.ward.infrastructure.finding;

import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import dev.ward.domain.project.WorkspaceMember;
import dev.ward.domain.project.WorkspaceMembers;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Reads a turbo gate's findings per package: each {@code <package>:<task>: } block is parsed by the
 * gate's own finding source with the member as project root, and each finding's file is rebased
 * onto the workspace root.
 */
final class TurborepoFindings implements GateFindingSource {
  private final GateFindingSource delegate;
  private final WorkspaceMembers members;

  TurborepoFindings(final GateFindingSource delegate, final WorkspaceMembers members) {
    this.delegate = delegate;
    this.members = members;
  }

  @Override
  public String gateId() {
    return delegate.gateId();
  }

  @Override
  public List<Finding> extract(
      final Project project, final GateResult result, final Instant gateStart) {
    if (project.taskRunner() != TaskRunner.TURBO) {
      return delegate.extract(project, result, gateStart);
    }
    final List<TurboOutput.Block> blocks = TurboOutput.parse(output(result));
    if (blocks.stream().allMatch(block -> block.packageKey().isEmpty())) {
      return delegate.extract(project, result, gateStart);
    }
    final Map<String, WorkspaceMember> byName = byName(project.root());
    final List<Finding> findings = new ArrayList<>();
    for (final TurboOutput.Block block : blocks) {
      final WorkspaceMember member = byName.get(block.packageKey());
      if (member != null) {
        findings.addAll(
            delegate
                .extract(member.project(), blockResult(result, block.text()), gateStart)
                .stream()
                .map(finding -> rebased(project.root(), member, finding))
                .toList());
      }
    }
    return List.copyOf(findings);
  }

  private Map<String, WorkspaceMember> byName(final Path root) {
    final Map<String, WorkspaceMember> byName = new LinkedHashMap<>();
    members.members(root).forEach(member -> byName.putIfAbsent(member.name(), member));
    return byName;
  }

  private static GateResult blockResult(final GateResult result, final String text) {
    return GateResult.builder(result.name(), result.status())
        .type(result.type())
        .command(result.command())
        .exitCode(result.exitCode())
        .duration(result.duration())
        .stdout(text)
        .stderr("")
        .summary(result.summary())
        .build();
  }

  private static Finding rebased(
      final Path root, final WorkspaceMember member, final Finding finding) {
    final String file = finding.file();
    if (file == null || Path.of(file).isAbsolute()) {
      return finding;
    }
    final String prefix = root.relativize(member.directory()).toString().replace('\\', '/');
    if (prefix.isEmpty()) {
      return finding;
    }
    return new Finding(
        finding.gateId(),
        finding.tool(),
        finding.ruleId(),
        finding.severity(),
        finding.message(),
        prefix + "/" + file,
        finding.line(),
        finding.column(),
        finding.helpUri());
  }

  private static String output(final GateResult result) {
    return Stream.of(result.stdout(), result.stderr())
        .filter(text -> text != null && !text.isEmpty())
        .reduce((first, second) -> first + "\n" + second)
        .orElse("");
  }
}
