export function direct(req: any, logger: any) {
  logger.info(req.query.user);
}

export function propagated(req: any, log: any) {
  const user = req.headers.user;
  const line = 'login by ' + user;
  log.warn(line);
}
