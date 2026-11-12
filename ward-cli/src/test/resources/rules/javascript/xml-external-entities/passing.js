import libxml from 'libxmljs';

export function parse(xml) {
  return libxml.parseXml(xml, {
    noent: false,
  });
}
