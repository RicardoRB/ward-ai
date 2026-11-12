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

class NodeLicenseLocationTest {
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
  void findsHoistedAndScopedPackagesFromAMemberDirectory() throws IOException {
    packageJson("node_modules/@scope/pkg", "@scope/pkg", "2.0.0", ",\"license\":\"BSD-3-Clause\"");

    assertThat(lookup("packages/app", "@scope/pkg", "2.0.0").license().describe())
        .isEqualTo("BSD-3-Clause");
  }

  @Test
  void prefersTheNestedInstallOfTheMemberOverTheRoot() throws IOException {
    packageJson("node_modules/dup", "dup", VERSION, ",\"license\":\"MIT\"");
    packageJson("packages/app/node_modules/dup", "dup", VERSION, ",\"license\":\"ISC\"");

    assertThat(lookup("packages/app", "dup", VERSION).license().describe()).isEqualTo("ISC");
  }

  @Test
  void findsPackagesInThePnpmStore() throws IOException {
    packageJson(
        "node_modules/.pnpm/@scope+pkg@1.0.0/node_modules/@scope/pkg",
        "@scope/pkg",
        VERSION,
        ",\"license\":\"Apache-2.0\"");

    assertThat(lookup("", "@scope/pkg", VERSION).license().describe()).isEqualTo("Apache-2.0");
  }

  @Test
  void aMissingNodeModulesDirectoryIsNotInstalled() {
    final LicenseLookup result = lookup("", "lodash", "4.17.21");

    assertThat(result.present()).isFalse();
    assertThat(result.reason()).isEqualTo(LicenseLookup.Reason.NOT_INSTALLED);
    assertThat(result.detail()).contains("not installed");
  }

  @Test
  void aDifferentInstalledVersionNamesBothVersions() throws IOException {
    packageJson("node_modules/lodash", "lodash", "4.17.20", ",\"license\":\"MIT\"");

    final LicenseLookup result = lookup("", "lodash", "4.17.21");

    assertThat(result.reason()).isEqualTo(LicenseLookup.Reason.VERSION_MISMATCH);
    assertThat(result.detail()).contains("4.17.20").contains("4.17.21");
  }
}
