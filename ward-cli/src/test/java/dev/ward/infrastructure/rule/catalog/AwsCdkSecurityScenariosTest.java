package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RulePreset;
import dev.ward.infrastructure.rule.BundledRuleMatchers;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The scenarios of story #606: TypeScript rules for insecure AWS CDK settings. */
class AwsCdkSecurityScenariosTest {
  private static final String PREFIX = "ward:typescript/";

  private static List<String> reported(final String ruleName, final String source) {
    final List<RuleDefinition> rules =
        CatalogRuleDefinitions.shipped().stream()
            .filter(rule -> rule.covers(AnalysisLanguage.TYPESCRIPT))
            .filter(rule -> RulePreset.STANDARD.selects(rule.presets()))
            .toList();
    final List<SourceUnit> units =
        List.of(new SourceUnit("lib/stack.ts", AnalysisLanguage.TYPESCRIPT, source));
    final List<FrontendSupport> frontends = new RuleFixtureRunner().frontends();
    return new RuleEngine(BundledRuleMatchers.create())
        .run(units, frontends, rules).findings().stream()
        .filter(finding -> finding.ruleId().equals(PREFIX + ruleName))
        .map(finding -> finding.line() + ":" + finding.column())
        .toList();
  }

  @Test
  @DisplayName("a public bucket")
  void aPublicBucket() {
    // Given a CDK stack that creates an S3 bucket with public read access, and one that blocks it
    final String source =
        """
        import { Bucket, BlockPublicAccess } from 'aws-cdk-lib/aws-s3';

        export function build(scope: any) {
          new Bucket(scope, 'Open', { publicReadAccess: true });
          new Bucket(scope, 'Closed', { blockPublicAccess: BlockPublicAccess.BLOCK_ALL });
        }
        """;

    // When the CDK rules run
    final List<String> found = reported("cdk-public-bucket", source);

    // Then the open bucket is reported and the blocking one is not
    assertThat(found).containsExactly("4:3");
  }

  @Test
  @DisplayName("an open security group")
  void anOpenSecurityGroup() {
    // Given a security group open to 0.0.0.0/0 on a port, and one limited to a known range
    final String source =
        """
        import * as ec2 from 'aws-cdk-lib/aws-ec2';

        export function build(group: ec2.SecurityGroup) {
          group.addIngressRule(ec2.Peer.ipv4('0.0.0.0/0'), ec2.Port.tcp(22));
          group.addIngressRule(ec2.Peer.ipv4('10.0.0.0/16'), ec2.Port.tcp(22));
        }
        """;

    // When the CDK rules run
    final List<String> found = reported("cdk-open-security-group", source);

    // Then the open rule is reported and the known range is not
    assertThat(found).containsExactly("4:3");
  }

  @Test
  @DisplayName("a wildcard IAM policy")
  void aWildcardIamPolicy() {
    // Given a policy statement with actions ['*'] and resources ['*']
    final String source =
        """
        import * as iam from 'aws-cdk-lib/aws-iam';

        export const everything = new iam.PolicyStatement({
          actions: ['*'],
          resources: ['*'],
        });

        export const narrow = new iam.PolicyStatement({
          actions: ['s3:GetObject'],
          resources: ['arn:aws:s3:::reports/*'],
        });
        """;

    // When the CDK rules run
    final List<String> found = reported("cdk-wildcard-iam-policy", source);

    // Then the wildcard statement is reported and the specific one is not
    assertThat(found).containsExactly("3:27");
  }
}
