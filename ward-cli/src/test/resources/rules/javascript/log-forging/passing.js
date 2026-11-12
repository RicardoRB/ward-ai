export function numeric(req, logger) {
  const attempt = Number(req.query.attempt);
  logger.info('attempt ' + attempt);
}

export function constant(logger) {
  logger.info('started');
}

// The limit: a value that leaves the function is not followed.
export function crossFunction(req, logger) {
  const user = read(req);
  logger.info(user);
}

function read(req) {
  return req.query.user;
}
