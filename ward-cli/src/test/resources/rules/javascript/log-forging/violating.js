export function direct(req, logger) {
  logger.info(req.query.user);
}

export function propagated(req, log) {
  const user = req.headers.user;
  const line = 'login by ' + user;
  log.warn(line);
}
