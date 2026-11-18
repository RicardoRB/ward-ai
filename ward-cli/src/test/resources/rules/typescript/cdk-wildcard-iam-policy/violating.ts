import * as iam from 'aws-cdk-lib/aws-iam';

export const everything = new iam.PolicyStatement({
  actions: ['*'],
  resources: ['*'],
});

export const reordered = new iam.PolicyStatement({
  resources: ["*"],
  effect: iam.Effect.ALLOW,
  actions: ['s3:GetObject', '*'],
});
