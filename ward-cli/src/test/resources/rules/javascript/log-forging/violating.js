export function direct(req, logger) {
  logger.info(req.query.user);
}

export function propagated(req, log) {
  const user = req.headers.user;
  const line = 'login by ' + user;
  log.warn(line);
}

// A value that another function of the file returns is followed.
export function crossFunction(req, logger) {
  const user = read(req);
  logger.info(user);
}

function read(req) {
  return req.query.user;
}
