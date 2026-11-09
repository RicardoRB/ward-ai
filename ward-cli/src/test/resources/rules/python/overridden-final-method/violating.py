from typing import final


class A:
    @final
    def m(self):
        pass


class B(A):
    def m(self):
        pass
