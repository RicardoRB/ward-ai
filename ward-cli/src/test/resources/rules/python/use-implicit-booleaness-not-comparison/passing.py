items = []
if items:
    pass


def read(request, label: str | None):
    value = request.POST.get("name")
    if value == "":
        return None
    if label == "":
        return None
    return value


def update(spec):
    if spec.channels is None:
        return None
    elif spec.channels == "":
        return []
    return spec.channels
