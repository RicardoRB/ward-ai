package dev.ward.domain;

import dev.ward.domain.doctor.EnvironmentFacts;
import dev.ward.domain.doctor.EnvironmentInspection;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reports a fixed set of executables instead of probing the host PATH. */
public class FakeEnvironmentInspection implements EnvironmentInspection {

  public final List<Path> inspectedRoots = new ArrayList<>();
  private final EnvironmentFacts facts;
  private boolean releaseTag;

  /** Creates an environment where only {@code executables} are on the path. */
  public FakeEnvironmentInspection(final String... executables) {
    this.facts = new EnvironmentFacts(Set.of(executables), Map.of());
  }

  /** Makes the project report a reachable release tag. */
  public FakeEnvironmentInspection withReleaseTag() {
    releaseTag = true;
    return this;
  }

  @Override
  public boolean hasReleaseTag(final Path projectRoot) {
    return releaseTag;
  }

  @Override
  public EnvironmentFacts inspect(final Path projectRoot) {
    inspectedRoots.add(projectRoot);
    return facts;
  }
}
