package dev.ward.infrastructure.finding;

import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.project.Project;
import java.time.Instant;
import java.util.List;

/** Extracts findings for one gate id from the artifacts its tool writes. */
public interface GateFindingSource {
  /** Returns the id of the gate whose findings this source reads. */
  String gateId();

  /** Returns the findings that the gate's tool wrote for this run. */
  List<Finding> extract(Project project, GateResult result, Instant gateStart);
}
