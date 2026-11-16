class A:
    def m(self):
        pass

    @staticmethod
    def s():
        pass

    @cached("slow")
    @staticmethod
    def t():
        pass
