package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.ProjectFiles;
import dev.ward.analysis.domain.rule.ProjectMatch;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/** Builds the syntax trees and manifest files of a small in-memory project for the checks. */
final class ProjectFixtures {

  private static final AntlrLanguageFrontend FRONTEND = new AntlrLanguageFrontend();

  private ProjectFixtures() {}

  /** Parses each file as TypeScript; the map is by project-relative path. */
  static SortedMap<String, SyntaxNode> trees(final Map<String, String> sources) {
    final SortedMap<String, SyntaxNode> trees = new TreeMap<>();
    sources.forEach(
        (path, text) ->
            trees.put(
                path,
                FRONTEND.parse(new SourceUnit(path, AnalysisLanguage.TYPESCRIPT, text)).tree()));
    return trees;
  }

  /** The manifest files of a project, by path. */
  static ProjectFiles manifests(final Map<String, String> files) {
    return new ProjectFiles(files);
  }

  /** Each finding as {@code path:line:column}, in order, for comparing positions. */
  static List<String> positions(final List<ProjectMatch> matches) {
    return matches.stream()
        .map(
            match ->
                match.path()
                    + ":"
                    + match.match().span().startLine()
                    + ":"
                    + match.match().span().startColumn())
        .toList();
  }

  /** Each finding's message, in order. */
  static List<String> messages(final List<ProjectMatch> matches) {
    return matches.stream().map(match -> match.match().message()).toList();
  }
}
