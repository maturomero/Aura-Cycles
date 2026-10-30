"""HU Comprar con Mercado Pago (AUR-55): camino feliz, rechazo, Mercado Pago caido y URL manipulada."""
from conftest import APP, MP, comprar, modo_mp, stock, ultima_venta

LUCIA = 3  # usuario_id


def test_compra_aprobada_queda_pagada_y_descuenta_stock(abrir):
    page = abrir("lucia@mail.com")
    antes = stock(3)
    comprar(page, 3)
    assert page.url.startswith(MP + "/checkout")
    page.click("text=Pagar (aprobado)")
    page.wait_for_load_state()
    assert "Pago acreditado" in page.content()
    _, estado, pago = ultima_venta(LUCIA)
    assert estado == "PAGADA" and pago != "-"
    assert stock(3) == antes - 1
    page.reload()
    assert ultima_venta(LUCIA)[1] == "PAGADA" and stock(3) == antes - 1


def test_compra_rechazada_devuelve_el_stock(abrir):
    page = abrir("lucia@mail.com")
    antes = stock(3)
    comprar(page, 3)
    page.click("text=Pagar (rechazado)")
    page.wait_for_load_state()
    assert "rechazado" in page.content()
    assert ultima_venta(LUCIA)[1] == "RECHAZADA"
    assert stock(3) == antes


def test_mercado_pago_caido_reserva_y_se_paga_despues(abrir):
    page = abrir("lucia@mail.com")
    antes = stock(3)
    modo_mp("lento")
    comprar(page, 3)
    assert "Mercado Pago no responde" in page.content()
    assert ultima_venta(LUCIA)[1] == "PENDIENTE_PAGO" and stock(3) == antes - 1
    modo_mp("normal")
    page.goto(f"{APP}/compras.xhtml")
    page.click("input[value='Pagar'] >> nth=0")
    page.wait_for_load_state()
    page.click("text=Pagar (aprobado)")
    page.wait_for_load_state()
    assert ultima_venta(LUCIA)[1] == "PAGADA"


def test_la_url_de_vuelta_no_se_cree(abrir):
    page = abrir("lucia@mail.com")
    comprar(page, 3)
    page.click("text=Volver a la tienda sin pagar")
    page.wait_for_load_state()
    venta = ultima_venta(LUCIA)[0]
    page.goto(f"{APP}/pago.xhtml?venta={venta}&status=approved&collection_status=approved")
    assert ultima_venta(LUCIA)[1] == "PENDIENTE_PAGO"
    page.goto(f"{APP}/pago.xhtml?venta={venta}&payment_id=1000000001&status=approved")
    assert ultima_venta(LUCIA)[1] == "PENDIENTE_PAGO"
