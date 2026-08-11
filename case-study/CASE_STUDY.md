# Case Study Scenarios to discuss

## Scenario 1: Cost Allocation and Tracking
**Situation**: The company needs to track and allocate costs accurately across different Warehouses and Stores. The costs include labor, inventory, transportation, and overhead expenses.

**Task**: Discuss the challenges in accurately tracking and allocating costs in a fulfillment environment. Think about what are important considerations for this, what are previous experiences that you have you could related to this problem and elaborate some questions and considerations

**Questions you may have and considerations:**

The task invites me to draw on previous experience. I am going to answer instead from the
failure modes this domain actually exhibits, which are visible in the code in this repository —
that is a deliberate substitution, not an omission. The model here already contains three of the
hard parts of cost allocation, and reasoning from them is more useful than an anecdote.

**The three challenges the domain already shows.**

*1. The allocation key is a modelling decision, not an accounting one.* A warehouse fulfils
products for stores. The moment a warehouse serves more than one store, every fixed cost it
incurs — rent, supervision, utilities — has to be split by some rule: by units shipped, by
volume, by orders, by floor space reserved. The rule is arbitrary in the sense that no
measurement decides it, and consequential in the sense that it determines which stores look
profitable. The fulfilment limits in this codebase (a store may use at most three warehouses; a
warehouse may hold at most five products) mean that overlap is the normal case, not the
exception, so this is a day-one problem rather than a scaling one.

*2. Identity is not stable over time, and cost history outlives it.* Replacement reuses the
business unit code: the old warehouse is archived, a new one takes its code. Any cost report
keyed on `businessUnitCode` will silently merge two physically different warehouses. Any report
keyed on the database id will silently split what the business considers one continuing
operation. Both are defensible and they are not the same number. This is Scenario 5 seen from the
cost side, and it is the single most likely source of a wrong report here.

*3. Costs arrive later and less precisely than the events they belong to.* A shipment happens
today; the carrier invoice arrives in three weeks, aggregated across hundreds of shipments, and
may be corrected afterwards. Meanwhile labour is a rota, not a per-order fact. So the system has
to hold estimated and actual costs simultaneously, and be able to restate. A design that stores
one cost number per event cannot express that at all.

**Considerations.**

- **Separate the ledger from the allocation.** Record what was actually incurred, immutably and
  with its source document, and treat allocation as a derived view computed from a versioned
  rule. If allocation is baked into the stored numbers, changing the rule means rewriting
  history, and no one will dare.
- **Effective dating everywhere.** Every cost record needs the period it belongs to and the
  period it was booked in. Without both, a restatement is indistinguishable from a new cost.
- **Direct and allocated must stay distinguishable.** A warehouse manager will accept a direct
  cost and argue about an allocated one. Merging them into a single figure makes the report
  unusable for the conversation it exists to support.
- **Precision has a price.** Per-order labour attribution needs scanning discipline the floor may
  not have. An allocation that is 90% right and trusted beats one that is 99% right and disputed.

**Questions I would ask before scoping anything.**

- What decision is this data for? Pricing, store profitability, warehouse performance, or
  statutory reporting? Those need different granularity, and building for all four at once is how
  these projects fail.
- What is the finance system of record today, and is this tool feeding it, reading from it, or
  competing with it?
- Who owns the allocation rule, and how often has it changed in the last two years?
- When a warehouse is replaced, does the business consider costs to continue or to restart? I
  would want that answered by finance, in writing, before designing a schema — the code already
  forces the question by reusing the code.
- What is the acceptable lag between an operation and its cost appearing? That single number
  decides whether this is a streaming problem or a batch one.
- Which costs are already captured somewhere, and which are genuinely not measured yet? The
  second group is a data-collection project, not a software one, and conflating them is how the
  timeline slips.

## Scenario 2: Cost Optimization Strategies
**Situation**: The company wants to identify and implement cost optimization strategies for its fulfillment operations. The goal is to reduce overall costs without compromising service quality.

**Task**: Discuss potential cost optimization strategies for fulfillment operations and expected outcomes from that. How would you identify, prioritize and implement these strategies?

**Questions you may have and considerations:**

**Identify before optimising.** Scenario 1 is a prerequisite, not a parallel track. Optimising
against an allocation nobody trusts produces savings that exist only in the report. The first
deliverable is a cost-per-unit-shipped broken down by warehouse and by store, with the variance
between them visible. Outliers are where the money is, and they are also where the data quality
problems surface — a warehouse that looks 40% cheaper is usually mismeasured before it is
efficient.

**Strategies this domain suggests, roughly in order of expected return.**

- **Fulfilment assignment.** Which warehouse serves which store for which product is, in this
  system, an explicit and changeable association. It is also the highest-leverage decision:
  transport cost is largely a function of distance, and the model already permits up to three
  warehouses per store. Making assignment cost-aware is likely the largest single win and needs
  no new data beyond distance and volume.
- **Capacity utilisation.** Locations have a maximum capacity and warehouses have a capacity and
  a stock. The gap between capacity paid for and stock held is directly visible. `MWH.001` sits
  at a location whose maximum is 40 while itself declaring 100 — the data already contains the
  shape of this problem.
- **Inventory placement.** Holding the same product in two warehouses costs working capital and
  buys resilience and speed. The per-product-per-store limit of two warehouses is exactly this
  trade-off, encoded. Whether two is right for a given product is an economic question the system
  can now measure.
- **Labour scheduling against demand.** Usually the largest line and the slowest to change, since
  it involves contracts and people rather than configuration.

**Prioritisation.** Expected saving against implementation cost against service risk, with the
last as a veto rather than a weight. Anything that reduces cost by degrading delivery time is not
a saving, it is a transfer to the customer, and it will be reversed later at a worse price. I
would start with changes that are reversible and measurable within one cycle: reassignment can be
undone next week, closing a site cannot.

**Implementation.** Treat each strategy as a hypothesis with a metric attached and a baseline
recorded beforehand. Roll out to a subset — a region, a product family — and compare against a
control rather than against last quarter, because seasonality will otherwise take the credit.
Instrument service quality with the same seriousness as cost, in the same report, so the
trade-off is never invisible.

**Expected outcomes** worth stating honestly: transport savings from better assignment are
usually real and quickly visible; inventory savings are real but appear as a one-off working
capital release rather than a recurring cost reduction, and the two get confused; labour savings
are the largest and the slowest, and are frequently overstated because the cost is stepped rather
than continuous — you do not save a fraction of a shift.

**Questions I would ask.**

- What is the current cost per unit shipped, and how confident is finance in it?
- Is there a service-level commitment that constrains this, and is it measured today?
- Which of these levers can actually be pulled? Are warehouse leases, carrier contracts and
  staffing within scope, or is only assignment genuinely changeable?
- Is there an experiment mechanism — can we route a subset of stores differently for a month?
- Who is accountable for the saving after the project ends, and how will it be tracked once the
  attention moves on?

## Scenario 3: Integration with Financial Systems
**Situation**: The Cost Control Tool needs to integrate with existing financial systems to ensure accurate and timely cost data. The integration should support real-time data synchronization and reporting.

**Task**: Discuss the importance of integrating the Cost Control Tool with financial systems. What benefits the company would have from that and how would you ensure seamless integration and data synchronization?

**Questions you may have and considerations:**

**Why it matters.** Without integration there are two sets of numbers, and the organisation spends
its meetings reconciling them instead of acting on them. The benefit is not primarily automation,
it is that a single number stops being negotiable. Secondary benefits follow: costs appear against
operations while they can still be influenced rather than at month-end, and manual re-keying —
reliably the largest source of error in this kind of pipeline — disappears.

**This repository already contains the integration problem in miniature.** `StoreResource`
notified the legacy system from inside the transaction, so a rollback left the legacy register
believing in a store the database never kept. The fix here — capture an immutable snapshot inside
the transaction, publish it only after commit — is the same shape as any financial integration:
never tell another system about a fact you have not committed.

**What that implies for a financial integration.**

- **Only committed facts leave the system.** Post-commit publication, always.
- **At-least-once delivery, with idempotent consumption.** The implementation in this repository
  is deliberately at-most-once: if the process dies between commit and the outbound call, the
  message is lost. That is an acceptable trade for a demonstration and unacceptable for financial
  data. The fix is a transactional outbox — write the intent to a table in the same transaction as
  the change, and have a relay publish it and mark it sent. Every message then carries a stable
  business key so a replay is a no-op rather than a double booking. This is the single most
  important design decision in the scenario.
- **"Real-time" needs defining before it is designed for.** Real-time posting to a general ledger
  is usually neither possible nor wanted: ledgers have periods, closes and controls. What is
  normally meant is a real-time operational view plus a periodic, reconciled financial posting.
  Conflating the two produces a system that is late for operations and unauditable for finance.
- **Reconciliation is a feature, not an afterthought.** A scheduled comparison of totals by period
  and cost centre, with discrepancies raised as work items. Any integration without one is trusted
  right up until the day it is wrong, and then trusted by nobody.
- **A period that is closed is closed.** Late costs go to the current period with a reference to
  the original, or through an explicit restatement. Silently mutating a closed period is how audit
  trails die.
- **Master data alignment first.** Cost centres, warehouse codes and store codes have to mean the
  same thing on both sides. And a warehouse that is replaced reuses its business unit code — if
  the financial system treats that code as a permanent identity, two physically different
  warehouses merge in the ledger. That has to be settled before the first message is sent.

**Questions I would ask.**

- Which system is the record of truth for a cost, and does the answer differ between management
  and statutory reporting?
- What does finance mean by real-time? What is the actual latency requirement, and is it driven by
  a decision or by a preference?
- What are the close calendar and the cut-off rules, and what currently happens to a late cost?
- Can the financial system accept events, or is it batch-file only? That changes the design more
  than any requirement in the brief.
- Is there an existing canonical cost-centre hierarchy, and does it survive a warehouse
  replacement?
- What audit and retention requirements apply? They usually decide the storage design outright.
- What is the volume — thousands of cost lines a day, or millions? The correct architecture
  differs by an order of magnitude, and this is the cheapest question to ask.

## Scenario 4: Budgeting and Forecasting
**Situation**: The company needs to develop budgeting and forecasting capabilities for its fulfillment operations. The goal is to predict future costs and allocate resources effectively.

**Task**: Discuss the importance of budgeting and forecasting in fulfillment operations and what would you take into account designing a system to support accurate budgeting and forecasting?

**Questions you may have and considerations:**

**Why it matters here.** Fulfilment costs are mostly committed in advance — leases, headcount,
carrier contracts — while revenue arrives with demand. A forecast is what turns a capacity
decision from a guess into a bet with known odds. In this domain it is also structural: locations
have hard warehouse and capacity limits, so expansion is not incremental. You cannot add 10% of a
warehouse, and `TILBURG-001` permits exactly one. Knowing a limit will be hit two quarters early
is the difference between a negotiation and an emergency.

**Design considerations.**

- **Cost behaviour, not just cost totals.** The model has to know which costs are fixed, which are
  variable per unit, and which are stepped. Fulfilment is dominated by stepped costs: a shift, a
  vehicle, a building. A linear model of a stepped cost is smooth, plausible and wrong exactly at
  the decision points where it is consulted.
- **Drivers before money.** Forecast volume by store and product first, then translate to cost
  through the same allocation rules Scenario 1 establishes. Forecasting money directly hides the
  assumption that is actually being made and makes variance impossible to explain.
- **Variance analysis is the product.** A budget is only useful if the difference between plan and
  actual can be decomposed — volume, rate, mix, efficiency. "We are 12% over" starts an argument;
  "volume was 8% up and cost per unit 4% up" starts a decision. This decomposition should be a
  first-class feature, not a spreadsheet someone maintains.
- **Rolling forecasts alongside the annual budget.** The budget is a commitment, the forecast is a
  belief, and they should be allowed to disagree visibly. Overwriting one with the other destroys
  the accountability that made the budget worth setting.
- **Versioning and auditability.** Every forecast keeps its assumptions and its author. Six months
  on, the useful question is not what the number was, but what we believed when we set it.
- **Scenario support with few, meaningful levers.** Volume growth, a site opening, a carrier rate
  change. A model with forty adjustable parameters is not more accurate, it is unfalsifiable.
- **Seasonality and event calendars.** Fulfilment is highly seasonal and any model ignoring it is
  wrong twice a year, in the two periods that matter most.
- **Feed capacity constraints back in.** The forecast should be checked against the location limits
  the system already enforces. A forecast that implies a fourth warehouse at a location permitting
  one is not a forecast, it is a capital request, and the system should say so.

**Questions I would ask.**

- What is the planning cycle and who owns the budget — central finance, or warehouse managers?
  That decides whether this is a top-down or bottom-up tool, and they are different products.
- What forecast accuracy is achieved today, and what would count as an improvement? Without a
  baseline, this cannot be evaluated.
- How far ahead do decisions need to be made? Lease terms and hiring lead times set the horizon,
  not preference.
- Is there clean historical volume and cost data, and for how long? A forecast needs several
  seasons; if the data starts eighteen months ago, that constrains the approach entirely.
- Are budgets set per warehouse, per store, or per cost centre — and again, what happens to a
  warehouse's budget when it is replaced mid-year?
- Who is accountable when actuals diverge, and what action is expected? A forecast nobody is
  answerable for degrades into a formality.

## Scenario 5: Cost Control in Warehouse Replacement
**Situation**: The company is planning to replace an existing Warehouse with a new one. The new Warehouse will reuse the Business Unit Code of the old Warehouse. The old Warehouse will be archived, but its cost history must be preserved.

**Task**: Discuss the cost control aspects of replacing a Warehouse. Why is it important to preserve cost history and how this relates to keeping the new Warehouse operation within budget?

**Questions you may have and considerations:**

This scenario is implemented in this repository, so the answer is concrete rather than
hypothetical.

**What the implementation does.** `POST /warehouse/{businessUnitCode}/replacement` archives the
predecessor and creates the successor under the same code, in one transaction. Archiving stamps
`archivedAt` and nothing else: the row stays in the database, keeps its own numeric id, its
capacity, its stock and its `createdAt`, and is excluded from every read path. Nothing is deleted.
Two replacement-specific rules run first, while the predecessor is still active — the successor's
capacity must accommodate the stock it inherits, and its stock must match — so a bad request is
rejected before anything is archived. If a later rule rejects the successor, the transaction rolls
the archival back, and there is a test asserting the predecessor survives that exact path.

**Why preserving the history matters, in cost terms.**

- **Comparability is the entire justification for the replacement.** The business case says the new
  site is cheaper or faster. That claim can only be tested against the old site's actual cost per
  unit, at real volumes, across a full season. Delete the history and the project can never be
  evaluated — which, in practice, means it will be declared a success.
- **The budget is a continuation, not a fresh start.** The successor inherits the predecessor's
  stock and its business unit code, and usually its stores and volumes too. Its budget should be
  derived from the predecessor's run rate, adjusted for the specific changes the business case
  claims. Without the history there is no defensible starting number, and the budget becomes
  whatever was requested.
- **Transition costs are real and land on nobody by default.** Moving stock, running two sites in
  parallel, ramping labour: these are one-off costs that belong to the replacement, not to the new
  warehouse's steady-state performance. If they are charged to the successor's code they make it
  look permanently expensive; if they are dropped they make the project look free. They need their
  own bucket, and the archival boundary is what makes that possible.
- **Audit and restatement.** A cost booked to the code last quarter belongs to the predecessor.
  Anything that answers "what did this cost in Q2" has to resolve to the entity that existed then.

**The trap, and it is specific.** Reusing the business unit code means the code is *not* a unique
identifier over time — it is unique only among active warehouses, which is exactly what the
implementation enforces. Any cost report grouping by `businessUnitCode` will merge two physically
different warehouses into one series and produce a step change nobody can explain. Any report
grouping by database id will split what the business calls one continuing operation. Both are
wrong for some question and right for another, which is why the system keeps both: the code for
continuity, the id and `archivedAt`/`createdAt` for the boundary.

The practical consequence is that cost records must reference the **warehouse instance**, not the
code, and reporting must join through the code deliberately when continuity is what is wanted.
Effective dating makes that automatic: every warehouse row already carries a `createdAt` and an
`archivedAt`, which is precisely the interval a cost record's date can be matched against. The
grandfathered `MWH.001` — seeded at a location whose maximum capacity is 40 while declaring 100 —
is a live example of why history is kept rather than corrected: the system stops new violations and
preserves the old fact instead of pretending it never happened.

**Questions I would ask.**

- Does finance consider the successor a continuation of the same cost centre or a new one? This
  single answer determines the reporting design and is a business decision, not a technical one.
- How should transition costs be treated — capitalised, expensed to the project, or absorbed by the
  successor?
- Is there an overlap period where both warehouses operate? The current model permits only one
  active warehouse per business unit code at a time, so a genuine dual-running period needs either a
  temporary second code or an explicit change to that rule. Worth settling before the move, not
  during it.
- What is the expected payback period, and who reviews the actual against it, and when? A
  replacement without a scheduled post-implementation review is an unmeasured bet.
- How long must archived cost history be retained, and is it ever purged? That answer constrains
  the storage design and should be known before the first replacement rather than after the first
  audit.

## Instructions for Candidates
Before starting the case study, read the [BRIEFING.md](BRIEFING.md) to quickly understand the domain, entities, business rules, and other relevant details.

**Analyze the Scenarios**: Carefully analyze each scenario and consider the tasks provided. To make informed decisions about the project's scope and ensure valuable outcomes, what key information would you seek to gather before defining the boundaries of the work? Your goal is to bridge technical aspects with business value, bringing a high level discussion; no need to deep dive.
