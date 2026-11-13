import libxml from 'libxmljs';

export function parse(xml: any) {
  return libxml.parseXml(xml, {
    noent: false,
  });
}
