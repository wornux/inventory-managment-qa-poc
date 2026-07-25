#!/usr/bin/env python3
import argparse
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Summarize a JaCoCo XML report and list residual source gaps.")
    parser.add_argument("report", nargs="?", default="target/site/jacoco/jacoco.xml")
    parser.add_argument("--fail-on-missed", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    report = Path(args.report)
    if not report.is_file():
        print(f"JaCoCo report not found: {report}", file=sys.stderr)
        return 2

    root = ET.parse(report).getroot()
    missed_total = 0
    for counter in root.findall("counter"):
        missed = int(counter.attrib["missed"])
        covered = int(counter.attrib["covered"])
        total = missed + covered
        ratio = covered / total * 100 if total else 100.0
        missed_total += missed
        print(f"{counter.attrib['type']:11} {covered:5}/{total:<5} {ratio:6.2f}%")

    gaps = []
    for package in root.findall("package"):
        package_name = package.attrib["name"].replace("/", ".")
        for source in package.findall("sourcefile"):
            lines = [
                line.attrib["nr"]
                for line in source.findall("line")
                if int(line.attrib.get("mi", "0")) or int(line.attrib.get("mb", "0"))
            ]
            if lines:
                gaps.append((package_name, source.attrib["name"], lines))

    if gaps:
        print("\nResidual source gaps:")
        for package_name, source_name, lines in gaps:
            print(f"- {package_name}.{source_name}: {', '.join(lines)}")
    else:
        print("\nNo residual source gaps.")

    return 1 if args.fail_on_missed and missed_total else 0


if __name__ == "__main__":
    raise SystemExit(main())
