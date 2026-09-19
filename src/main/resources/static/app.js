const API_URL = "/api/tickets";

const state = {
  decisions: [],
};

const form = document.getElementById("ticketForm");
const subjectInput = document.getElementById("subject");
const bodyInput = document.getElementById("body");
const submitBtn = document.getElementById("submitBtn");
const bulkDemoBtn = document.getElementById("bulkDemoBtn");
const resultsBody = document.getElementById("resultsBody");
const emptyState = document.getElementById("emptyState");

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  const subject = subjectInput.value.trim();
  const body = bodyInput.value.trim();
  if (!subject || !body) return;

  submitBtn.disabled = true;
  try {
    const decision = await submitTicket(subject, body);
    addDecision(decision);
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
      const decision = await submitTicket(sample.subject, sample.body);
      addDecision(decision);
    }
  } catch (err) {
    alert("Bulk demo failed: " + err.message);
  } finally {
    bulkDemoBtn.disabled = false;
    bulkDemoBtn.textContent = originalLabel;
  }
});

async function submitTicket(subject, body) {
  const res = await fetch(API_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ subject, body }),
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || res.statusText);
  }
  return res.json();
}

function addDecision(decision) {
  state.decisions.push(decision);
  renderRow(decision);
  renderStats();
}

function renderRow(d) {
  emptyState.style.display = "none";
  const row = document.createElement("tr");
  row.className = d.action === "auto_route" ? "row-auto" : "row-review";

  row.innerHTML = `
    <td>${escapeHtml(d.subject)}</td>
    <td>${escapeHtml(d.category)} <span class="confidence">${pct(d.categoryConfidence)}</span></td>
    <td>${escapeHtml(d.urgency)} <span class="confidence">${pct(d.urgencyConfidence)}</span></td>
    <td>${d.autoResolvable ? "Yes" : "No"} <span class="confidence">${pct(d.autoResolvableConfidence)}</span></td>
    <td>${d.action === "auto_route" ? "Auto-route" : "Needs human review"}</td>
    <td>${escapeHtml(d.engineUsed)}</td>
    <td>${d.latencyMs} ms</td>
  `;
  resultsBody.prepend(row);
}

function renderStats() {
  const total = state.decisions.length;
  document.getElementById("statCount").textContent = total;

  if (total === 0) {
    document.getElementById("statAutoRoute").textContent = "0%";
    document.getElementById("statConfidence").textContent = "–";
    document.getElementById("statLatency").textContent = "–";
    return;
  }

  const autoRouted = state.decisions.filter((d) => d.action === "auto_route").length;
  const avgConfidence =
    state.decisions.reduce(
      (sum, d) => sum + (d.categoryConfidence + d.urgencyConfidence + d.autoResolvableConfidence) / 3,
      0
    ) / total;
  const avgLatency = state.decisions.reduce((sum, d) => sum + d.latencyMs, 0) / total;

  document.getElementById("statAutoRoute").textContent = pct(autoRouted / total);
  document.getElementById("statConfidence").textContent = pct(avgConfidence);
  document.getElementById("statLatency").textContent = `${avgLatency.toFixed(1)} ms`;
}

function pct(value) {
  return `${Math.round(value * 100)}%`;
}

function escapeHtml(str) {
  const div = document.createElement("div");
  div.textContent = str;
  return div.innerHTML;
}
