"""Eje A: accesibilidad (axe, WCAG 2 A y AA) y responsive en 360, 768 y 1280 px."""
import os

import pytest
from axe_playwright_python.sync_playwright import Axe

from conftest import APP, login

ANCHOS = {"celular": (360, 740), "tablet": (768, 1024), "escritorio": (1280, 800)}
PAGINAS = [
    (None, "login.xhtml"),
    ("ana@mail.com", "productos.xhtml"),
    ("ana@mail.com", "producto.xhtml?productoId=1"),
    ("ana@mail.com", "compras.xhtml"),
    ("ana@mail.com", "perfil.xhtml"),
    ("mod@mail.com", "moderacion.xhtml"),
]
CAPTURAS = os.path.join(os.path.dirname(__file__), "capturas")
axe = Axe()


@pytest.mark.parametrize("ancho", ANCHOS)
def test_sin_problemas_graves_de_accesibilidad_ni_scroll_horizontal(navegador, ancho):
    os.makedirs(CAPTURAS, exist_ok=True)
    w, h = ANCHOS[ancho]
    problemas = []
    sesiones = {}
    for email, pagina in PAGINAS:
        if email not in sesiones:
            ctx = navegador.new_context(ignore_https_errors=True, viewport={"width": w, "height": h})
            sesiones[email] = ctx.new_page()
            if email:
                login(sesiones[email], email)
        page = sesiones[email]
        page.goto(f"{APP}/{pagina}")
        page.wait_for_load_state()
        page.screenshot(path=os.path.join(CAPTURAS, f"{ancho}-{pagina.split('.')[0]}.png"), full_page=True)
        r = axe.run(page, options={"runOnly": {"type": "tag", "values": ["wcag2a", "wcag2aa"]}})
        for v in r.response["violations"]:
            if v["impact"] in ("serious", "critical"):
                problemas.append(f"{pagina} [{v['impact']}] {v['id']}: {len(v['nodes'])} elementos")
        if page.evaluate("document.documentElement.scrollWidth > window.innerWidth + 1"):
            problemas.append(f"{pagina}: scroll horizontal a {w} px")
    for p in sesiones.values():
        p.context.close()
    assert not problemas, problemas
