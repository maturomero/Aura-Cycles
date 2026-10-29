"""HU Pedir una devolucion (AUR-32, AUR-39, SEC-05): garantia por SOAP, reembolso por Mercado Pago y compra ajena."""
from conftest import APP, aprobar, comprar, modo_soap, pedir_devolucion, sql, esperar, ultima_venta

CARLOS = 2


def _compra_pagada(page, producto_id):
    comprar(page, producto_id)
    page.click("text=Pagar (aprobado)")
    page.wait_for_load_state()
    return ultima_venta(CARLOS)[0]


def test_devolucion_validada_aprobada_y_reembolsada(abrir):
    carlos = abrir("carlos@mail.com")
    venta = _compra_pagada(carlos, 2)
    codigo = pedir_devolucion(carlos, venta, "ARREPENTIMIENTO", "")
    assert codigo and "Pendiente de revisi" in carlos.content()
    dev = int(codigo[4:])
    assert sql(f"select rma from devolucion where id={dev}").startswith("RMA-")

    mod = abrir("mod@mail.com")
    aprobar(mod, codigo)
    assert esperar(lambda: sql(f"select estado from devolucion where id={dev}") == "REEMBOLSADA", 30)
    assert sql(f"select monto_reembolso from devolucion where id={dev}") == "52830.00"
    assert sql(f"select mp_reembolso_id is not null from devolucion where id={dev}") == "1"


def test_un_consumible_no_tiene_garantia(abrir):
    carlos = abrir("carlos@mail.com")
    venta = _compra_pagada(carlos, 4)
    assert pedir_devolucion(carlos, venta, "DEFECTUOSO") is None
    html = carlos.content()
    assert "repuesto de desgaste" in html and "garantiaNoValida" not in html
    assert sql(f"select count(*) from devolucion where venta_id={venta}") == "0"


def test_con_el_proveedor_caido_queda_validandose(abrir):
    carlos = abrir("carlos@mail.com")
    venta = _compra_pagada(carlos, 7)
    modo_soap("error")
    codigo = pedir_devolucion(carlos, venta, "NO_COMPATIBLE", "")
    html = carlos.content()
    assert "Validando la garant" in html and "ORA-" not in html
    assert sql(f"select estado from devolucion where id={int(codigo[4:])}") == "PENDIENTE_VALIDACION"


def test_no_se_puede_devolver_una_compra_ajena(abrir):
    ana = abrir("ana@mail.com")
    ana.goto(f"{APP}/devolucion.xhtml?ventaId=3")
    html = ana.content()
    assert "No pudimos abrir la solicitud" in html and "La compra no existe" in html
    assert "Carlos" not in html and "Cadena" not in html
    assert ana.locator("input[value='Enviar solicitud']").count() == 0
