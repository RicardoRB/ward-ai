import * as s3 from 'aws-cdk-lib/aws-s3';
import * as sqs from 'aws-cdk-lib/aws-sqs';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as rds from 'aws-cdk-lib/aws-rds';

export function build(scope: any, vpc: any, engine: any) {
  new s3.Bucket(scope, 'Managed', { encryption: s3.BucketEncryption.S3_MANAGED });
  new sqs.Queue(scope, 'Jobs', { encryption: sqs.QueueEncryption.SQS_MANAGED });
  new ec2.Volume(scope, 'Disk', { availabilityZone: 'a', size: 8, encrypted: true });
  new rds.DatabaseInstance(scope, 'Db', { engine, vpc, storageEncrypted: true });
  new s3.Bucket(scope, 'Default');
  return { encrypted: false };
}
