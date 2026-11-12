import https from 'https';

export const agent = new https.Agent({
  rejectUnauthorized: true,
});

export function global() {
  process.env.NODE_TLS_REJECT_UNAUTHORIZED = '1';
}
