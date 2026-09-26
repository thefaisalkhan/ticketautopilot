const jobForm = document.getElementById("jobForm");
const jobTitleInput = document.getElementById("jobTitle");
const jobDescriptionInput = document.getElementById("jobDescription");
const candidateList = document.getElementById("candidateList");
const addCandidateBtn = document.getElementById("addCandidateBtn");
const loadSampleBtn = document.getElementById("loadSampleBtn");
const rankBtn = document.getElementById("rankBtn");
const resultsBody = document.getElementById("resultsBody");
const emptyState = document.getElementById("emptyState");

let candidateSeq = 0;

function addCandidateRow(name = "", resume = "") {
  candidateSeq += 1;
  const id = candidateSeq;

  const row = document.createElement("div");
  row.className = "candidate-row";
  row.dataset.id = String(id);
  row.innerHTML = `
    <div class="candidate-row-fields">
      <input type="text" class="candidate-name" placeholder="Candidate name" value="${escapeAttr(name)}" required />
      <textarea class="candidate-resume" rows="3" placeholder="Paste resume text..." required>${escapeHtml(resume)}</textarea>
    </div>
    <button type="button" class="remove-candidate-btn" title="Remove candidate">&times;</button>
  `;
  row.querySelector(".remove-candidate-btn").addEventListener("click", () => row.remove());
  candidateList.appendChild(row);
}

addCandidateBtn.addEventListener("click", () => addCandidateRow());

loadSampleBtn.addEventListener("click", () => {
  jobTitleInput.value = SAMPLE_JOB.title;
  jobDescriptionInput.value = SAMPLE_JOB.description;
  candidateList.innerHTML = "";
  SAMPLE_CANDIDATES.forEach((c) => addCandidateRow(c.name, c.resume));
});

jobForm.addEventListener("submit", async (event) => {
  event.preventDefault();

  const jobTitle = jobTitleInput.value.trim();
  const jobDescription = jobDescriptionInput.value.trim();
  const candidates = Array.from(candidateList.querySelectorAll(".candidate-row"))
    .map((row) => ({
      name: row.querySelector(".candidate-name").value.trim(),
      resume: row.querySelector(".candidate-resume").value.trim(),
    }))
    .filter((c) => c.name && c.resume);

  if (!jobTitle || !jobDescription) {
    alert("Fill in the job title and description first.");
    return;
  }
  if (candidates.length === 0) {
    alert("Add at least one candidate with a name and resume.");
    return;
  }

  setBusy(true);
  try {
    const res = await fetch("/api/candidate-ranking", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ jobTitle, jobDescription, candidates }),
    });
    if (!res.ok) {
      throw new Error(await res.text());
    }
    const results = await res.json();
    renderResults(results);
  } catch (err) {
    alert("Ranking failed: " + err.message);
  } finally {
    setBusy(false);
  }
});

function renderResults(results) {
  resultsBody.innerHTML = "";
  results.forEach((r, index) => {
    const tr = document.createElement("tr");
    tr.className = rowClassFor(r.fitTier);

    const scorePct = Math.round((r.matchScoreExpectedValue / 3) * 100);

    tr.innerHTML = `
      <td>${index + 1}</td>
      <td>${escapeHtml(r.candidateName)}</td>
      <td><span class="tier-badge tier-${r.fitTier}">${tierLabel(r.fitTier)}</span>
        <span class="confidence">${pct(r.fitTierConfidence)}</span></td>
      <td>
        <div class="score-bar-wrap" title="${escapeAttr(formatProbabilities(r.matchScoreProbabilities))}">
          <div class="score-bar"><div class="score-bar-fill" style="width:${scorePct}%"></div></div>
          <span class="score-label">${escapeHtml(r.matchScoreLabel)}</span>
        </div>
      </td>
      <td>${r.recommendInterview ? "Yes" : "No"} <span class="confidence">${pct(r.recommendInterviewConfidence)}</span></td>
      <td>${r.latencyMs} ms</td>
    `;
    resultsBody.appendChild(tr);
  });
  updateEmptyState(results.length > 0);
}

function rowClassFor(fitTier) {
  if (fitTier === "strong_fit") return "row-strong-fit";
  if (fitTier === "potential_fit") return "row-potential-fit";
  return "row-not-a-fit";
}

function tierLabel(fitTier) {
  return { strong_fit: "Strong fit", potential_fit: "Potential fit", not_a_fit: "Not a fit" }[fitTier] || fitTier;
}

function pct(value) {
  return `${Math.round(value * 100)}%`;
}

function formatProbabilities(probabilities) {
  if (!probabilities) return "";
  return Object.entries(probabilities)
    .map(([label, p]) => `${label}: ${pct(p)}`)
    .join(" · ");
}

function updateEmptyState(hasResults) {
  emptyState.style.display = hasResults ? "none" : "";
}

function setBusy(busy) {
  rankBtn.disabled = busy;
  addCandidateBtn.disabled = busy;
  loadSampleBtn.disabled = busy;
  rankBtn.textContent = busy ? "Ranking..." : "Rank candidates";
}

function escapeHtml(value) {
  const div = document.createElement("div");
  div.textContent = value;
  return div.innerHTML;
}

function escapeAttr(value) {
  return escapeHtml(value).replaceAll('"', "&quot;");
}

addCandidateRow();
addCandidateRow();
