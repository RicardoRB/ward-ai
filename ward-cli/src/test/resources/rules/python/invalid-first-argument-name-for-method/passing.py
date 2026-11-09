class A:
    def m(self):
        pass

    @staticmethod
    def s(a):
        pass


class M(type):
    def m(cls):
        pass
