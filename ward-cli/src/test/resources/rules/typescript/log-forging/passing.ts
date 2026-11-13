export function numeric(req: any, logger: any) {
  const attempt = Number(req.query.attempt);
  logger.info('attempt ' + attempt);
}

export function constant(logger: any) {
  logger.info('started');
}
