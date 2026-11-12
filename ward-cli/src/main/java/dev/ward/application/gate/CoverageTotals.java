package dev.ward.application.gate;

import dev.ward.domain.gate.FileLines;
import dev.ward.domain.gate.LineCoverage;
import dev.ward.domain.gate.LineHits;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The line and branch counts summed over every file of every report.
 *
 * @param lines the covered and coverable lines
 * @param branches the covered and coverable branches
 */
record CoverageTotals(LineCoverage lines, LineCoverage branches) {

  /**
   * Sums the reports. A file is told apart by the module directory of its report and its name, and
   * a line that any report covers is covered. shortcut: a configured report outside every module
   * directory counts as the root's, so same-named files of two such reports merge; list module
   * reports under their modules to keep them apart.
   */
  static CoverageTotals of(final Path root, final List<LineHits> hits, final CrapPorts ports) {
    final List<Path> modules = ports.files().moduleDirectories(root);
    final Map<String, FileLines> merged = new HashMap<>();
    for (final LineHits report : hits) {
      final String module = module(modules, report.report());
      for (final LineHits.Entry entry : report.entries()) {
        merged.merge(module + "|" + entry.name(), entry.lines(), FileLines::merge);
      }
    }
    long coverable = 0;
    long covered = 0;
    long branches = 0;
    long coveredBranches = 0;
    for (final FileLines lines : merged.values()) {
      coverable += lines.coverable().size();
      covered += lines.covered().size();
      branches += lines.branchCount();
      coveredBranches += lines.coveredBranchCount();
    }
    return new CoverageTotals(
        LineCoverage.of(covered, coverable), LineCoverage.of(coveredBranches, branches));
  }

  private static String module(final List<Path> modules, final Path report) {
    final Path file = report.toAbsolutePath().normalize();
    Path best = null;
    for (final Path module : modules) {
      final Path directory = module.toAbsolutePath().normalize();
      if (file.startsWith(directory)
          && (best == null || directory.getNameCount() > best.getNameCount())) {
        best = directory;
      }
    }
    return best == null ? "" : best.toString();
  }
}
