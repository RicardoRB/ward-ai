class A(B):
    def f(self):
        super(C, self).f()
        super(type(self), self).f()
        super(self.__class__, self).f()
