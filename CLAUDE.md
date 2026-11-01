# Family Finance Manager

Private Android app (Kotlin, Jetpack Compose, Material 3) for two users.
Full spec: docs/PRD.md. Read it before starting any task.

## Non-negotiable rules
- Money is always Long paise via a Money value class. Never Float or Double.
- Balances are derived from opening balance + transactions. Never stored.
- Transfers never count as income or expense.
- No user-visible strings in Kotlin/Compose. Use string resources:
  res/values/strings.xml (en) and res/values-ta/strings.xml (Tamil).
- No business logic in composables. Logic lives in the domain package.
- Verify dependency versions against official docs. Never invent versions.
- Domain code is pure Kotlin with no Android or Firebase imports.
- Every financial rule needs a unit test. Run tests before saying a task is done.

## Working style
- Work on one phase at a time and stop at the end of each phase.
- Show test results when finishing.
- Ask me before making architecture decisions not covered in the PRD.