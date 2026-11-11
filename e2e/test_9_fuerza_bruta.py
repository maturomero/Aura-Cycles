"""Va ultimo: bloquea la IP 5 minutos."""
from conftest import compose, login


def test_despues_de_cinco_fallos_responde_429(abrir):
    page = abrir()
    estados = []
    page.on("response", lambda r: estados.append(r.status) if r.url.endswith("login.xhtml") and r.request.method == "POST" else None)
    for i in range(5):
        login(page, "lucia@mail.com", f"mala{i}")
    login(page, "lucia@mail.com")
    assert 429 in estados
    assert "Demasiados intentos" in page.content()
    assert "productos.xhtml" not in page.url


# La API usa el mismo limitador: con la IP bloqueada, el login por REST responde 429 aunque la clave sea la buena
def test_la_api_tambien_responde_429():
    import test_7_api
    st, h, cuerpo = test_7_api.pedir("POST", "/auth/login", cuerpo={"email": "lucia@mail.com", "password": "Clave1234"})
    assert st == 429 and int(h["Retry-After"]) > 0
    test_7_api.es_problema(h, cuerpo, 429, "demasiados-intentos")
    assert "token" not in cuerpo


# SEC-09: despues de toda la suite, el log del servidor no tiene emails completos, claves ni hashes
def test_el_log_no_tiene_emails_claves_ni_hashes():
    log = compose("logs", "--no-color", "app")
    texto = log.stdout + log.stderr
    assert texto, "no se pudo leer el log"
    emails = [e for e in ["ana@mail.com", "carlos@mail.com", "lucia@mail.com", "javier@mail.com", "mod@mail.com"] if e in texto]
    assert not emails, emails
    assert "Clave1234" not in texto and "$2a$12$" not in texto
