#!/usr/bin/env python3
"""Recompute and validate the camera-free PDR evidence-based project tracker."""
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "docs" / "PDR_TASKS.json"
OUTPUT = ROOT / "docs" / "PDR_PROGRESS.md"

def load():
    data = json.loads(SOURCE.read_text(encoding="utf-8"))
    if data.get("schema_version") != 1:
        raise ValueError("Unknown task schema_version")
    ids, sub_ids, total_weight = set(), set(), 0
    if not data.get("phases"):
        raise ValueError("Missing phases")
    for phase in data["phases"]:
        if not phase.get("tasks"):
            raise ValueError("Phase without tasks: " + str(phase.get("id")))
        for task in phase["tasks"]:
            tid = task["id"]
            if tid in ids:
                raise ValueError("Duplicate task id: " + tid)
            ids.add(tid)
            weight = task["weight"]
            if type(weight) is not int or not 0 < weight <= 100:
                raise ValueError("Invalid task weight: " + tid)
            total_weight += weight
            if not task.get("title") or not task.get("limit"):
                raise ValueError("Task needs title and limit: " + tid)
            if sum(s["weight"] for s in task["subtasks"]) != 100:
                raise ValueError("Subtask weights must equal 100: " + tid)
            for s in task["subtasks"]:
                sid = s["id"]
                if sid in sub_ids or not sid.startswith(tid + "-"):
                    raise ValueError("Duplicate/bad subtask id: " + sid)
                sub_ids.add(sid)
                if type(s["accepted"]) is not bool or s["percent"] not in (0, 100):
                    raise ValueError("Subtask must be accepted true/false and 0/100: " + sid)
                if s["accepted"] != (s["percent"] == 100):
                    raise ValueError("Percent and acceptance mismatch: " + sid)
                if type(s["weight"]) is not int or s["weight"] <= 0:
                    raise ValueError("Invalid subtask weight: " + sid)
                if not s.get("title") or not s.get("acceptance"):
                    raise ValueError("Missing subtask detail: " + sid)
                if s["accepted"]:
                    if not s.get("evidence"):
                        raise ValueError("Completed subtask without evidence: " + sid)
                    target = ROOT / s["evidence"]
                    if Path(s["evidence"]).is_absolute() or ".." in Path(s["evidence"]).parts or not target.is_file():
                        raise ValueError("Evidence file missing or outside repo: " + sid)
                elif not s.get("blocker"):
                    raise ValueError("Incomplete subtask without blocker: " + sid)
    if total_weight != 100:
        raise ValueError("Global task weights sum to %s rather than 100" % total_weight)
    return data

def task_pct(task):
    return sum(s["weight"] * s["percent"] for s in task["subtasks"]) / 100.0

def total_pct(data):
    return sum(task["weight"] * task_pct(task) for ph in data["phases"] for task in ph["tasks"]) / 100.0

def render(data):
    tasks = [task for ph in data["phases"] for task in ph["tasks"]]
    subtasks = sum(len(t["subtasks"]) for t in tasks)
    overall = total_pct(data)
    lines = [
        "# Suivi — odométrie PDR sans caméra (Galaxy S21)",
        "",
        "> **Avancement pondéré : %.1f %%** · %d tâches / %d sous-tâches · dernière mise à jour : %s." % (overall,len(tasks),subtasks,data["updated_at"]),
        ">",
        "> **Important :** ce pourcentage mesure les *livrables jalonnés* du plan, **pas une précision de localisation de %.1f %%**. Les sous-tâches passent de 0 à 100 %% uniquement sur preuve; leur somme pondérée donne le pourcentage de la tâche. Les tâches sont elles-mêmes pondérées, somme des poids = 100. Une réalisation logicielle ne remplace jamais les tests réels du Galaxy S21." % overall,
        ">",
        "> Branche `feature/camera-free-pdr` · [PR #5](https://github.com/jboehm1/VisualOdometry/pull/5) · [CI de référence : succès](https://github.com/jboehm1/VisualOdometry/actions/runs/39156725543) · [Documentation technique](PDR_MODULE.md).",
        ">",
        "> Source modifiable : [PDR_TASKS.json](PDR_TASKS.json). Mise à jour : `python3 tools/pdr/progress.py --write`. Contrôle cohérence : `python3 tools/pdr/progress.py --check`.",
        "",
        "## Synthèse par phase",
        "",
        "| Phase | Poids global | Avancement de phase | Contribution réelle |",
        "|---|---:|---:|---:|"
    ]
    for phase in data["phases"]:
        tw = sum(t["weight"] for t in phase["tasks"])
        contrib = sum(t["weight"] * task_pct(t) for t in phase["tasks"]) / 100.0
        lines.append("| %s — %s | %d %% | %.1f %% | %.2f pts |" % (phase["id"],phase["title"],tw,100*contrib/tw,contrib))
    lines += [
        "| **TOTAL** | **100 %%** | **%.1f %%** | **%.2f pts** |" % (overall,overall),
        "",
        "## État démontré et limites prioritaires",
        "",
        "- **Validé en CI :** compilation Android, lint, tests unitaires, replays synthétiques, APK debug; cela ne démontre ni précision réelle ni absence de bugs sur le S21.",
        "- **Opérationnel au niveau logiciel :** mode sans caméra, distance pas × foulée, trace locale sous hypothèse d'orientation, capture des capteurs, export ZIP, replay.",
        "- **Non démontré :** fonctionnement physique complet sur S21, précision à pied/en courant, téléphone en poche, navigation fiable sans GNSS, vraie correction de dérive, service écran éteint.",
        "- **Critère de publication :** tests matériels indépendants, échappatoires en cas d'ambiguïté, erreurs et batterie mesurées. Rester en PR draft tant que ces preuves manquent.",
        "",
        "## Tableau des tâches",
        "",
        "| ID | Tâche | Poids global | Avancement | État |",
        "|---|---|---:|---:|---|"
    ]
    for t in tasks:
        pct = task_pct(t)
        status = "Terminé" if pct==100 else ("Non commencé" if pct==0 else "Partiel")
        lines.append("| [%s](#%s) | %s | %d %% | **%.0f %%** | %s |" % (t["id"],t["id"].lower(),t["title"],t["weight"],pct,status))
    lines.extend(["","## Détail des sous-tâches, preuves et limites",""])
    for phase in data["phases"]:
        lines.extend(["## %s — %s" % (phase["id"],phase["title"]),"",phase["objective"],""])
        for t in phase["tasks"]:
            pct=task_pct(t)
            lines += [
                "### %s — %s (%.0f %%)" % (t["id"],t["title"],pct),
                "",
                "**Poids dans le projet : %d %%. Limite actuelle :** %s" % (t["weight"],t["limit"]),
                "",
                "| Sous-tâche | Part de tâche | Avancement | Preuve / critère restant |",
                "|---|---:|---:|---|"
            ]
            for s in t["subtasks"]:
                if s["accepted"]:
                    proof="Livré : [%s](../%s). Critère : %s" % (s["evidence"],s["evidence"],s["acceptance"])
                else:
                    proof="**À faire :** " + s["blocker"]
                lines.append("| %s — %s | %d %% | **%d %%** | %s |" % (s["id"],s["title"],s["weight"],s["percent"],proof))
            lines.append("")
    lines += [
        "## Règles de mise à jour",
        "",
        "1. Ajouter une preuve vérifiable dans la sous-tâche (code, test, mesure, rapport) avant de changer `accepted` à `true`.",
        "2. Ne jamais réduire les poids pour faire monter le score. Si l'objectif change, documenter la nouvelle base et conserver l'ancienne dans l'historique Git.",
        "3. Lancer `python3 tools/pdr/progress.py --write` pour régénérer ce tableau; la CI refuse les incohérences.",
        "4. Les tests réels, la qualité des positions et l'IA ne passent à 100 % qu'après une preuve indépendante."
    ]
    return "\n".join(lines) + "\n"

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    group=parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--check",action="store_true")
    group.add_argument("--write",action="store_true")
    args=parser.parse_args()
    data=load()
    expected=render(data)
    if args.write:
        OUTPUT.write_text(expected,encoding="utf-8")
    elif not OUTPUT.is_file() or OUTPUT.read_text(encoding="utf-8")!=expected:
        raise SystemExit("PDR_PROGRESS.md is stale: run python3 tools/pdr/progress.py --write")
    print("PDR progress validated: %.1f%% | %d tasks" % (total_pct(data),sum(len(p["tasks"]) for p in data["phases"])))

if __name__=="__main__":
    main()
