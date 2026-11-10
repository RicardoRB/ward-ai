export function handle(req, libxml) {
  libxml.parseXml(req.body.xml, { noent: true });
}
