"""Eje D: lo que la catedra prueba en vivo."""
import ssl
import urllib.request

from conftest import APP, login, sql

RAIZ = APP.rsplit("/", 1)[0]

_ctx = ssl.create_default_context()
_ctx.check_hostname = False
_ctx.verify_mode = ssl.CERT_NONE


def _get(url):
    o = urllib.request.build_opener(urllib.request.ProxyHandler({}), urllib.request.HTTPSHandler(context=_ctx))
    try:
        r = o.open(url, timeout=10)
        return r.status, r.headers, r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.headers, e.read().decode("utf-8", "replace")


HEADERS = ["Content-Security-Policy", "Strict-Transport-Security", "X-Content-Type-Options", "X-Frame-Options"]


def test_headers_tambien_en_el_login_del_contenedor_y_en_los_errores():
    # pagina protegida sin sesion: el contenedor muestra el login por FORWARD; 403 y 404 salen por ERROR
    for url in [f"{APP}/compras.xhtml", f"{APP}/moderacion.xhtml", f"{APP}/no-existe.xhtml"]:
        st, h, _ = _get(url)
        assert all(x in h for x in HEADERS), (url, st)


def test_headers_y_cookie_de_sesion():
    st, h, _ = _get(f"{APP}/login.xhtml")
    for header in HEADERS:
        assert header in h
    assert "script-src 'self'" in h["Content-Security-Policy"]
    assert "unsafe-inline" not in h["Content-Security-Policy"]
    cookie = h["Set-Cookie"]
    assert "secure" in cookie and "HttpOnly" in cookie and "SameSite=Lax" in cookie


def test_errores_sin_detalles_internos():
    st, _, body = _get(f"{APP}/no-existe.xhtml")
    assert st == 404 and "No pudimos mostrar" in body
    assert "Exception" not in body and "at org." not in body
    assert _get(f"{APP}/j_security_check")[0] == 404


class _SinRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *a, **k):
        return None


def test_la_raiz_va_a_la_app_y_no_muestra_wildfly():
    o = urllib.request.build_opener(urllib.request.ProxyHandler({}), urllib.request.HTTPSHandler(context=_ctx), _SinRedirect())
    try:
        o.open(RAIZ + "/", timeout=10)
        assert False, "esperaba un redirect"
    except urllib.error.HTTPError as e:
        assert e.code in (302, 303) and e.headers["Location"].endswith("/repuestos/")
    assert "wildfly" not in _get(RAIZ + "/noredirect.html")[2].lower()


def test_sin_violaciones_de_csp(abrir):
    errores = []
    for email, paginas in [("ana@mail.com", ["productos.xhtml", "producto.xhtml?productoId=1", "compras.xhtml", "perfil.xhtml"]),
                           ("mod@mail.com", ["moderacion.xhtml"])]:
        page = abrir()
        page.on("console", lambda m: errores.append(m.text) if "Content Security Policy" in m.text else None)
        login(page, email)
        for p in paginas:
            page.goto(f"{APP}/{p}")
        # las fuentes salen de la app, no de Google: tienen que cargar con la CSP estricta
        assert page.evaluate("document.fonts.ready.then(() => document.fonts.check('14px \"IBM Plex Sans\"'))")
    assert not errores, errores


def test_vista_vencida_vuelve_a_la_pagina_con_aviso(abrir):
    page = abrir()
    estados = []
    page.on("response", lambda r: estados.append(r.status) if r.request.method == "POST" else None)
    page.goto(f"{APP}/login.xhtml")
    page.evaluate("document.querySelector(\"input[name='jakarta.faces.ViewState']\").value='-123:-456'")
    page.fill("input[id$='email']", "ana@mail.com")
    page.fill("input[id$='pass']", "Clave1234")
    page.click("input[value='Ingresar']")
    page.wait_for_load_state()
    assert 500 not in estados and page.url.endswith("/login.xhtml")
    assert "venci" in page.content() and "Exception" not in page.content()


def test_un_cliente_no_entra_al_panel_del_moderador(abrir):
    carlos = abrir("carlos@mail.com")
    r = carlos.goto(f"{APP}/moderacion.xhtml")
    assert r.status == 403 and "Devoluciones pendientes" not in carlos.content()


def test_xss_y_mass_assignment_en_el_registro(abrir):
    page = abrir()
    alertas = []
    page.on("dialog", lambda d: (alertas.append(d.message), d.dismiss()))
    page.goto(f"{APP}/login.xhtml")
    page.click("input[value='Crear una cuenta']")
    page.wait_for_load_state()
    page.fill("input[id$='nom']", "<script>alert(1)</script>")
    page.fill("input[id$='mailN']", "e2e-xss@mail.com")
    page.fill("input[id$='passN']", "Clave1234")
    page.evaluate("""() => { const f = document.querySelector("input[id$='mailN']").form;
        const i = document.createElement('input'); i.type='hidden'; i.name='rol'; i.value='MODERADOR'; f.appendChild(i); }""")
    page.click("input[value='Crear cuenta']")
    page.wait_for_load_state()
    assert "&lt;script&gt;" in page.content() and not alertas
    assert sql("select rol from usuario where email='e2e-xss@mail.com'") == "CLIENTE"


def test_inyeccion_sql_en_el_buscador(abrir):
    page = abrir("ana@mail.com")
    page.goto(f"{APP}/productos.xhtml")
    page.fill("input[type='text']", "' OR '1'='1")
    page.click("input[value='Buscar']")
    page.wait_for_load_state()
    assert "No hay repuestos que coincidan" in page.content()
