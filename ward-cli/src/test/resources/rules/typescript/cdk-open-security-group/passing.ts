import * as ec2 from 'aws-cdk-lib/aws-ec2';

export function build(group: ec2.SecurityGroup, other: ec2.SecurityGroup) {
  group.addIngressRule(ec2.Peer.ipv4('10.0.0.0/16'), ec2.Port.tcp(22));
  group.addIngressRule(ec2.Peer.ipv6('2001:db8::/32'), ec2.Port.tcp(443));
  group.addIngressRule(ec2.Peer.securityGroupId(other.securityGroupId), ec2.Port.allTcp());
  group.addEgressRule(ec2.Peer.anyIpv4(), ec2.Port.tcp(443));
}
