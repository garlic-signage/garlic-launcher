#!/usr/bin/env python3
"""Writes an SVG badge with the line coverage from the XML report of JaCoCo.

    coverage_badge.py report.xml coverage.svg

No external service: the CI commits the SVG to the branch "badges", the README shows it from there.
"""
import sys
import xml.etree.ElementTree as ElementTree


def color(percent):
    if percent >= 90:
        return "#4c1"     # green
    if percent >= 75:
        return "#97ca00"  # yellow green
    if percent >= 60:
        return "#dfb317"  # yellow
    return "#e05d44"      # red


def width(text):
    # Verdana 11px, roughly; enough for digits, "%" and the label
    return 7 * len(text) + 14


def badge(label, value, fill):
    left, right = width(label), width(value)
    total = left + right
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="{total}" height="20" role="img" aria-label="{label}: {value}">
<title>{label}: {value}</title>
<linearGradient id="s" x2="0" y2="100%"><stop offset="0" stop-color="#bbb" stop-opacity=".1"/><stop offset="1" stop-opacity=".1"/></linearGradient>
<clipPath id="r"><rect width="{total}" height="20" rx="3" fill="#fff"/></clipPath>
<g clip-path="url(#r)"><rect width="{left}" height="20" fill="#555"/><rect x="{left}" width="{right}" height="20" fill="{fill}"/><rect width="{total}" height="20" fill="url(#s)"/></g>
<g fill="#fff" text-anchor="middle" font-family="Verdana,Geneva,DejaVu Sans,sans-serif" font-size="11">
<text x="{left / 2}" y="15" fill="#010101" fill-opacity=".3">{label}</text><text x="{left / 2}" y="14">{label}</text>
<text x="{left + right / 2}" y="15" fill="#010101" fill-opacity=".3">{value}</text><text x="{left + right / 2}" y="14">{value}</text>
</g>
</svg>
"""


def line_percent(report_path):
    # the counters directly below <report> are the totals of all packages
    root = ElementTree.parse(report_path).getroot()
    for counter in root.findall("counter"):
        if counter.get("type") == "LINE":
            missed, covered = int(counter.get("missed")), int(counter.get("covered"))
            return 100.0 * covered / (missed + covered) if missed + covered else 0.0
    sys.exit(f"{report_path}: no LINE counter found")


def main():
    if len(sys.argv) != 3:
        sys.exit("usage: coverage_badge.py <jacoco report.xml> <badge.svg>")
    percent = line_percent(sys.argv[1])
    with open(sys.argv[2], "w", encoding="utf-8") as svg:
        svg.write(badge("coverage", f"{percent:.0f}%", color(percent)))
    print(f"line coverage {percent:.1f}%")


if __name__ == "__main__":
    main()
