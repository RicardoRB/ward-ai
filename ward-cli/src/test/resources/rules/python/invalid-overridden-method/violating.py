class A:
    @property
    def x(self):
        return 1


class B(A):
    def x(self):
        return 2
