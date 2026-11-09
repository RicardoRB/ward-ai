package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.ProjectFiles;
import dev.ward.analysis.domain.rule.ProjectMatch;
import java.util.List;
import java.util.SortedMap;

/**
 * A check that is a fact about the whole project, not one file: it reads the syntax trees of every
 * file of its language at once, once per run, after the per-unit cache. A file-by-file run finds
 * nothing with it, so {@link RuleCheck#matches(SyntaxNode)} of such a check returns no matches.
 */
@FunctionalInterface
public interface ProjectCheck {

  /**
   * Returns the matches over all files.
   *
   * @param trees the syntax tree of each file of the rule's language, by project-relative path
   */
  List<ProjectMatch> project(SortedMap<String, SyntaxNode> trees);

  /**
   * Returns the matches over all files, with the manifest files of the project for a check that
   * reads them. By default it ignores the files and returns {@link #project(SortedMap)}.
   *
   * @param trees the syntax tree of each file of the rule's language, by project-relative path
   * @param files the manifest files of the project
   */
  default List<ProjectMatch> project(
      final SortedMap<String, SyntaxNode> trees, final ProjectFiles files) {
    return project(trees);
  }
}
