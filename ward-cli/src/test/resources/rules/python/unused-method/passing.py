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
