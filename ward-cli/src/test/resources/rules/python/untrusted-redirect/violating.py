from urllib.parse import urlparse
from flask import redirect, request
from django.http import HttpResponseRedirect


def go():
    target = request.args.get("next")
    return redirect(target)


def go_django(request):
    return HttpResponseRedirect(request.GET["next"])


def go_before_query():
    target = request.args.get("next")
    return redirect(target + "?done=1")


def go_f_before_query():
    target = request.args.get("next")
    return redirect(f"{target}?done=1")


def go_through_helper(request):
    return redirect(host_and_query(request))


def host_and_query(request):
    return request.args.get("next") + "?done=1"


def go_plain_tail(request):
    tail = request.args.get("tail")
    return redirect(tail + "/more?x=1")


def accepts_anything(url):
    return url.startswith("/")


def returns_true_early(url):
    if url.startswith("/"):
        return True
    parsed = urlparse(url)
    if parsed.netloc:
        return False
    return True


def go_guarded_by_a_function_that_checks_nothing_about_the_host():
    target = request.args.get("next")
    if target and accepts_anything(target):
        return redirect(target)
    return redirect("/")


def go_guarded_by_a_function_that_returns_true_early():
    target = request.args.get("next")
    if returns_true_early(target):
        return redirect(target)
    return redirect("/")


def go_when_the_guard_does_not_exit():
    target = request.args.get("next")
    if not relative_only(target):
        print("rejected")
    return redirect(target)


def go_with_another_value_than_the_checked_one():
    target = request.args.get("next")
    other = request.args.get("other")
    if relative_only(target):
        return redirect(other)
    return redirect("/")


def go_after_the_value_is_changed():
    target = request.args.get("next")
    if relative_only(target):
        target = request.args.get("other")
        return redirect(target)
    return redirect("/")


def relative_only(url):
    parsed = urlparse(url)
    if parsed.netloc:
        return False
    return parsed.path.startswith("/")

