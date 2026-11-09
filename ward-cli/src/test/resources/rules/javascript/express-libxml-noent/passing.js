export function handle(req, libxml) {
  libxml.parseXml('<a/>', { noent: false });
}
