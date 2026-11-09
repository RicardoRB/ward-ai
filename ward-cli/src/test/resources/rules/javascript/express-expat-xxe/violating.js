export function handle(req, parser) {
  parser.parse(req.body.xml);
}
