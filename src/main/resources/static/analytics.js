// Categorical colors are fixed per engine (slots 1-4 of the validated
// palette) so the same engine reads as the same color across both the
// usage-count chart and the latency chart. Category/urgency charts use a
// single sequential hue instead — they're magnitude-by-label, not identity.
const SEQUENTIAL_BLUE = "#2a78d6";
const ENGINE_COLORS = {
  jev: "#2a78d6",
  rule_based: "#eb6834",
  fallback: "#1baf7a",
  rule_based_fast_path: "#eda100",
};
const ENGINE_LABELS = {
  jev: "Jev",
  rule_based: "Rule-based",
  fallback: "Fallback",
  rule_based_fast_path: "Rule-based (fast path)",
};
const URGENCY_ORDER = ["low", "normal", "high", "critical"];

(async function init() {
  let summary;
  try {
    const res = await fetch("/api/analytics/summary");
    if (!res.ok) throw new Error(await res.text());
    summary = await res.json();
  } catch (err) {
    alert("Failed to load analytics: " + err.message);
    return;
  }

  if (summary.totalDecisions === 0) {
    document.getElementById("summaryStats").style.display = "none";
    document.getElementById("chartsWrap").style.display = "none";
    document.getElementById("emptyPanel").style.display = "";
    return;
  }

  renderSummaryStats(summary);
  renderCategoryChart(summary.categoryDistribution);
  renderUrgencyChart(summary.urgencyDistribution);
  renderEngineChart(summary.engineUsageDistribution);
  renderLatencyChart(summary.avgLatencyByEngine);
  renderDailyChart(summary.decisionsPerDay);
})();

function renderSummaryStats(summary) {
  document.getElementById("statTotal").textContent = summary.totalDecisions.toLocaleString();
  document.getElementById("statAutoRoute").textContent = pct(summary.autoRoutedPercentage);
  document.getElementById("statConfidence").textContent = pct(summary.avgConfidence);

  const agreementEl = document.getElementById("statAgreement");
  if (summary.agreementRate === null) {
    agreementEl.textContent = "No Compare-both data yet";
  } else {
    const pairs = summary.comparablePairCount;
    agreementEl.textContent = `${pct(summary.agreementRate)} (${pairs} ticket${pairs === 1 ? "" : "s"})`;
  }
}

function renderCategoryChart(distribution) {
  const items = sortedEntries(distribution).map(([label, value]) => ({ label, value, color: SEQUENTIAL_BLUE }));
  renderBarChart(document.getElementById("categoryChart"), items);
}

function renderUrgencyChart(distribution) {
  const items = URGENCY_ORDER.filter((label) => distribution[label] !== undefined).map((label) => ({
    label,
    value: distribution[label],
    color: SEQUENTIAL_BLUE,
  }));
  renderBarChart(document.getElementById("urgencyChart"), items);
}

function renderEngineChart(distribution) {
  const engines = Object.keys(distribution);
  const items = engines.map((engine) => ({
    label: ENGINE_LABELS[engine] || engine,
    value: distribution[engine],
    color: ENGINE_COLORS[engine] || SEQUENTIAL_BLUE,
  }));
  renderBarChart(document.getElementById("engineChart"), items);
  renderLegend(document.getElementById("engineLegend"), engines);
}

function renderLatencyChart(avgLatencyByEngine) {
  const items = Object.keys(avgLatencyByEngine).map((engine) => ({
    label: ENGINE_LABELS[engine] || engine,
    value: Math.round(avgLatencyByEngine[engine] * 10) / 10,
    color: ENGINE_COLORS[engine] || SEQUENTIAL_BLUE,
  }));
  renderBarChart(document.getElementById("latencyChart"), items, { formatValue: (v) => `${v} ms` });
}

function renderDailyChart(decisionsPerDay) {
  const chartEl = document.getElementById("dailyChart");
  const note = document.getElementById("dailyEmptyNote");
  if (decisionsPerDay.length <= 1) {
    chartEl.style.display = "none";
    note.style.display = "";
    return;
  }
  chartEl.style.display = "";
  note.style.display = "none";
  const items = decisionsPerDay.map((d) => ({ label: d.date, value: d.count, color: SEQUENTIAL_BLUE }));
  renderBarChart(chartEl, items);
}

function renderLegend(container, engines) {
  container.replaceChildren(
    ...engines.map((engine) => {
      const item = document.createElement("span");
      item.className = "legend-item";
      const swatch = document.createElement("span");
      swatch.className = "legend-swatch";
      swatch.style.background = ENGINE_COLORS[engine] || SEQUENTIAL_BLUE;
      item.appendChild(swatch);
      item.appendChild(document.createTextNode(ENGINE_LABELS[engine] || engine));
      return item;
    })
  );
}

// Simple horizontal bar chart, built as inline SVG. Bars are capped at 20px
// thick (well under the 24px spec ceiling), rounded only at the data end
// (square at the baseline), with a 10px row gap and a direct value label at
// the tip — so every value is readable without hovering. A native <title>
// still provides the supplementary hover detail.
function renderBarChart(container, items, options = {}) {
  if (items.length === 0) {
    container.replaceChildren();
    return;
  }

  const barHeight = 20;
  const rowHeight = 30;
  const labelWidth = 140;
  const plotWidth = 420;
  const totalWidth = labelWidth + plotWidth + 60;
  const totalHeight = items.length * rowHeight;
  const maxValue = Math.max(...items.map((i) => i.value), 1);
  const formatValue = options.formatValue || ((v) => v.toLocaleString());

  const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  svg.setAttribute("viewBox", `0 0 ${totalWidth} ${totalHeight}`);
  svg.setAttribute("width", "100%");
  svg.setAttribute("height", String(totalHeight));
  svg.setAttribute("role", "img");
  svg.setAttribute("aria-label", "Bar chart");

  items.forEach((item, i) => {
    const y = i * rowHeight + (rowHeight - barHeight) / 2;
    const barWidth = item.value > 0 ? Math.max((item.value / maxValue) * plotWidth, 3) : 0;

    const label = document.createElementNS("http://www.w3.org/2000/svg", "text");
    label.setAttribute("x", String(labelWidth - 10));
    label.setAttribute("y", String(y + barHeight / 2));
    label.setAttribute("dy", "0.35em");
    label.setAttribute("text-anchor", "end");
    label.setAttribute("class", "chart-axis-label");
    label.textContent = item.label;
    svg.appendChild(label);

    if (barWidth > 0) {
      const path = document.createElementNS("http://www.w3.org/2000/svg", "path");
      path.setAttribute("d", roundedBarPath(barWidth, barHeight, 4));
      path.setAttribute("transform", `translate(${labelWidth}, ${y})`);
      path.setAttribute("fill", item.color);

      const title = document.createElementNS("http://www.w3.org/2000/svg", "title");
      title.textContent = `${item.label}: ${formatValue(item.value)}`;
      path.appendChild(title);
      svg.appendChild(path);
    }

    const value = document.createElementNS("http://www.w3.org/2000/svg", "text");
    value.setAttribute("x", String(labelWidth + barWidth + 8));
    value.setAttribute("y", String(y + barHeight / 2));
    value.setAttribute("dy", "0.35em");
    value.setAttribute("class", "chart-value-label");
    value.textContent = formatValue(item.value);
    svg.appendChild(value);
  });

  container.replaceChildren(svg);
}

function roundedBarPath(width, height, radius) {
  const r = Math.max(0, Math.min(radius, height / 2, width / 2));
  if (r === 0) {
    return `M0,0 H${width} V${height} H0 Z`;
  }
  return `M0,0 H${width - r} A${r},${r} 0 0 1 ${width},${r} V${height - r} A${r},${r} 0 0 1 ${width - r},${height} H0 Z`;
}

function sortedEntries(obj) {
  return Object.entries(obj).sort((a, b) => b[1] - a[1]);
}

function pct(value) {
  return `${Math.round(value * 100)}%`;
}
