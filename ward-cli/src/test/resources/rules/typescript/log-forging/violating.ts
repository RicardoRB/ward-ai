export function direct(req: any, logger: any) {
  logger.info(req.query.user);
}

export function propagated(req: any, log: any) {
  const user = req.headers.user;
  const line = 'login by ' + user;
  log.warn(line);
}

// A value that another function of the file returns is followed.
export function crossFunction(req: any, logger: any) {
  const user = read(req);
  logger.info(user);
}

function read(req: any) {
  return req.query.user;
}
