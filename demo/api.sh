#!/usr/bin/env bash
# Recorrido de la API REST para mostrar en vivo: JWT, roles, Idempotency-Key, ETag, 409 del State y Problem Details.
# Con la app levantada (docker compose up --build):   ./demo/api.sh
# Se puede correr 4 veces; despues ya no quedan compras de prueba sin devolver: docker compose down -v y levantar de nuevo.
API=${API_URL:-https://localhost:8443/repuestos/api/v1}
CLAVE=Clave1234
bien=0; mal=0
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT

# pedir <metodo> <ruta> [opciones de curl...]: deja el codigo en $codigo, los headers en $tmp/h y el cuerpo en $tmp/b
pedir() {
  local metodo=$1 ruta=$2; shift 2
  : > "$tmp/b"
  codigo=$(curl -sk -X "$metodo" "$API$ruta" -D "$tmp/h" -o "$tmp/b" -w '%{http_code}' "$@")
}

header() { grep -i "^$1:" "$tmp/h" | head -1 | sed 's/^[^:]*: *//' | tr -d '\r'; }

paso() {  # titulo, codigo esperado
  if [ "$codigo" = "$2" ]; then bien=$((bien+1)); marca="OK "; else mal=$((mal+1)); marca="MAL"; fi
  printf '\n[%s] %s\n      esperado %s, respondio %s\n' "$marca" "$1" "$2" "$codigo"
  for h in Location ETag Retry-After Idempotent-Replayed WWW-Authenticate; do
    [ -n "$(header $h)" ] && printf '      %s: %s\n' "$h" "$(header $h)"
  done
  [ -s "$tmp/b" ] && printf '      %s\n' "$(cut -c1-300 "$tmp/b")"
}

# Deja el JWT en $TOKEN
entrar() {
  pedir POST /auth/login -H 'Content-Type: application/json' -d "{\"email\":\"$1\",\"password\":\"$CLAVE\"}"
  TOKEN=$(sed -n 's/.*"token":"\([^"]*\)".*/\1/p' "$tmp/b")
}

echo "== 1. JWT =="
entrar mod@mail.com; MOD=$TOKEN
paso "Login del moderador: devuelve un JWT" 200
[ -z "$MOD" ] && { echo; echo "No hay token: ¿esta levantada la app? (docker compose up --build)"; exit 1; }

pedir GET /devoluciones
paso "Sin token" 401
pedir GET /devoluciones -H "Authorization: Bearer ${MOD%??}xx"
paso "Con la firma del token cambiada" 401
pedir POST /auth/login -H 'Content-Type: application/json' -d '{"email":"mod@mail.com","password":"no-es-la-clave"}'
paso "Login con la clave mal (mensaje generico)" 401
entrar mod@mail.com; MOD=$TOKEN

echo; echo "== 2. Crear una devolucion con Idempotency-Key =="
# Compras de prueba de schema.sql que se pueden devolver: se usa la primera que siga libre
for par in ana:1 carlos:3 lucia:5 javier:8; do
  usuario=${par%%:*}; venta=${par#*:}
  entrar "$usuario@mail.com"; CLI=$TOKEN
  KEY="demo-$(date +%s)-$venta"
  CUERPO="{\"ventaId\":$venta,\"motivo\":\"NO_COMPATIBLE\",\"comentario\":\"No entra en mi bici\"}"
  pedir POST /devoluciones -H "Authorization: Bearer $CLI" -H "Idempotency-Key: $KEY" -H 'Content-Type: application/json' -d "$CUERPO"
  [ "$codigo" = 201 ] && break
done
paso "POST /devoluciones como $usuario (compra $venta)" 201
ID=$(sed -n 's/^{"id":\([0-9]*\).*/\1/p' "$tmp/b")
[ -z "$ID" ] && { echo; echo "No quedan compras de prueba sin devolver: docker compose down -v && docker compose up --build"; exit 1; }
ETAG=$(header ETag)

pedir POST /devoluciones -H "Authorization: Bearer $CLI" -H "Idempotency-Key: $KEY" -H 'Content-Type: application/json' -d "$CUERPO"
paso "El mismo pedido con la misma clave: la misma devolucion, no otra" 201
pedir POST /devoluciones -H "Authorization: Bearer $CLI" -H "Idempotency-Key: $KEY" -H 'Content-Type: application/json' -d "{\"ventaId\":$venta,\"motivo\":\"DEFECTUOSO\",\"comentario\":\"otra cosa\"}"
paso "La misma clave con otro contenido" 409
pedir POST /devoluciones -H "Authorization: Bearer $CLI" -H 'Content-Type: application/json' -d "$CUERPO"
paso "Sin Idempotency-Key" 400

echo; echo "== 3. ETag =="
pedir GET "/devoluciones/$ID" -H "Authorization: Bearer $CLI"
paso "GET /devoluciones/$ID" 200
pedir GET "/devoluciones/$ID" -H "Authorization: Bearer $CLI" -H "If-None-Match: $ETAG"
paso "GET con If-None-Match vigente: sin cuerpo" 304

echo; echo "== 4. Roles y datos ajenos =="
if [ "$usuario" = ana ]; then entrar carlos@mail.com; else entrar ana@mail.com; fi
OTRO=$TOKEN
pedir GET "/devoluciones/$ID" -H "Authorization: Bearer $OTRO"
paso "Otro cliente pide esa devolucion (BOLA): para el no existe" 404
pedir PATCH "/devoluciones/$ID" -H "Authorization: Bearer $CLI" -H "If-Match: $ETAG" -H 'Content-Type: application/json' -d '{"estado":"APROBADA"}'
paso "El cliente quiere aprobarse su devolucion (BFLA)" 403

echo; echo "== 5. Resolver con If-Match y el 409 del State =="
pedir PATCH "/devoluciones/$ID" -H "Authorization: Bearer $MOD" -H 'Content-Type: application/json' -d '{"estado":"APROBADA"}'
paso "PATCH sin If-Match" 428
pedir PATCH "/devoluciones/$ID" -H "Authorization: Bearer $MOD" -H 'If-Match: "0000000000000000"' -H 'Content-Type: application/json' -d '{"estado":"APROBADA"}'
paso "PATCH con un ETag viejo" 412
pedir PATCH "/devoluciones/$ID" -H "Authorization: Bearer $MOD" -H "If-Match: $ETAG" -H 'Content-Type: application/json' -d '{"estado":"APROBADA"}'
paso "PATCH con el ETag vigente: APROBADA (stock, reembolso y aviso salen por el topico)" 200

sleep 4
pedir GET "/devoluciones/$ID" -H "Authorization: Bearer $MOD"
paso "Unos segundos despues ya esta REEMBOLSADA" 200
ETAG=$(header ETag)
pedir PATCH "/devoluciones/$ID" -H "Authorization: Bearer $MOD" -H "If-Match: $ETAG" -H 'Content-Type: application/json' -d '{"estado":"RECHAZADA"}'
paso "Rechazar una devolucion REEMBOLSADA: el State no lo permite" 409

printf '\n%s pasos bien, %s mal\n' "$bien" "$mal"
[ "$mal" = 0 ]
