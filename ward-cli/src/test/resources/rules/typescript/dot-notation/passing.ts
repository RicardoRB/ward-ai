const config = {};
const first = config.timeout;
const key = 'x';
const value = config[key];
const other = config['a-b'];

export const identity = (value: unknown) => value as Settings['checkIdentity'];

export const checker = (value: unknown) =>
  value as tls.ConnectionOptions['checkServerIdentity'];
