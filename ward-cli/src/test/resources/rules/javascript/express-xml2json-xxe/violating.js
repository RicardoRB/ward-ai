import xml2json from 'xml2json';
export function handle(req) {
  return xml2json.toJson(req.body.xml);
}
