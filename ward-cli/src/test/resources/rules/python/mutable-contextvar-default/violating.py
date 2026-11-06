import contextvars
v = contextvars.ContextVar("v", default=[])
