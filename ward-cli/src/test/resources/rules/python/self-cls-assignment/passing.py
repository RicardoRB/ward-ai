class Box:
    def reset(self):
        value = None
        return value


def get_class(class_id):
    cls = REGISTRY.get(class_id)
    return cls
