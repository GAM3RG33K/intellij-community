class Holder:
    def __init__(self, x):
        self.x = x

    def get(self):
        return self.x


ex<caret>pr = Holder(42).get()