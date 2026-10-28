# ADR-002 · Strategy para calcular el reembolso

**Estado:** Aceptada · 18/09/2026
**Patrón:** GoF de comportamiento

## Contexto
El monto a devolver depende del motivo: si el repuesto vino fallado o distinto se devuelve todo, si el cliente se arrepintió se retiene el 10% del envío. Era un if sobre el motivo dentro de ServicioDeReembolsos, y es esperable que aparezcan reglas nuevas (por ejemplo, una devolución parcial si el repuesto se usó).

## Decisión
Interfaz `PoliticaDeReembolso` con `aplicaA(motivo)` y `calcular(importe)`. Dos implementaciones: `ReembolsoTotal` y `ReembolsoConRetencion`. ServicioDeReembolsos recibe todas por CDI (`Instance<PoliticaDeReembolso>`) y usa la que aplica.

## Consecuencias
+ Una regla nueva es una clase nueva; ServicioDeReembolsos no se toca (OCP).
+ Cada regla se prueba sola.
− Hay que cuidar que para cada motivo aplique una sola política: si hay cero o dos, el servicio corta con error.

## Descartadas
- if/switch por motivo: cada regla nueva obliga a editar el servicio.
- Porcentajes en una tabla de la base: sirve para cambiar números, no para reglas con lógica distinta.
