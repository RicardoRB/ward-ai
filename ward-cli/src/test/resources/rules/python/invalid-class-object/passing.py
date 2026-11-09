class A:
    pass


class B(A):
    pass


obj = A()
obj.__class__ = B
