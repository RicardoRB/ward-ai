import unittest


class Service:
    def used_method(self):
        return 1

    def run(self):
        return self.used_method()

    def dynamic(self):
        return 4

    @staticmethod
    def helper():
        return 2


class ServiceTest(unittest.TestCase):
    def setUp(self):
        self.service = Service()


Service.helper()
Service().run()
getattr(Service(), "dynamic")()


from marshmallow import fields


class ValueField(fields.Field):
    def _deserialize(self, value, attr, data, **kwargs):
        return value


from html.parser import HTMLParser


class TagCounter(HTMLParser):
    def handle_starttag(self, tag, attrs):
        self.count = getattr(self, "count", 0) + 1

    def handle_data(self, data):
        self.last = data


from statsd.client.base import StatsClientBase


class QuietClient(StatsClientBase):
    def _send(self, data):
        return None

    def pipeline(self):
        return None
