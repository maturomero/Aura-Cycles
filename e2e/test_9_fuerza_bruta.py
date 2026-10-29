"""Va ultimo: bloquea la IP 5 minutos."""
from conftest import login


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
