"""Junta los reportes JSON de ZAP. Falla si hay algun High o un Medium que no este justificado en zap-justificadas.tsv."""
import json
import os
import pathlib
import sys
from collections import Counter

RIESGO = {"3": "High", "2": "Medium", "1": "Low", "0": "Info"}
carpeta = pathlib.Path(sys.argv[1])
justificadas = {}
for linea in (pathlib.Path(__file__).parent / "zap-justificadas.tsv").read_text(encoding="utf-8").splitlines():
    if linea.strip() and not linea.startswith("#"):
        plugin, motivo = linea.split("\t", 1)
        justificadas[plugin] = motivo

filas, problemas = [], []
for reporte in sorted(carpeta.glob("*.json")):
    for sitio in json.loads(reporte.read_text())["site"]:
        for a in sitio["alerts"]:
            riesgo = RIESGO[a["riskcode"]]
            filas.append((reporte.stem, riesgo, a["pluginid"], a["name"], len(a["instances"])))
            if riesgo == "High" or (riesgo == "Medium" and a["pluginid"] not in justificadas):
                problemas.append(f"{reporte.stem}: [{riesgo}] {a['name']} ({a['pluginid']})")

print("| Escaneo | Riesgo | Regla | Alerta | Instancias | Justificacion |")
print("|---|---|---|---|---|---|")
for esc, riesgo, plugin, nombre, n in filas:
    print(f"| {esc} | {riesgo} | {plugin} | {nombre} | {n} | {justificadas.get(plugin, '')} |")
if os.environ.get("GITHUB_ACTIONS"):
    c = Counter(f[1] for f in filas)
    print(f"::notice title={os.environ.get('ZAP_TITULO', 'ZAP')}::High {c['High']}, Medium {c['Medium']}, Low {c['Low']}, informativas {c['Info']}")
if problemas:
    print("\nSin resolver:\n" + "\n".join(problemas))
    sys.exit(1)
print("\nOK: 0 High y ningun Medium sin justificar")
