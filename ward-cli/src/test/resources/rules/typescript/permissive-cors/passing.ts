export function setup(app: any, cors: any) {
  app.use(cors({ origin: 'https://app.example.com' }));
}

export function header(res: any) {
  res.setHeader('Access-Control-Allow-Origin', 'https://app.example.com');
}
