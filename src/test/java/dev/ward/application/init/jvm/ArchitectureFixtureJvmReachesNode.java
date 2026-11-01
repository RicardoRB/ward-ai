package dev.ward.application.init.jvm;

import dev.ward.application.init.node.NodeTools;
import dev.ward.domain.project.BuildTool;
import java.util.List;

/** A violating fixture: a JVM toolchain class that depends on the Node toolchain. */
public final class ArchitectureFixtureJvmReachesNode {
  public List<String> command() {
    return NodeTools.command(BuildTool.NPM, "lint");
  }
}
