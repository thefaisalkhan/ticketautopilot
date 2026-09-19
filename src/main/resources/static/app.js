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
const importBtn = document.getElementById("importBtn");
const importBtnLabel = document.getElementById("importBtnLabel");
const importInput = document.getElementById("importInput");
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

  setBusy(true);
  try {
    await submitAndRender(subject, body);
    form.reset();
  } catch (err) {
    alert("Failed to submit ticket: " + err.message);
  } finally {
    setBusy(false);
  }
});

bulkDemoBtn.addEventListener("click", async () => {
  setBusy(true);
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
    setBusy(false);
    bulkDemoBtn.textContent = originalLabel;
  }
});

importInput.addEventListener("change", async () => {
  const file = importInput.files[0];
  importInput.value = ""; // allow re-selecting the same file later
  if (!file) return;

  let tickets;
  try {
    tickets = ticketsFromCsv(await file.text());
  } catch (err) {
    alert("Could not parse CSV: " + err.message);
    return;
  }

  if (tickets.length === 0) {
    alert("No valid rows found. Expected a 'Subject' column and a 'Description' (or 'Body') column.");
    return;
  }

  const callCount = state.mode === "compare" ? tickets.length * 2 : tickets.length;
  if (tickets.length > 30 && !confirm(`Import ${tickets.length} tickets? This makes ${callCount} triage call(s).`)) {
    return;
  }

  setBusy(true);
  const originalLabel = importBtnLabel.textContent;
  try {
    for (let i = 0; i < tickets.length; i++) {
      importBtnLabel.textContent = `Importing (${i + 1}/${tickets.length})...`;
      await submitAndRender(tickets[i].subject, tickets[i].body);
    }
  } catch (err) {
    alert("Import failed: " + err.message);
  } finally {
    setBusy(false);
    importBtnLabel.textContent = originalLabel;
  }
});

function setBusy(busy) {
  submitBtn.disabled = busy;
  bulkDemoBtn.disabled = busy;
  importBtn.classList.toggle("disabled", busy);
}

// Minimal RFC4180-ish CSV parser: handles quoted fields, embedded commas/newlines,
// and "" as an escaped quote. Freshdesk's own ticket export (and most CSV exports)
// produce exactly this shape.
function parseCsv(text) {
  const rows = [];
  let row = [];
  let field = "";
  let inQuotes = false;

  for (let i = 0; i < text.length; i++) {
    const char = text[i];

    if (inQuotes) {
      if (char === '"' && text[i + 1] === '"') {
        field += '"';
        i++;
      } else if (char === '"') {
        inQuotes = false;
      } else {
        field += char;
      }
      continue;
    }

    if (char === '"') {
      inQuotes = true;
    } else if (char === ",") {
      row.push(field);
      field = "";
    } else if (char === "\r") {
      // skip; \n (below) ends the row
    } else if (char === "\n") {
      row.push(field);
      rows.push(row);
      row = [];
      field = "";
    } else {
      field += char;
    }
  }
  if (field.length > 0 || row.length > 0) {
    row.push(field);
    rows.push(row);
  }
  return rows;
}

function ticketsFromCsv(text) {
  const rows = parseCsv(text).filter((r) => r.some((cell) => cell.trim() !== ""));
  if (rows.length < 2) return [];

  const header = rows[0].map((h) => h.trim().toLowerCase());
  const subjectIdx = header.indexOf("subject");
  const bodyIdx = header.findIndex((h) => h === "description" || h === "body");
  if (subjectIdx === -1 || bodyIdx === -1) {
    throw new Error("Missing a 'Subject' and/or 'Description'/'Body' column header.");
  }

  const tickets = [];
  for (let i = 1; i < rows.length; i++) {
    const subject = (rows[i][subjectIdx] || "").trim();
    const body = (rows[i][bodyIdx] || "").trim();
    if (subject && body) {
      tickets.push({ subject, body });
    }
  }
  return tickets;
}

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
    <td${detailAttrs(d.categoryProbabilities)}>${escapeHtml(d.category)} <span class="confidence">${pct(d.categoryConfidence)}</span></td>
    <td${detailAttrs(d.urgencyProbabilities)}>${escapeHtml(d.urgency)} <span class="confidence">${pct(d.urgencyConfidence)}</span></td>
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
    `${rb.category} ${pctInline(rb.categoryConfidence)}`,
    jev.categoryProbabilities
  )}</td>
    <td class="${urgencyDiff ? "diff" : ""}">${compareCell(
    `${jev.urgency} ${pctInline(jev.urgencyConfidence)}`,
    `${rb.urgency} ${pctInline(rb.urgencyConfidence)}`,
    jev.urgencyProbabilities
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

// jevProbabilities is only ever available on Jev's own line (the rule-based
// engine has no real distribution), so the hover detail only ever appears there.
function compareCell(jevText, rbText, jevProbabilities) {
  const formatted = formatProbabilities(jevProbabilities);
  const jevClass = formatted ? "compare-line has-detail" : "compare-line";
  const jevTitle = formatted ? ` title="${escapeAttr(formatted)}"` : "";
  return (
    `<div class="${jevClass}"${jevTitle}><span class="engine-tag">Jev</span>${escapeHtml(jevText)}</div>` +
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

// Jev's Choice/Score answers include a full probability distribution, not
// just the winning value — formatted here for a hover tooltip, sorted so
// the most likely alternatives show first. Only Jev ever produces these;
// the rule-based engine passes undefined/null and gets no tooltip.
function formatProbabilities(map) {
  if (!map) return "";
  return Object.entries(map)
    .sort((a, b) => b[1] - a[1])
    .map(([label, value]) => `${label}: ${pct(value)}`)
    .join(", ");
}

function detailAttrs(map) {
  const formatted = formatProbabilities(map);
  if (!formatted) return "";
  return ` class="has-detail" title="${escapeAttr(formatted)}"`;
}

function escapeHtml(str) {
  const div = document.createElement("div");
  div.textContent = str;
  return div.innerHTML;
}

function escapeAttr(str) {
  return escapeHtml(str).replace(/"/g, "&quot;");
}
