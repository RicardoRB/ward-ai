export function constant(res: any) {
  res.setHeader('X-Origin', 'internal');
}

export function numeric(req: any, res: any) {
  const version = Number(req.query.version);
  res.setHeader('X-Version', 'v' + version);
}

const pageOf = (target: string): number => Number(new URLSearchParams(target.split('?', 2)[1]).get('page')) || 1;

export function pagedLink(req: any, res: any, base: string) {
  const page = pageOf(req.url);
  res.setHeader('link', `<${base}/?page=${page + 1}>; rel="next"`);
}

function pageNumber(target: string): number {
  return parseInt(target.split('=')[1], 10);
}

export function pagedLinkFunction(req: any, res: any, base: string) {
  const page = pageNumber(req.url);
  res.setHeader('link', `<${base}/?page=${page + 1}>; rel="next"`);
}

export function nestedCallback(app: any, base: string) {
  app.get('/', (req: any, res: any) => {
    res.setHeader('link', `<${base}/?page=${pageOf(req.url) + 1}>; rel="next"`);
  });
}
