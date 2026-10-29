#!/usr/bin/env bash
# ZAP activo autenticado (cliente y moderador). Requiere la app levantada y python con playwright. Uso: seguridad/zap-activo.sh [carpeta]
set -euo pipefail
OUT=${1:-zap-activo}
mkdir -p "$OUT" && chmod 777 "$OUT"
for email in ${ZAP_USUARIOS:-ana@mail.com mod@mail.com}; do
  docker rm -f zap-activo >/dev/null 2>&1 || true
  docker run -d --name zap-activo --network host -v "$(cd "$OUT" && pwd):/zap/wrk:rw" ghcr.io/zaproxy/zaproxy:stable \
    zap.sh -daemon -host 127.0.0.1 -port 8091 -config api.disablekey=true -config spider.postform=false \
    -config "anticsrf.tokens.token(30).name=jakarta.faces.ViewState" -config "anticsrf.tokens.token(30).enabled=true" >/dev/null
  until curl -s --noproxy '*' -o /dev/null http://127.0.0.1:8091/JSON/core/view/version/; do sleep 2; done
  python3 "$(dirname "$0")/zap_activo.py" "$email" "$OUT"
  docker rm -f zap-activo >/dev/null
done
python3 "$(dirname "$0")/zap_resumen.py" "$OUT"
