package dev.ward.infrastructure.analysis.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class JvmModuleLayoutsTest {
  @Test
  void mavenListsModulesWithProfilesAndPomPathsButNotComments() {
    final String pom =
        "<project><modules><module>a</module><module> b/pom.xml </module>"
            + "<!-- <module>old</module> --></modules>"
            + "<profiles><profile><modules><module>c/d</module><module>a</module></modules>"
            + "</profile></profiles></project>";

    assertThat(JvmModuleLayouts.listed(Optional.of(pom), Optional.empty()))
        .containsExactly("a", "b", "c/d");
  }

  @Test
  void gradleListsIncludedProjectsInBothDslsAndSkipsIncludeBuild() {
    final String settings =
        "include(\":core\", \":web:api\")\ninclude 'tools'\nincludeBuild(\"../other\")\n";

    assertThat(JvmModuleLayouts.listed(Optional.empty(), Optional.of(settings)))
        .containsExactly("core", "web/api", "tools");
  }

  @Test
  void aPomWinsAndNoBuildFileListsNothing() {
    assertThat(JvmModuleLayouts.listed(Optional.of("<project/>"), Optional.of("include(\"core\")")))
        .isEmpty();
    assertThat(JvmModuleLayouts.listed(Optional.empty(), Optional.empty())).isEmpty();
  }
}
