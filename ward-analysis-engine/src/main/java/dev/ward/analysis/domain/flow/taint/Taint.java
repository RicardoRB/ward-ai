package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.FlowStep;
import java.util.List;
import java.util.Objects;

/**
 * The taint of one write: the source it came from and the steps from that source to the write.
 *
 * @param source the name of the source
 * @param path the steps up to and including this write, in order
 */
record Taint(String source, List<FlowStep> path) {

  /**
   * Copies the path.
   *
   * @throws NullPointerException if a field is missing
   */
  Taint {
    Objects.requireNonNull(source, "source");
    path = List.copyOf(Objects.requireNonNull(path, "path"));
  }
}
