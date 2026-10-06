#!/usr/bin/env python3
# Summarises the JUnit and JaCoCo XML reports of the root and common modules for run.sh test
import glob
import os
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODULES = [("impetus", ROOT), ("common", os.path.join(ROOT, "common"))]
COUNTERS = ["CLASS", "METHOD", "LINE", "BRANCH"]


def junit_results(module_dir):
    cases = []
    for path in sorted(glob.glob(os.path.join(module_dir, "build", "test-results", "*", "*.xml"))):
        for case in ET.parse(path).getroot().iter("testcase"):
            status = "pass"
            detail = ""
            for tag in ("failure", "error"):
                node = case.find(tag)
                if node is not None:
                    status = "fail"
                    detail = (node.get("message") or node.text or "").strip().splitlines()[0] if (node.get("message") or node.text) else ""
            if case.find("skipped") is not None:
                status = "skip"
            cases.append((case.get("classname"), case.get("name"), status, detail))
    return cases


def jacoco_totals(module_dir):
    path = os.path.join(module_dir, "build", "reports", "jacoco", "test", "jacocoTestReport.xml")
    if not os.path.exists(path):
        return None, []
    report = ET.parse(path).getroot()
    totals = {c: (0, 0) for c in COUNTERS}
    for counter in report.findall("counter"):
        kind = counter.get("type")
        if kind in totals:
            totals[kind] = (int(counter.get("missed")), int(counter.get("covered")))
    gaps = []
    for package in report.findall("package"):
        for clazz in package.findall("class"):
            counts = {c.get("type"): (int(c.get("missed")), int(c.get("covered"))) for c in clazz.findall("counter")}
            missed_methods = counts.get("METHOD", (0, 0))[0]
            if missed_methods:
                names = [m.get("name") for m in clazz.findall("method")
                         if any(c.get("type") == "METHOD" and int(c.get("missed")) for c in m.findall("counter"))]
                gaps.append((clazz.get("name").replace("/", "."), missed_methods, names))
    return totals, gaps


def pct(missed, covered):
    total = missed + covered
    return 100.0 if total == 0 else 100.0 * covered / total


def main():
    verbose = "--all" in sys.argv
    exit_code = 0
    grand = {c: [0, 0] for c in COUNTERS}
    for name, module_dir in MODULES:
        print(f"\n== {name} ==")
        cases = junit_results(module_dir)
        failed = [c for c in cases if c[2] == "fail"]
        skipped = [c for c in cases if c[2] == "skip"]
        passed = len(cases) - len(failed) - len(skipped)
        if not cases:
            print("  no test results found (did compileTestJava fail?)")
            exit_code = 1
        else:
            print(f"  tests: {len(cases)}  passed: {passed}  failed: {len(failed)}  skipped: {len(skipped)}")
        for classname, test, status, detail in cases:
            if status == "fail" or verbose:
                mark = {"pass": "PASS", "fail": "FAIL", "skip": "SKIP"}[status]
                print(f"  [{mark}] {classname}.{test}" + (f"  -- {detail}" if detail else ""))
        if failed:
            exit_code = 1
        totals, gaps = jacoco_totals(module_dir)
        if totals is None:
            print("  no coverage report found")
            exit_code = 1
            continue
        print("  coverage: " + "  ".join(f"{c.lower()} {pct(*totals[c]):.2f}% ({totals[c][1]}/{sum(totals[c])})" for c in COUNTERS))
        for c in COUNTERS:
            grand[c][0] += totals[c][0]
            grand[c][1] += totals[c][1]
        if gaps:
            print(f"  classes with uncovered methods: {len(gaps)}")
            for clazz, missed, names in sorted(gaps):
                shown = ", ".join(names[:6]) + (", ..." if len(names) > 6 else "")
                print(f"    {clazz}  ({missed} missed): {shown}")
            exit_code = 1
    print("\n== overall ==")
    print("  " + "  ".join(f"{c.lower()} {pct(*grand[c]):.2f}%" for c in COUNTERS))
    for c in ("METHOD", "CLASS"):
        if grand[c][0]:
            print(f"  {c.lower()} coverage is below 100%")
            exit_code = 1
    return exit_code


if __name__ == "__main__":
    sys.exit(main())
