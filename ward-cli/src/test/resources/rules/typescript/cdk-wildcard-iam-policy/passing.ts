import * as iam from 'aws-cdk-lib/aws-iam';

export const narrow = new iam.PolicyStatement({
  actions: ['s3:GetObject'],
  resources: ['arn:aws:s3:::reports/*'],
});

export const noResourceLimit = new iam.PolicyStatement({
  actions: ['ec2:DescribeInstances'],
  resources: ['*'],
});

export const serviceWide = new iam.PolicyStatement({
  actions: ['s3:*'],
  resources: ['arn:aws:s3:::reports'],
});

export const denied = new iam.PolicyStatement({
  effect: iam.Effect.DENY,
  actions: ['*'],
  resources: ['*'],
});
