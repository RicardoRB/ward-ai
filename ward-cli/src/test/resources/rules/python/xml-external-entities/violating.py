from lxml import etree
import xml.sax


def parser():
    return etree.XMLParser(resolve_entities=True)


def sax(reader):
    reader.setFeature(xml.sax.handler.feature_external_ges, True)
