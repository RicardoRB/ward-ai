export function setup(app, cors) {
  app.use(cors({ origin: 'https://app.example.com' }));
}

export function header(res) {
  res.setHeader('Access-Control-Allow-Origin', 'https://app.example.com');
}
