import https from 'https';

export const agent = new https.Agent({
  rejectUnauthorized: false,
});

export function global() {
  process.env.NODE_TLS_REJECT_UNAUTHORIZED = '0';
}
