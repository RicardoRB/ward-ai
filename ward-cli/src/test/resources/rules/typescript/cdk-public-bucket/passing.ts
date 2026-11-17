import { Bucket, BlockPublicAccess, BucketAccessControl } from 'aws-cdk-lib/aws-s3';

export function build(scope: any, reader: any) {
  const closed = new Bucket(scope, 'Closed', { blockPublicAccess: BlockPublicAccess.BLOCK_ALL });
  new Bucket(scope, 'Private', { accessControl: BucketAccessControl.PRIVATE });
  new Bucket(scope, 'Explicit', { publicReadAccess: false });
  new BlockPublicAccess({ blockPublicAcls: true, blockPublicPolicy: true });
  closed.grantRead(reader);
}
