# ADR-007 · Modelo de IA y AIGateway (Facade + Adapter)

**Estado:** Aceptada · 06/11/2026
**Patrones:** Facade (AIGateway) y Adapter (ClienteLLM / ClienteLLMHttp), GoF estructurales

## Contexto
Queremos que una IA sugiera cómo moderar cada reseña. El modelo es un servicio externo que puede tardar, caerse, contestar cualquier cosa o ser engañado por el texto de la reseña.

## Decisión
Un modelo chico y barato por API (Claude Haiku 5.5, configurable con IA_MODELO), temperatura 0 y hasta 200 tokens de salida. Todo pasa por `AIGateway` (Facade): tapa emails y teléfonos, corta el texto a 2000 caracteres, valida la respuesta contra el contrato v1, baja a REVISAR si la confianza es menor a 0,6 y ante cualquier error devuelve el fallback (REVISAR / SIN_IA). La llamada HTTP está detrás de la interfaz `ClienteLLM`; `ClienteLLMHttp` es el Adapter a la API del proveedor, con timeout de 5 s. Viene apagada (IA_MODERACION=false).

## Consecuencias
+ El resto del sistema no sabe qué modelo hay atrás: cambiarlo es otro Adapter.
+ Los tests usan un ClienteLLM falso, sin gastar llamadas (AIGatewayTest).
+ Si la IA falla, el moderador trabaja como siempre.
− Costo por llamada y dependencia de un proveedor; se mide y se puede apagar sin desplegar.

## Descartadas
- Un modelo grande: más caro y lento para clasificar texto corto.
- Un modelo propio entrenado: no tenemos datos ni tiempo.
- Llamar al proveedor directo desde ServicioDeResenas: no habría un lugar único para las defensas.
