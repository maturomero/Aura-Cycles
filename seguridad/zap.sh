#!/usr/bin/env bash
# ZAP baseline (escaneo pasivo) sin sesion, con sesion de cliente y con sesion de moderador.
# Requiere la app levantada (docker compose up) y python con playwright. Uso: seguridad/zap.sh [carpeta]
set -euo pipefail
APP=${APP_URL:-https://localhost:8443/repuestos}
OUT=${1:-zap-reportes}
mkdir -p "$OUT" && chmod 777 "$OUT"
# El ViewState de JSF es el token anti-CSRF (estado en el servidor, id aleatorio): se lo decimos a ZAP
CSRF="-config anticsrf.tokens.token(30).name=jakarta.faces.ViewState -config anticsrf.tokens.token(30).enabled=true"

escanear() {  # url, nombre, opciones extra
  docker run --rm --network host -v "$(cd "$OUT" && pwd):/zap/wrk:rw" ghcr.io/zaproxy/zaproxy:stable \
    zap-baseline.py -t "$1" -m 1 -I -J "$2.json" -r "$2.html" -z "$CSRF $3" > "$OUT/$2.log" 2>&1 || true
  grep -E '^(WARN|FAIL)' "$OUT/$2.log" || true
}

escanear "$APP/" sin-sesion ""
for par in ana@mail.com:productos.xhtml mod@mail.com:moderacion.xhtml; do
  email=${par%%:*}; inicio=${par#*:}
  sesion=$(python3 "$(dirname "$0")/sesion.py" "$email")
  # La cookie va en todos los pedidos; sin enviar formularios, asi el spider no cierra la sesion
  escanear "$APP/$inicio" "sesion-${email%%@*}" "-config spider.postform=false \
    -config replacer.full_list(0).description=sesion -config replacer.full_list(0).enabled=true \
    -config replacer.full_list(0).matchtype=REQ_HEADER -config replacer.full_list(0).matchstr=Cookie \
    -config replacer.full_list(0).regex=false -config replacer.full_list(0).replacement=JSESSIONID=$sesion"
done
python3 "$(dirname "$0")/zap_resumen.py" "$OUT"
