export function setup(app, cors) {
  app.use(cors());
}

export function wide(app, cors) {
  app.use(cors({ origin: '*' }));
}

export function header(res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
}
