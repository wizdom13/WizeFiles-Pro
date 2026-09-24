# Classified engineering debt

Production `TODO`, `FIXME`, and `HACK` markers are governed by
`config/engineering-debt.tsv`. Each path is assigned a category, priority, owner, and outcome-based
exit criterion. `scripts/validate_engineering_debt.py` fails when a marker is unclassified or when
the approved total grows, preventing provider correctness work from being hidden among cosmetic
notes.

Priority meanings follow the risk register: P1 covers provider correctness, destructive-operation
semantics, and bounded external behavior; P2 covers lifecycle, platform, and maintainability work;
P3 covers localized compatibility cleanup. A new P0 issue belongs in `TODO_RISK_REGISTER.md` and
the blocking release gates, not this registry.

When resolving debt, remove the marker in the same change as its outcome-based regression test.
Lower `maximum_markers` whenever the repository total falls. New markers require an explicit rule
or must fit an existing owned category without increasing the approved total.

P1 rules must name exact source files; broad directory prefixes are rejected by the validator. This
keeps provider and destructive-operation exit criteria specific enough to review independently.
