import { Bucket, BlockPublicAccess, BucketAccessControl } from 'aws-cdk-lib/aws-s3';

export function build(scope: any, site: Bucket) {
  new Bucket(scope, 'Open', { publicReadAccess: true });
  new Bucket(scope, 'Acl', { accessControl: BucketAccessControl.PUBLIC_READ_WRITE });
  const relaxed = new BlockPublicAccess({ blockPublicPolicy: false, blockPublicAcls: true });
  site.grantPublicAccess();
  return relaxed;
}
