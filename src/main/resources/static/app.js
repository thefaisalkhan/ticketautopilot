const SINGLE_URL = "/api/tickets";
const COMPARE_URL = "/api/tickets/compare";

const state = {
  mode: "jev", // 'jev' | 'rule_based' | 'compare'
  singleResults: [],
  compareResults: [],
};

const form = document.getElementById("ticketForm");
const subjectInput = document.getElementById("subject");
const bodyInput = document.getElementById("body");
const submitBtn = document.getElementById("submitBtn");
const bulkDemoBtn = document.getElementById("bulkDemoBtn");
const resultsBody = document.getElementById("resultsBody");
const compareBody = document.getElementById("compareBody");
const emptyState = document.getElementById("emptyState");
const statsBar = document.getElementById("statsBar");
const singleResultsWrap = document.getElementById("singleResultsWrap");
const compareResultsWrap = document.getElementById("compareResultsWrap");
const engineButtons = document.querySelectorAll(".engine-btn");

engineButtons.forEach((btn) => {
  btn.addEventListener("click", () => {
    if (btn.classList.contains("active")) return;

    engineButtons.forEach((b) => b.classList.remove("active"));
    btn.classList.add("active");
    state.mode = btn.dataset.engine;
    state.singleResults = [];
    state.compareResults = [];
    resultsBody.innerHTML = "";
    compareBody.innerHTML = "";

    const isCompare = state.mode === "compare";
    singleResultsWrap.style.display = isCompare ? "none" : "";
    compareResultsWrap.style.display = isCompare ? "" : "none";
    statsBar.dataset.mode = isCompare ? "compare" : "single";

    updateEmptyState();
    renderStats();
  });
});

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  const subject = subjectInput.value.trim();
  const body = bodyInput.value.trim();
  if (!subject || !body) return;

  submitBtn.disabled = true;
  try {
    await submitAndRender(subject, body);
    form.reset();
  } catch (err) {
    alert("Failed to submit ticket: " + err.message);
  } finally {
    submitBtn.disabled = false;
  }
});

bulkDemoBtn.addEventListener("click", async () => {
  bulkDemoBtn.disabled = true;
  const originalLabel = bulkDemoBtn.textContent;
  try {
    for (let i = 0; i < SAMPLE_TICKETS.length; i++) {
      const sample = SAMPLE_TICKETS[i];
      bulkDemoBtn.textContent = `Running demo (${i + 1}/${SAMPLE_TICKETS.length})...`;
      await submitAndRender(sample.subject, sample.body);
    }
  } catch (err) {
    alert("Bulk demo failed: " + err.message);
  } finally {
    bulkDemoBtn.disabled = false;
    bulkDemoBtn.textContent = originalLabel;
  }
});

async function submitAndRender(subject, body) {
  if (state.mode === "compare") {
    const result = await postJson(COMPARE_URL, { subject, body });
    state.compareResults.push(result);
    renderCompareRow(result);
  } else {
    const result = await postJson(SINGLE_URL, { subject, body, engine: state.mode });
    state.singleResults.push(result);
    renderSingleRow(result);
  }
  renderStats();
}

async function postJson(url, payload) {
  const res = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || res.statusText);
  }
  return res.json();
}

function updateEmptyState() {
  const hasResults = state.mode === "compare" ? state.compareResults.length > 0 : state.singleResults.length > 0;
  emptyState.style.display = hasResults ? "none" : "";
}

function renderSingleRow(d) {
  updateEmptyState();
  const row = document.createElement("tr");
  row.className = d.action === "auto_route" ? "row-auto" : "row-review";

  row.innerHTML = `
    <td>${escapeHtml(d.subject)}</td>
    <td>${escapeHtml(d.category)} <span class="confidence">${pct(d.categoryConfidence)}</span></td>
    <td>${escapeHtml(d.urgency)} <span class="confidence">${pct(d.urgencyConfidence)}</span></td>
    <td>${d.autoResolvable ? "Yes" : "No"} <span class="confidence">${pct(d.autoResolvableConfidence)}</span></td>
    <td>${actionLabel(d.action)}</td>
    <td>${escapeHtml(d.engineUsed)}</td>
    <td>${d.latencyMs} ms</td>
  `;
  resultsBody.prepend(row);
}

function renderCompareRow(r) {
  updateEmptyState();
  const jev = r.jev;
  const rb = r.ruleBased;
  const row = document.createElement("tr");

  const categoryDiff = jev.category !== rb.category;
  const urgencyDiff = jev.urgency !== rb.urgency;
  const autoResolvableDiff = jev.autoResolvable !== rb.autoResolvable;
  const actionDiff = jev.action !== rb.action;

  row.innerHTML = `
    <td>${escapeHtml(r.subject)}</td>
    <td class="${categoryDiff ? "diff" : ""}">${compareCell(
    `${jev.category} ${pctInline(jev.categoryConfidence)}`,
    `${rb.category} ${pctInline(rb.categoryConfidence)}`
  )}</td>
    <td class="${urgencyDiff ? "diff" : ""}">${compareCell(
    `${jev.urgency} ${pctInline(jev.urgencyConfidence)}`,
    `${rb.urgency} ${pctInline(rb.urgencyConfidence)}`
  )}</td>
    <td class="${autoResolvableDiff ? "diff" : ""}">${compareCell(
    `${jev.autoResolvable ? "Yes" : "No"} ${pctInline(jev.autoResolvableConfidence)}`,
    `${rb.autoResolvable ? "Yes" : "No"} ${pctInline(rb.autoResolvableConfidence)}`
  )}</td>
    <td class="${actionDiff ? "diff" : ""}">${compareCell(actionLabel(jev.action), actionLabel(rb.action))}</td>
    <td>${compareCell(`${jev.latencyMs} ms`, `${rb.latencyMs} ms`)}</td>
  `;
  compareBody.prepend(row);
}

function compareCell(jevText, rbText) {
  return (
    `<div class="compare-line"><span class="engine-tag">Jev</span>${escapeHtml(jevText)}</div>` +
    `<div class="compare-line"><span class="engine-tag">RB</span>${escapeHtml(rbText)}</div>`
  );
}

function actionLabel(action) {
  return action === "auto_route" ? "Auto-route" : "Needs human review";
}

function pctInline(value) {
  return `(${Math.round(value * 100)}%)`;
}

function renderStats() {
  if (state.mode === "compare") {
    renderCompareStats();
  } else {
    renderSingleStats();
  }
}

function renderSingleStats() {
  const total = state.singleResults.length;
  document.getElementById("statCount").textContent = total;

  if (total === 0) {
    document.getElementById("statAutoRoute").textContent = "0%";
    document.getElementById("statConfidence").textContent = "–";
    document.getElementById("statLatency").textContent = "–";
    return;
  }

  const autoRouted = state.singleResults.filter((d) => d.action === "auto_route").length;
  const avgConfidence =
    state.singleResults.reduce(
      (sum, d) => sum + (d.categoryConfidence + d.urgencyConfidence + d.autoResolvableConfidence) / 3,
      0
    ) / total;
  const avgLatency = state.singleResults.reduce((sum, d) => sum + d.latencyMs, 0) / total;

  document.getElementById("statAutoRoute").textContent = pct(autoRouted / total);
  document.getElementById("statConfidence").textContent = pct(avgConfidence);
  document.getElementById("statLatency").textContent = `${avgLatency.toFixed(1)} ms`;
}

function renderCompareStats() {
  const total = state.compareResults.length;
  document.getElementById("statCount").textContent = total;

  if (total === 0) {
    document.getElementById("statAutoRoute").textContent = "0%";
    document.getElementById("statConfidence").textContent = "–";
    document.getElementById("statLatencyJev").textContent = "–";
    document.getElementById("statLatencyRuleBased").textContent = "–";
    document.getElementById("statAgreement").textContent = "–";
    return;
  }

  const allDecisions = state.compareResults.flatMap((r) => [r.jev, r.ruleBased]);
  const autoRouted = allDecisions.filter((d) => d.action === "auto_route").length;
  const avgConfidence =
    allDecisions.reduce(
      (sum, d) => sum + (d.categoryConfidence + d.urgencyConfidence + d.autoResolvableConfidence) / 3,
      0
    ) / allDecisions.length;
  const avgLatencyJev = state.compareResults.reduce((sum, r) => sum + r.jev.latencyMs, 0) / total;
  const avgLatencyRuleBased = state.compareResults.reduce((sum, r) => sum + r.ruleBased.latencyMs, 0) / total;
  const agreeing = state.compareResults.filter(
    (r) =>
      r.jev.category === r.ruleBased.category &&
      r.jev.urgency === r.ruleBased.urgency &&
      r.jev.action === r.ruleBased.action
  ).length;

  document.getElementById("statAutoRoute").textContent = pct(autoRouted / allDecisions.length);
  document.getElementById("statConfidence").textContent = pct(avgConfidence);
  document.getElementById("statLatencyJev").textContent = `${avgLatencyJev.toFixed(1)} ms`;
  document.getElementById("statLatencyRuleBased").textContent = `${avgLatencyRuleBased.toFixed(1)} ms`;
  document.getElementById("statAgreement").textContent = pct(agreeing / total);
}

function pct(value) {
  return `${Math.round(value * 100)}%`;
}

function escapeHtml(str) {
  const div = document.createElement("div");
  div.textContent = str;
  return div.innerHTML;
}
