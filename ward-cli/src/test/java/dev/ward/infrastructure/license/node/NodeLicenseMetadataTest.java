package dev.ward.infrastructure.license.node;

import static dev.ward.infrastructure.license.LicenseReaderFixtures.dependency;
import static dev.ward.infrastructure.license.LicenseReaderFixtures.project;
import static dev.ward.infrastructure.license.LicenseReaderFixtures.write;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.dependency.Ecosystem;
import dev.ward.domain.license.LicenseLookup;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Language;
import dev.ward.infrastructure.json.ForyJsonCodec;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NodeLicenseMetadataTest {
  private static final String VERSION = "1.0.0";

  @TempDir Path root;

  private final NodeLicenseMetadata reader = new NodeLicenseMetadata(new ForyJsonCodec());

  private LicenseLookup lookup(final String module, final String name, final String version) {
    return reader.lookup(
        project(root, Language.JAVASCRIPT, BuildTool.NPM),
        module,
        dependency(Ecosystem.NPM, name, version));
  }

  private void packageJson(
      final String where, final String name, final String version, final String extra)
      throws IOException {
    write(
        root,
        where + "/package.json",
        "{\"name\":\"" + name + "\",\"version\":\"" + version + "\"" + extra + "}");
  }

  @Test
  void readsAnSpdxString() throws IOException {
    packageJson("node_modules/left-pad", "left-pad", "1.3.0", ",\"license\":\"MIT\"");

    final LicenseLookup result = lookup("", "left-pad", "1.3.0");

    assertThat(result.license().describe()).isEqualTo("MIT");
    assertThat(result.detail()).isEqualTo("package-json");
  }

  @Test
  void readsTheLegacyObjectAndArrayForms() throws IOException {
    packageJson("node_modules/a", "a", VERSION, ",\"license\":{\"type\":\"ISC\"}");
    packageJson(
        "node_modules/b",
        "b",
        VERSION,
        ",\"licenses\":[{\"type\":\"MIT\"},{\"type\":\"Apache-2.0\"}]");

    assertThat(lookup("", "a", VERSION).license().describe()).isEqualTo("ISC");
    assertThat(lookup("", "b", VERSION).license().entries()).hasSize(2);
  }

  @Test
  void aPackageJsonWithoutALicenseDeclaresNone() throws IOException {
    packageJson("node_modules/bare", "bare", VERSION, "");

    assertThat(lookup("", "bare", VERSION).reason())
        .isEqualTo(LicenseLookup.Reason.NO_LICENSE_DECLARED);
  }
}
