class A(B):
    def f(self):
        super(A, self).f()
        super(B, self).f()
        super().f()
