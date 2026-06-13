# HCI + LLM Research Roadmap

**Core question:** How can AI systems help people while preserving user agency, control, trust, and understanding?

---

## Stage 1: Learn how HCI research thinks (6 months)

Read CHI papers regularly — not passively. For every paper ask:

- What question are they asking?
- Why is that question interesting?
- What method did they use?
- What are the weaknesses?
- What would I do differently?

Store each review in a public repo: `llm-hci-reading-notes`

### Reading list (15 papers)

**Week 1 — Foundation**
1. Shneiderman — *Human-Centered Artificial Intelligence: Reliable, Safe & Trustworthy* (2020)
2. Amershi et al. — *Human-AI Interaction Guidelines* (CHI 2019)

**Week 2 — Trust and Transparency**
3. Ribeiro, Singh, Guestrin — *Why Should I Trust You?* (LIME, 2016)
4. Lipton — *The Mythos of Model Interpretability* (2016)

**Week 3 — AI Assistants and Memory**
5. Park et al. — *Generative Agents: Interactive Simulacra of Human Behavior* (2023)
6. Yao et al. — *ReAct: Synergizing Reasoning and Acting in Language Models* (2022)

**Week 4 — LLM Evaluation**
7. Liang et al. — *HELM: Holistic Evaluation of Language Models* (2022)
8. Microsoft Research — *Sparks of Artificial General Intelligence* (2023)

**Then continue into:** CHI, CSCW, and IUI proceedings.

For every paper write: Research Question | Methodology | Findings | Limitations | What I Would Test Next

---

## Stage 2: Replicate before innovating (6–12 months)

Pick an HCI paper. Rebuild the prototype. Repeat the experiment on a small scale.

Ask: *Does this result still hold when using LLMs?*

---

## Stage 3: Turn Watney4 into a research platform

Add instrumentation to compare conditions:

- citations vs no citations
- visible memory vs hidden memory
- confidence scores vs uncertainty statements
- explanation modes

Each feature becomes an independent variable for a user study.

---

## Stage 4: Learn study design

- qualitative interviews
- surveys
- thematic analysis
- experimental design
- within-subject studies
- between-subject studies

---

## Stage 5: Build a visible research trail

```
2026
├── 30 paper reviews
├── 3 experiments
├── 1 replication
└── Watney4 evaluations

2027
├── 60 paper reviews
├── 5 experiments
├── 2 replications
├── small user studies
└── stronger methodology

2028
├── mature research portfolio
├── established theme
└── potential publication attempt
```

---

## GitHub Portfolio

| Area | Repos |
|------|-------|
| Flagship Systems | Watney4, Debby, RailSync |
| Research Lab | llm-hci-research-lab, paper notes, experiments, evaluations |
| Infrastructure | local-ai-lab, ProDesk build, benchmarks, deployment notes |
| Writing / Case Studies | blog-linked repos, technical reports, experiment summaries |

---

## How Watney4 supports this

| Task | Tools |
|------|-------|
| Reading papers | `web_search` → find papers, `web_fetch` → get abstracts, `write` → save notes |
| Literature review | `cron` for daily arxiv digests, `save_memory` for cross-session knowledge |
| Running experiments | `bash` to launch scripts, `opencode` to write analysis code |
| Note-taking | `write` → Obsidian vault, `context_inject` for working summaries |
| Experiment monitoring | `cron` for periodic status checks, `remind` for one-shot notifications |
| Data analysis | `bash` → Python/R, `opencode` for complex script generation, `grep`/`glob` for data files |
| Resource checks | `system_status` before/during experiments |
