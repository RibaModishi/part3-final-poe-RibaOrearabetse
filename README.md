# Pocket Watch App

Pocket Watch is a mobile budgeting and personal finance tracker designed to help users manage spending, set goals, and build sustainable financial habits.  
The app combines structured budgeting, expense tracking, goals, rewards/gamification, reports, and support content in one experience.

## Group Members

| Name | Student Number | Group |
|---|---|---|
| Joy Chivava | ST10453506 | 3 |
| Khensani Maluleke | ST10451309 | 2 |
| Khumo-Thato Chabeli | ST10448834 | 3 |
| Orearabetse Riba | ST10446648 | 2 |

## Team Responsibilities

- **Business Logic (ViewModels / flow logic): Joy + Riba**
  - Core ViewModel logic for auth, expenses, budgets, goals, rewards, reports, and screen-state handling.
  - Input validation, filtering, calculations, progress/status logic, and navigation behavior wiring.
- **Database Layer: Khumo**
  - Room entities/DAOs, schema evolution, and migration-focused data safety.
  - Data relationships and persistence support for all app modules.
- **User Interface Layer: Khensani**
  - Visual design language, layouts, component styling, icon/asset guidance, and page-level UI direction.
  - UX consistency across launch/auth/dashboard/category/expense/budget/goals/rewards/reports/help pages.

## Project Overview

Pocket Watch supports practical financial management through:

- **Authentication**
  - Registration and login with validation and secure handling patterns.
- **Expense Tracking**
  - Add/edit/delete-style flows, date/category filtering, totals, and receipt support.
- **Budgeting**
  - Monthly budget planning, category allocations, and summary indicators.
- **Savings Goals**
  - Goal creation, progress updates, pinning, delete flow, and progress history views.
- **Rewards / Gamification**
  - XP/levels, badges, level journey, and achievement statistics.
- **Reports**
  - Daily/weekly/category views, spending summaries, trend blocks, category breakdowns.
- **Info & Help**
  - Expandable FAQ support screen with grouped self-service answers.
- **Navigation**
  - Functional top hamburger menus and bottom navigation patterns across major screens.

## Technical Approach

- **Platform:** Android (Kotlin, XML layouts)
- **Architecture:** Activity + ViewModel + Room (clean separation of UI, state logic, persistence)
- **Persistence:** Room database with migration support
- **Async/Data flow:** Coroutines + lifecycle-aware observation
- **Testing direction:** Unit/instrumentation coverage for critical logic and migration behavior
- **Security direction:** Password hashing and safer auth/data handling patterns

## Major Challenges We Faced

This project had significant integration complexity. Key struggle areas included:

1. **Database migration and data safety**
   - Needed to add schema changes without destroying existing user data.
   - Required moving away from destructive migration fallback behavior and validating migration correctness.
2. **Login breakages after schema/data-flow changes**
   - Authentication issues appeared after migration/seed changes.
   - Required aligning hashing flow, default test user handling, and query behavior.
3. **UI integration vs existing logic**
   - Large challenge in applying the designed UI while preserving existing business logic and IDs.
   - Many screens required full layout redesign without breaking bound logic.
4. **Resource/build failures**
   - XML/string encoding problems (special characters/unicode escape issues).
   - Resource linking errors and missing references during rapid UI iteration.
5. **Navigation consistency**
   - Ensuring all hamburger menus and bottom nav elements were truly functional across screens.
   - Adding icon-enabled popup menu behavior consistently.
6. **Reports and visual sections**
   - Matching design intent for trend sections and breakdown visuals while still using real app data.
   - Iterative tuning for readability, spacing, and behavior.
7. **Environment/toolchain instability**
   - JDK/JAVA_HOME setup friction.
   - Gradle daemon crashes and memory/paging-file limitations interrupted builds and verification cycles.
8. **Collaborative alignment**
   - Synchronizing database work, UI references, and business-logic changes from multiple contributors in parallel.
   - Keeping commits coherent while the tree had unrelated local/IDE changes.

## How AI Support Was Used

To support delivery speed and troubleshooting, the team used Claude and DeepSeek for:

- explaining compile/runtime errors and likely causes,
- comparing alternative implementation approaches,
- improving code structure/readability suggestions,
- generating/refining draft text for documentation sections.

All final integration decisions, validation, and project-specific tailoring were done within the team context.

## What We Learned

- Non-destructive migration planning is essential early in Android projects.
- UI redesigns are fastest when IDs/contracts are planned from the start.
- Functional navigation consistency requires deliberate shared patterns.
- Build environment stability can become a delivery risk if not managed proactively.
- Clear role ownership helps, but frequent integration checkpoints are critical.

## YouTube Demo Link

- [Pocket Watch Demo 1](https://youtu.be/8eNBxrmB6-I)
- [Pocket Watch Demo 2](https://youtu.be/Yb3H48hMdGM)

## Suggested README Add-ons (Optional)

If your lecturer requires more detail, you can add:

- Installation/run steps
- Known limitations
- Test plan summary
- Reflection per member
- References list (if required by your submission rubric)
