package dev.ward.analysis.domain.android.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.android.XmlParser;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The behavior of the checks that the catalog fixtures, one file per case, cannot show. */
class AndroidChecksTest {

  private static final String MANIFEST = "<manifest xmlns:android=\"urn:a\">\n%s</manifest>\n";

  private static List<RuleMatch> run(final RuleCheck check, final String text) {
    return check.matches(XmlParser.parse(text).tree());
  }

  private static String application(final String attributes) {
    return MANIFEST.formatted("<application " + attributes + "/>\n");
  }

  @Test
  void aBackupLimitedByExtractionRulesOrFullBackupContentIsNotReported() {
    final RuleCheck check = new AllowBackupCheck();

    assertThat(run(check, application("android:dataExtractionRules=\"@xml/rules\""))).isEmpty();
    assertThat(run(check, application("android:fullBackupContent=\"@xml/rules\""))).isEmpty();
    assertThat(run(check, application("android:allowBackup=\"false\""))).isEmpty();
    assertThat(run(check, application("android:allowBackup=\"true\""))).hasSize(1);
    assertThat(run(check, application("android:label=\"x\""))).hasSize(1);
  }

  @Test
  void aFileWithoutAnApplicationHasNothingToReport() {
    final String library = MANIFEST.formatted("");

    assertThat(run(new AllowBackupCheck(), library)).isEmpty();
    assertThat(run(new NetworkConfigCleartextCheck(), library)).isEmpty();
    assertThat(run(new ApplicationFlagCheck("debuggable", "m"), library)).isEmpty();
  }

  @Test
  void aProviderIsNotExportedByAnIntentFilterAlone() {
    final String text =
        MANIFEST.formatted("<provider android:name=\".P\"><intent-filter/></provider>\n");

    assertThat(run(new ExportedComponentCheck("provider"), text)).isEmpty();
  }

  @Test
  void aBlankPermissionIsNoPermission() {
    final String text =
        MANIFEST.formatted(
            "<service android:name=\".S\" android:exported=\"true\" android:permission=\" \"/>\n");

    assertThat(run(new ExportedComponentCheck("service"), text)).hasSize(1);
  }

  @Test
  void theKindOfComponentSelectsWhatIsChecked() {
    final String text =
        MANIFEST.formatted("<receiver android:name=\".R\" android:exported=\"true\"/>\n");

    assertThat(run(new ExportedComponentCheck("service"), text)).isEmpty();
    assertThat(run(new ExportedComponentCheck("receiver"), text))
        .singleElement()
        .satisfies(match -> assertThat(match.message()).startsWith("Exported receiver"));
  }

  @Test
  void textInsideThePreferenceAndNavigationFilesIsChecked() {
    final String preferences =
        "<PreferenceScreen xmlns:android=\"urn:a\"><Preference android:title=\"Sync\""
            + " android:summary=\"@string/s\"/></PreferenceScreen>";

    assertThat(run(new HardcodedTextCheck(), preferences)).hasSize(1);
    assertThat(run(new HardcodedTextCheck(), "<resources><string android:text=\"x\"/></resources>"))
        .isEmpty();
    assertThat(run(new HardcodedTextCheck(), "<a android:text=\"  \"/>")).isEmpty();
    assertThat(run(new HardcodedTextCheck(), "<a text=\"x\"/>")).isEmpty();
  }

  @Test
  void anImageHiddenFromAccessibilityNeedsNoDescription() {
    final String hidden = "<ImageView android:importantForAccessibility=\"noHideDescendants\"/>";
    final String yes = "<ImageView android:importantForAccessibility=\"yes\"/>";
    final String material =
        "<com.google.android.material.imageview.ShapeableImageView android:id=\"@+id/a\"/>";

    assertThat(run(new ContentDescriptionCheck(), hidden)).isEmpty();
    assertThat(run(new ContentDescriptionCheck(), yes)).hasSize(1);
    assertThat(run(new ContentDescriptionCheck(), material)).hasSize(1);
  }

  @Test
  void aBrokenFileGivesNoFindings() {
    assertThat(run(new ContentDescriptionCheck(), "<ImageView")).isEmpty();
    assertThat(run(new ExportedComponentCheck("service"), "<<")).isEmpty();
  }
}
