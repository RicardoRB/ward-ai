from urllib.parse import urlencode, urljoin, urlparse

from django.urls import reverse
from flask import abort, redirect, request, url_for


def go():
    return redirect(url_for("home"))


def go_known():
    page = request.args.get("page")
    return redirect("/pages/" + str(int(page)))


def go_to_partner():
    back = request.args.get("back")
    return redirect("https://partner.example/start?" + urlencode({"back": back}))


def go_to_step():
    step = request.args.get("step")
    return redirect(reverse("wizard", query={"step": step}))


def referer_query(request):
    parsed = urlparse(request.headers.get("Referer", ""))
    if parsed.query:
        return "?" + parsed.query
    return ""


def go_with_query(request, token):
    url = reverse("checks", args=[token])
    url += referer_query(request)
    return redirect(url)


def go_with_literal_query():
    tail = request.args.get("tail")
    return redirect("/search?q=" + tail)


def go_with_f_query():
    tail = request.args.get("tail")
    return redirect(f"/search?q={tail}&page=2")


def go_with_chained_query():
    tail = request.args.get("tail")
    more = request.args.get("more")
    return redirect("/search?" + tail + "&" + more)


def relative_only(url):
    if not url:
        return False
    parsed = urlparse(url)
    if parsed.netloc:
        return False
    return parsed.path.startswith("/")


def same_host(target):
    own = urlparse(request.host_url)
    joined = urlparse(urljoin(request.host_url, target))
    return joined.scheme in ("http", "https") and own.netloc == joined.netloc


def is_trusted(target):
    return same_host(target) and same_host(target.replace("\\", "/"))


def go_guarded_by_relative_check():
    target = request.args.get("next")
    if target and relative_only(target):
        return redirect(target)
    return redirect("/")


def go_guarded_by_negated_check():
    target = request.args.get("next")
    if not relative_only(target):
        abort(400)
    return redirect(target)


def go_guarded_by_negated_check_in_a_chain():
    target = request.args.get("next")
    if target is None or not is_trusted(target):
        return redirect("/")
    return redirect(target)


def go_guarded_expression():
    if request.args.get("next") and is_trusted(request.args.get("next")):
        return redirect(request.args.get("next"))
    return redirect("/")


def go_guarded_else_branch():
    target = request.args.get("next")
    if not same_host(target):
        return redirect("/")
    else:
        return redirect(target)
