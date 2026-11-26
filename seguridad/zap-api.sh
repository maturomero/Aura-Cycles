#!/usr/bin/env bash
# ZAP sobre la API REST: importa docs/api/openapi.yaml y hace el escaneo activo con el JWT de un cliente y de un moderador.
# Requiere la app levantada (docker compose up). Uso: seguridad/zap-api.sh [carpeta]
set -euo pipefail
APP=${APP_URL:-https://localhost:8443/repuestos}
OUT=${1:-zap-api}
mkdir -p "$OUT" && chmod 777 "$OUT"
cp "$(dirname "$0")/../docs/api/openapi.yaml" "$OUT/openapi.yaml"

# Los dos tokens se piden antes de escanear: el escaneo activo prueba el login con datos malos y dispara el bloqueo por IP
declare -A JWT
for email in ana@mail.com mod@mail.com; do
  JWT[$email]=$(curl -sk -X POST "$APP/api/v1/auth/login" -H 'Content-Type: application/json' \
        -d "{\"email\":\"$email\",\"password\":\"Clave1234\"}" | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])')
done

for email in ana@mail.com mod@mail.com; do
  jwt=${JWT[$email]}
  nombre="api-${email%%@*}"
  docker run --rm --network host -v "$(cd "$OUT" && pwd):/zap/wrk:rw" ghcr.io/zaproxy/zaproxy:stable \
    zap-api-scan.py -t /zap/wrk/openapi.yaml -f openapi -I -J "$nombre.json" -r "$nombre.html" -z "\
      -config replacer.full_list(0).description=jwt -config replacer.full_list(0).enabled=true \
      -config replacer.full_list(0).matchtype=REQ_HEADER -config replacer.full_list(0).matchstr=Authorization \
      -config replacer.full_list(0).regex=false -config replacer.full_list(0).replacement=Bearer\ $jwt" \
    > "$OUT/$nombre.log" 2>&1 || true
  grep -E '^(WARN|FAIL)' "$OUT/$nombre.log" || true
done
rm -f "$OUT/openapi.yaml"
python3 "$(dirname "$0")/zap_resumen.py" "$OUT"
