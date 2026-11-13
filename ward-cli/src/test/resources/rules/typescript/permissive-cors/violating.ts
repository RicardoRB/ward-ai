export function setup(app: any, cors: any) {
  app.use(cors());
}

export function wide(app: any, cors: any) {
  app.use(cors({ origin: '*' }));
}

export function header(res: any) {
  res.setHeader('Access-Control-Allow-Origin', '*');
}
