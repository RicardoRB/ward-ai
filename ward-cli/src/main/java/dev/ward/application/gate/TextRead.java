package dev.ward.application.gate;

import dev.ward.analysis.domain.ir.TextFile;
import dev.ward.analysis.domain.rule.RuleExecutionError;
import java.util.List;

/**
 * The text files of a project that the {@code secrets} check scans.
 *
 * @param files the files, sorted by path
 * @param errors the files that could not be read or are too large; each is an execution error
 */
public record TextRead(List<TextFile> files, List<RuleExecutionError> errors) {

  /** No files and no errors. */
  public static final TextRead EMPTY = new TextRead(List.of(), List.of());

  /** Copies the lists. */
  public TextRead {
    files = List.copyOf(files);
    errors = List.copyOf(errors);
  }
}
