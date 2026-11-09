class A:
    def __getnewargs_ex__(self):
        return ((), {})
