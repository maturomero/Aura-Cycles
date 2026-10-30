"""HU Pedir una devolucion (AUR-32, AUR-39, SEC-05): garantia por SOAP, reembolso por Mercado Pago y compra ajena."""
import json
import urllib.request

from conftest import APP, MP, aprobar, comprar, modo_soap, pedir_devolucion, programados, sql, esperar, ultima_venta

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


def test_con_el_proveedor_caido_queda_validandose_y_va_a_la_cola(abrir):
    carlos = abrir("carlos@mail.com")
    venta = _compra_pagada(carlos, 7)
    en_cola = programados("cola.validaciones")
    modo_soap("error")
    codigo = pedir_devolucion(carlos, venta, "NO_COMPATIBLE", "")
    html = carlos.content()
    assert "Validando la garant" in html and "ORA-" not in html
    assert sql(f"select estado from devolucion where id={int(codigo[4:])}") == "PENDIENTE_VALIDACION"
    # El reintento queda en la cola punto a punto con 2 minutos de espera
    assert programados("cola.validaciones") == en_cola + 1


def test_no_se_puede_devolver_una_compra_ajena(abrir):
    ana = abrir("ana@mail.com")
    ana.goto(f"{APP}/devolucion.xhtml?ventaId=3")
    html = ana.content()
    assert "No pudimos abrir la solicitud" in html and "La compra no existe" in html
    assert "Carlos" not in html and "Cadena" not in html
    assert ana.locator("input[value='Enviar solicitud']").count() == 0


# Equivale al 409 de REST: dos moderadores aprueban la misma devolucion a la vez; uno solo pasa y hay un solo reembolso
def test_dos_moderadores_aprueban_la_misma_devolucion_a_la_vez(abrir):
    carlos = abrir("carlos@mail.com")
    venta = _compra_pagada(carlos, 2)
    codigo = pedir_devolucion(carlos, venta, "ARREPENTIMIENTO", "")
    dev = int(codigo[4:])

    uno, otro = abrir("mod@mail.com"), abrir("mod@mail.com")
    for m in (uno, otro):
        m.goto(f"{APP}/moderacion.xhtml")
        m.click("input[value^='Devoluciones pendientes']")
        m.wait_for_load_state()
    with uno.expect_navigation(), otro.expect_navigation():
        uno.locator("tr", has_text=codigo).locator("input[value='Aprobar']").click(no_wait_after=True)
        otro.locator("tr", has_text=codigo).locator("input[value='Aprobar']").click(no_wait_after=True)

    resultados = sorted("ok" if "Devolución aprobada" in m.content() else "no" if "No se puede aprobar" in m.content() else "?"
                        for m in (uno, otro))
    assert resultados == ["no", "ok"]
    assert esperar(lambda: sql(f"select estado from devolucion where id={dev}") == "REEMBOLSADA", 30)
    pago = sql(f"select mp_pago_id from venta where id={venta}")
    pedido = urllib.request.Request(f"{MP}/v1/payments/{pago}", headers={"Authorization": "Bearer TEST-e2e"})
    with urllib.request.build_opener(urllib.request.ProxyHandler({})).open(pedido) as r:
        assert str(json.load(r)["transaction_amount_refunded"]) in ("52830", "52830.0", "52830.00")
