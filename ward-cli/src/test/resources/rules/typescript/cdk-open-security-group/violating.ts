import * as ec2 from 'aws-cdk-lib/aws-ec2';

export function build(group: ec2.SecurityGroup, web: ec2.Connections) {
  group.addIngressRule(ec2.Peer.anyIpv4(), ec2.Port.tcp(22));
  group.addIngressRule(ec2.Peer.ipv4('0.0.0.0/0'), ec2.Port.allTraffic());
  group.addIngressRule(ec2.Peer.ipv6('::/0'), ec2.Port.tcp(3306), 'database');
  web.allowFromAnyIpv4(ec2.Port.tcp(8080));
}
