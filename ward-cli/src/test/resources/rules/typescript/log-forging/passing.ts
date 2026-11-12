export function numeric(req: any, logger: any) {
  const attempt = Number(req.query.attempt);
  logger.info('attempt ' + attempt);
}

export function constant(logger: any) {
  logger.info('started');
}

// The limit: a value that leaves the function is not followed.
export function crossFunction(req: any, logger: any) {
  const user = read(req);
  logger.info(user);
}

function read(req: any) {
  return req.query.user;
}
