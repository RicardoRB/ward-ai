export function numeric(req, logger) {
  const attempt = Number(req.query.attempt);
  logger.info('attempt ' + attempt);
}

export function constant(logger) {
  logger.info('started');
}
