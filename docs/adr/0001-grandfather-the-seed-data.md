# ADR-0001: Grandfather the seed data; validate on write only

**Status:** accepted

## Context

`Location` documents `maxCapacity` as "maximum capacity of the location summing all the warehouse
capacities". `ZWOLLE-001` is declared `maxNumberOfWarehouses=1, maxCapacity=40`, yet `import.sql`
seeds `MWH.001` there with capacity **100** — two and a half times its location limit. The seed data
therefore contradicts the rules the assignment asks us to enforce.

## Decision

Validate on write only. Never retro-validate existing rows, and do not edit `import.sql`.

## Alternatives considered

- **Edit `import.sql` so the fixtures obey the rules.** Rejected. It makes the implementation look
  correct by quietly rewriting the evidence, and the contradiction is conspicuous enough to be
  deliberate — a reviewer may well be checking whether it is noticed.
- **Validate existing rows on read and reject or repair them.** Rejected. It turns a historical fact
  into a runtime failure for data the system already accepted.

## Consequences

- Pre-existing violations persist and are visible; the system stops new ones.
- Any test that replaces `MWH.001` must use a capacity ≤ 40. Two tests encode exactly this
  (`theSeededOverCapacityWarehouseCannotBeReplacedAtItsOwnCapacity` and
  `…CanBeReplacedWithinItsLocationLimit`), so the grandfathering is executable rather than a note.
- Replacing `MWH.001` at its current capacity is impossible. That is the correct behaviour: the
  location cannot legitimately hold it.
