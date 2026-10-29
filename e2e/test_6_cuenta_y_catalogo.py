"""HU-03 (perfil) y HU-04 (catalogo): lo que no cubren los otros E2E."""
from conftest import APP, sql

LUCIA = "lucia@mail.com"


def test_perfil_guarda_el_nombre_y_rechaza_cambios_de_clave_mal_hechos(abrir):
    page = abrir(LUCIA)
    page.goto(f"{APP}/perfil.xhtml")
    original = page.input_value("input[id$='nom']")
    page.fill("input[id$='nom']", "Lucia E2E")
    page.click("input[value='Guardar cambios']")
    page.wait_for_load_state()
    assert "Guardamos tus datos." in page.content()
    assert sql(f"select nombre from usuario where email='{LUCIA}'") == "Lucia E2E"

    page.fill("input[id$='actual']", "Clave1234")
    page.fill("input[id$='nueva']", "OtraClave123")
    page.fill("input[id$='rep']", "OtraClave124")
    page.click("input[value='Cambiar contraseña']")
    page.wait_for_load_state()
    assert "Las contraseñas nuevas no coinciden." in page.content()

    page.fill("input[id$='actual']", "NoEsLaClave1")
    page.fill("input[id$='nueva']", "OtraClave123")
    page.fill("input[id$='rep']", "OtraClave123")
    page.click("input[value='Cambiar contraseña']")
    page.wait_for_load_state()
    assert "La contrasena actual no coincide." in page.content()

    page.fill("input[id$='nom']", original)
    page.click("input[value='Guardar cambios']")
    page.wait_for_load_state()


def test_sin_stock_no_se_puede_comprar(abrir):
    antes = sql("select stock from producto where id=1")
    sql("update producto set stock=0 where id=1")
    try:
        page = abrir("ana@mail.com")
        page.goto(f"{APP}/productos.xhtml")
        tarjeta = page.locator(".producto-card").filter(has_text="Cubierta MTB 29").first
        assert "Sin stock" in tarjeta.inner_text()
        assert tarjeta.locator("input[value='Comprar']").is_disabled()
        page.goto(f"{APP}/producto.xhtml?productoId=1")
        assert page.locator("input[value='Comprar']").is_disabled()
    finally:
        sql(f"update producto set stock={antes} where id=1")


def test_un_repuesto_sin_resenas_aprobadas_lo_dice(abrir):
    sin = sql("select min(id) from producto where id not in (select producto_id from resena where estado='APROBADA')")
    page = abrir("ana@mail.com")
    page.goto(f"{APP}/producto.xhtml?productoId={sin}")
    assert "Todavía no hay reseñas publicadas" in page.content()
