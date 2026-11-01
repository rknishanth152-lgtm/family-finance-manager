# FAMILY FINANCE MANAGER

## Product Requirements Document (PRD)

**Version:** 1.0
**Product Type:** Private, native Android application
**Primary Users:** Father and mother
**Primary Market:** Personal and family finance management in India
**Primary Language:** Tamil
**Secondary Language:** English
**Currency:** Indian Rupee (INR)
**Distribution:** Private APK installation
**Cloud Platform:** Firebase
**Development Approach:** AI-assisted development using Claude Code and Android Studio

---

# 1. EXECUTIVE SUMMARY

## 1.1 Product Overview

Family Finance Manager is a private Android application designed to replace an existing income-expense management application currently used by the user's father.

The application will provide a modern, accessible, Tamil-first interface for recording and managing family income, expenses, account transfers, balances, financial notes, and reports.

Two authorized users, initially the user's father and mother, must be able to access the same financial workspace from separate Android phones.

Transactions entered on one device should synchronize with the other device automatically when an internet connection is available.

The application must support offline transaction entry, persistent local data, downloadable statements, backup and restoration, and migration of historical financial records from the existing application.

The application is intended exclusively for private use. Public Play Store distribution is not required.

## 1.2 Problem Statement

The existing application already supports important financial management functions, but its interface can be improved in several ways:

* Navigation is unnecessarily complicated.
* Financial summaries compete with transaction details for screen space.
* Recording a transaction can involve more steps than necessary.
* Tamil support must be treated as a first-class requirement.
* Accessing the same financial records across two phones requires a reliable shared-data architecture.
* Financial records must remain portable through exports and backups.
* Historical data must be preserved during migration.

The new application must retain the useful capabilities of the existing application while improving usability, language support, data integrity, and synchronization.

## 1.3 Product Vision

Create a simple, dependable family finance application that allows two people to record financial activity in Tamil or English, understand their family's financial position, and retrieve accurate financial records without depending on complicated navigation.

The application must prioritize correctness and reliability over decorative features.

## 1.4 Core Product Principles

1. Tamil is a primary product language, not an optional translation.
2. Financial records must never be silently discarded.
3. Account transfers must not be incorrectly counted as income or expenses.
4. Every balance must be derived from consistent financial records.
5. Users must be able to record transactions quickly.
6. Both phones must work with the same authorized financial workspace.
7. Offline activity must be handled transparently.
8. Users must be able to export and restore their data.
9. The application must not require a public Play Store listing.
10. Every important financial calculation must have automated tests.

---

# 2. PRODUCT OBJECTIVES

## 2.1 Primary Objectives

The application must:

* Replace the essential daily workflows of the existing expense manager.
* Support two authorized users accessing the same family financial records.
* Provide Tamil and English interfaces.
* Record income, expenses, and account transfers.
* Maintain accurate account and family balances.
* Support transaction history, searching, and filtering.
* Provide calendar and notebook features.
* Generate PDF, CSV, and XLSX statements.
* Support local data persistence and cloud synchronization.
* Preserve historical transactions during migration.
* Support backup and restoration.
* Be installable privately on Android devices.

## 2.2 Secondary Objectives

The application should eventually support:

* Recurring transactions.
* Monthly budgets.
* Saved transaction templates.
* Receipt attachments.
* Loan tracking.
* Payment reminders.
* Advanced financial analytics.
* Voice-based transaction entry.
* Automated receipt recognition.

These features must not delay the delivery of a reliable core application.

## 2.3 Non-Goals

The initial release will not attempt to provide:

* Direct bank-account integration.
* Automatic access to banking credentials.
* Automatic reading of SMS messages.
* UPI payment initiation.
* Stock trading or investment execution.
* Tax filing.
* Professional accounting compliance.
* A public multi-tenant commercial service.
* AI-generated financial advice.
* Public Play Store distribution.

These features introduce unnecessary complexity for the initial personal-use application.

---

# 3. TARGET USERS

## 3.1 Primary User: Father

The father currently uses an income-expense management application.

He requires:

* Familiar transaction workflows.
* Agricultural and household expense categories.
* Tamil descriptions.
* Multiple account support.
* Historical financial records.
* Monthly summaries.
* Account transfers.
* Detailed statements.
* Easy editing of transactions.
* Reliable access to existing financial information.

The interface must minimize disruption to his existing habits.

## 3.2 Primary User: Mother

The mother requires:

* Simple navigation.
* Clear Add Income and Add Expense actions.
* Readable Tamil labels.
* Straightforward transaction entry.
* Easy access to the family's financial summary.
* Shared access to financial records.
* Searchable transaction history.
* Statement export.

The application must not assume that every user is comfortable with technical terminology.

## 3.3 Shared Workspace

Both users will initially access one family workspace.

Each user will have an individual authenticated identity, but both will access the same authorized financial records.

Every transaction must record who created it.

The initial permission model will allow both users to:

* View transactions.
* Add income.
* Add expenses.
* Create transfers.
* Edit transactions.
* Delete transactions, subject to confirmation.
* View reports.
* Export statements.
* Access the notebook.

Only authorized workspace administrators may manage membership or delete the entire workspace.

---

# 4. PLATFORM AND TECHNICAL REQUIREMENTS

## 4.1 Android Platform

The application must be a native Android application.

Required technologies:

* Kotlin.
* Jetpack Compose.
* Material 3 components.
* Android Navigation Compose.
* Kotlin Coroutines and Flow.
* Android resource-based localization.
* Android Studio.
* Gradle build system.

The implementation must use maintained, compatible stable dependencies selected at project initialization.

Claude must verify dependency versions against official documentation rather than inventing versions.

The project must not use a WebView-based interface as a substitute for native Android screens.

## 4.2 Application Architecture

Use a layered architecture with clear separation of responsibilities.

Recommended structure:

Presentation layer:

* Compose screens.
* Reusable UI components.
* ViewModels.
* UI state.
* Navigation.

Domain layer:

* Financial calculations.
* Transaction validation.
* Transfer rules.
* Reporting logic.
* Use cases.

Data layer:

* Data models.
* Repositories.
* Database access.
* Cloud synchronization.
* Export and import services.

Infrastructure:

* Authentication.
* Firebase configuration.
* Logging.
* Backup services.
* File generation.

Business logic must not be embedded directly in Compose screen functions.

## 4.3 Local Data and Cloud Synchronization

The application must maintain usable local financial data and synchronize it with the cloud.

The initial implementation should use Firebase Cloud Firestore's Android offline persistence to minimize synchronization complexity.

Firestore's local cache and offline synchronization must be configured and tested explicitly.

Room may be introduced if testing demonstrates that Firestore's built-in offline model is insufficient for required local queries, transaction workflows, or consistency guarantees.

If Room is introduced, the application must define one authoritative local data source and an explicit synchronization mechanism. It must not maintain two independent databases without a reconciliation strategy.

The first implementation must prioritize correctness and simplicity over unnecessary infrastructure.

## 4.4 Cloud Services

Required services:

* Firebase Authentication.
* Cloud Firestore.
* Firebase security rules.
* Firebase App Check where appropriate.
* Firebase Cloud Storage for receipt attachments, if enabled.
* Firebase Emulator Suite for development and testing where applicable.

Cloud services must be configured separately for development and production.

Production credentials must not be committed to the source repository.

The application must not contain administrative service-account credentials.

## 4.5 Offline Requirements

The application should allow users to:

* Open previously synchronized financial records.
* Add transactions while offline.
* View cached summaries.
* Edit eligible cached records.
* See whether changes are pending synchronization.
* Resume synchronization when connectivity returns.

The interface must distinguish between locally saved changes and cloud-confirmed changes.

The application must not claim that a transaction has synchronized when the cloud has not confirmed it.

Offline data availability is limited to information already cached or stored locally.

---

# 5. INFORMATION ARCHITECTURE

The application will have five primary navigation destinations.

1. Home.
2. Transactions.
3. Calendar.
4. Reports.
5. More.

A persistent Add Transaction action will be available throughout the application.

The Add Transaction action must provide:

* Add Income.
* Add Expense.
* Transfer.

The More section will contain:

* Notebook.
* Accounts.
* Categories.
* Budgets.
* Recurring Transactions.
* Backup and Restore.
* Language Settings.
* Workspace Settings.
* Privacy and Security.
* Application Information.

The interface must use a consistent navigation hierarchy.

Primary functions must not be hidden behind multiple hamburger menus.

---

# 6. FEATURE REQUIREMENTS

## 6.1 Dashboard

### Purpose

Provide an immediate overview of the family's financial position.

### Required Components

1. Family workspace name.
2. Current reporting period.
3. Previous-period navigation.
4. Next-period navigation.
5. Total income.
6. Total expenses.
7. Net cash flow.
8. Overall available balance.
9. Previous balance.
10. Recent transactions.
11. Income-versus-expense chart.
12. Category spending overview.
13. Quick transaction actions.
14. Cloud synchronization status.

### Reporting Period

Default to the current calendar month.

Allow selection of:

* Today.
* This week.
* This month.
* Last month.
* This year.
* Custom date range.

### Financial Summary Definitions

Opening Balance:
The combined balance of tracked accounts at the beginning of the reporting period.

Total Income:
The sum of income transactions included in the selected period.

Total Expenses:
The sum of expense transactions included in the selected period.

Net Cash Flow:
Total Income minus Total Expenses.

Closing Balance:
Opening Balance plus Total Income minus Total Expenses, adjusted for any explicitly supported opening-balance corrections or other financial transaction types.

Transfers between tracked accounts must not change the combined family balance.

Account-specific balances must reflect transfers into and out of the respective accounts.

### Acceptance Criteria

* The dashboard loads previously available local data without requiring a fresh network request.
* Income and expense totals agree with transaction history.
* Changing the reporting period updates every relevant summary.
* Transfers do not inflate income or expenses.
* The dashboard shows synchronization status.
* The interface remains usable with large transaction histories.
* Tamil labels do not overflow their containers.

## 6.2 Add Income

### Required Fields

* Amount.
* Transaction title.
* Income category.
* Income source.
* Transaction date.
* Destination account.

### Optional Fields

* Payment method.
* Description.
* Tags.
* Receipt attachment.
* Recurrence settings.
* Reference number.

### Suggested Income Categories

* Agricultural Income.
* Business Income.
* Salary.
* Interest Received.
* Investment Income.
* Money Received from Family.
* Loan Received.
* Refund.
* Other Income.

Categories must be customizable.

### Validation Rules

* Amount must be greater than zero.
* Amount must use a valid monetary representation.
* Category must be valid.
* Destination account must exist and be accessible.
* Date must be valid.
* Title must not be empty.
* Notes must support Tamil and English text.

Loan receipts and other non-earned cash inflows must be distinguishable from earned income in reports.

### User Experience

The amount field must be prominent.

The form should show commonly used fields first and place advanced options in an expandable section.

After saving, the user should be able to:

* Return to the dashboard.
* View the saved transaction.
* Add another transaction.

## 6.3 Add Expense

### Required Fields

* Amount.
* Transaction title.
* Expense category.
* Transaction date.
* Source account.

### Optional Fields

* Merchant or recipient.
* Payment method.
* Description.
* Tags.
* Receipt attachment.
* Recurrence settings.
* Reference number.

### Suggested Categories

Household:

* Groceries.
* Food.
* Household Supplies.
* Electricity.
* Water.
* Internet.
* Mobile Recharge.
* Rent.

Agriculture:

* Seeds.
* Fertilizers.
* Pesticides.
* Agricultural Labour.
* Machinery.
* Equipment.
* Irrigation.
* Transport.
* Repairs.
* Other Agricultural Expenses.

Personal:

* Medical.
* Education.
* Travel.
* Vehicle Maintenance.
* Insurance.
* Entertainment.
* Gifts.
* Other Expenses.

Financial:

* Loan Principal Repayment.
* Loan Interest.
* Bank Charges.
* Taxes.

Categories must be editable and extensible.

### Validation Rules

* Amount must be greater than zero.
* Title must not be empty.
* Category must exist.
* Source account must be valid.
* Transaction date must be valid.
* Notes must support Tamil and English.
* Saving must not create duplicate transactions because of repeated button taps or network retries.

Loan principal repayments must be treated distinctly from ordinary expenses.

## 6.4 Account Transfers

### Purpose

Move money between tracked accounts without creating artificial income or expenses.

### Required Fields

* Amount.
* Source account.
* Destination account.
* Date.
* Description, if provided.

### Validation Rules

* Amount must be greater than zero.
* Source and destination accounts must be different.
* Both accounts must be accessible.
* Both account balances must be updated consistently.
* Transfers must be excluded from ordinary income and expense totals.

A transfer must have a stable transaction identifier and enough information to update both account balances consistently.

### Acceptance Criteria

Transferring ₹20,000 from cash to a bank account must decrease cash by ₹20,000 and increase the bank account by ₹20,000.

The combined family balance must remain unchanged.

## 6.5 Accounts Management

Users must be able to create and manage accounts.

Supported account types:

* Cash.
* Savings Account.
* Current Account.
* Digital Wallet.
* Other.

Each account must contain:

* Account ID.
* Name.
* Type.
* Currency.
* Opening balance.
* Opening-balance date.
* Active status.
* Creation timestamp.
* Modification timestamp.

The initial release will support INR accounts.

The application must distinguish between:

* Account balance.
* Combined family balance.
* Reporting-period net cash flow.

Deleting an account with existing transactions must require a deliberate migration or archival process.

The application must not silently delete historical transactions when an account is archived.

## 6.6 Categories Management

Users must be able to:

* Create categories.
* Rename categories.
* Change category icons.
* Change category colors.
* Archive unused categories.
* Create subcategories, if enabled.
* Search categories.

Income and expense categories must be distinguishable.

Archived categories must remain available for historical reporting.

Renaming a category must not corrupt existing transaction references.

## 6.7 Transaction History

The transaction history must provide:

* All transactions.
* Income-only view.
* Expense-only view.
* Transfer-only view.
* Text search.
* Date filters.
* Category filters.
* Account filters.
* Amount-range filters.
* Creator filters.
* Sorting by date or amount.
* Pagination or incremental loading.

Transaction entries must display:

* Date.
* Title.
* Category.
* Amount.
* Account.
* Relevant source or merchant.
* Synchronization state, when relevant.

Selecting an entry opens its details.

Users may edit or delete eligible transactions.

Deletion must require confirmation and must correctly update balances and reports.

Where practical, deleted or modified records should be represented in an audit history.

## 6.8 Calendar

The calendar must show financial activity by date.

Features:

* Monthly calendar.
* Previous and next month navigation.
* Visual indicators for dates with transactions.
* Daily income totals.
* Daily expense totals.
* Daily net movement.
* Transaction list for the selected date.

Tapping a date opens the transactions for that date.

Calendar calculations must respect the transaction's intended local date.

## 6.9 Notebook

The notebook allows users to record financial notes that are not necessarily transactions.

Fields:

* Entry ID.
* Title.
* Description.
* Date.
* Tags.
* Optional linked transaction.
* Optional reminder.
* Created by.
* Created timestamp.
* Modified timestamp.

The notebook must support Tamil and English.

Notes must be searchable.

Creating or editing a notebook entry must not alter account balances.

## 6.10 Reports and Analytics

Required reports:

1. Income versus Expenses.
2. Category-wise Expenses.
3. Income by Source.
4. Account Summary.
5. Daily Summary.
6. Monthly Summary.
7. Annual Summary.
8. Custom-period Statement.
9. Transaction History Report.

Reports must support date-range selection.

Where appropriate, users must be able to filter reports by account, category, and transaction type.

Every report must derive its calculations from the same underlying financial records used by the transaction history.

Reports must not rely on separately maintained totals that can become inconsistent.

## 6.11 Recurring Transactions

Users should be able to create recurring transaction templates.

Supported frequencies:

* Daily.
* Weekly.
* Monthly.
* Yearly.
* Custom interval, if implemented.

Each recurrence must contain:

* Transaction template.
* Frequency.
* Start date.
* Optional end date.
* Next scheduled date.
* Active status.

The system must prevent duplicate generation when synchronization or background execution is retried.

A scheduled transaction must not be represented as completed spending before its actual transaction record is generated under the user's chosen workflow.

## 6.12 Saved Transaction Templates

Users should be able to bookmark frequently repeated transactions.

A template may contain:

* Title.
* Category.
* Account.
* Source or recipient.
* Default notes.
* Payment method.

The amount should remain editable.

Selecting a template must create a new transaction, not modify an existing transaction.

## 6.13 Budgets

Users should be able to define monthly expense limits by category.

Each budget contains:

* Category.
* Period.
* Limit amount.
* Active status.

The application should display:

* Budget amount.
* Amount spent.
* Remaining amount.
* Percentage used.

Optional warnings may be shown when a budget reaches configurable thresholds.

Budgets must not prevent users from recording a legitimate expense.

## 6.14 Loan Tracking

Loan tracking may be implemented after the core financial system.

Required concepts:

* Borrowed amount.
* Lent amount.
* Counterparty.
* Loan date.
* Due date.
* Repayment history.
* Outstanding principal.
* Interest.
* Notes.

Borrowing money must not be counted as earned income.

Lending money must not automatically be treated as a permanent expense.

Principal repayments must be tracked separately from interest and fees.

## 6.15 Receipt Attachments

Users should be able to attach receipt photographs or documents to transactions.

Supported initial formats:

* JPEG.
* PNG.
* PDF, if supported by the chosen implementation.

Attachments must have:

* Stable IDs.
* Transaction association.
* Storage references.
* Upload status.
* File metadata.

Cloud access must be restricted to authorized workspace members.

The application must handle failed uploads without deleting the transaction.

---

# 7. TAMIL AND ENGLISH LOCALIZATION REQUIREMENTS

Tamil support is mandatory and must be implemented from the first development phase.

## 7.1 Interface Languages

Supported languages:

* Tamil: ta.
* English: en.

Tamil must be the default language for the initial family workspace.

Users may independently select their preferred interface language.

A language preference must persist across application restarts.

If individual user preferences are supported, one user's language choice must not automatically change the other user's interface language.

## 7.2 Localization Architecture

Use Android string resources.

English resources:

res/values/strings.xml

Tamil resources:

res/values-ta/strings.xml

Do not hardcode user-facing text directly into Kotlin or Compose files.

All visible interface strings must use resource-backed localization.

This includes:

* Navigation labels.
* Buttons.
* Form labels.
* Validation messages.
* Empty states.
* Error messages.
* Notifications.
* Dialogs.
* Reports.
* Export labels.
* Settings.
* Accessibility descriptions.

## 7.3 Tamil Typography

The interface must support proper Tamil rendering.

Requirements:

* Use a suitable Tamil-capable system font or bundled font.
* Test text rendering on actual Android devices.
* Avoid clipping Tamil vowel signs and combining characters.
* Support multiline Tamil descriptions.
* Support large font sizes.
* Avoid layouts that assume English text lengths.
* Ensure sufficient vertical spacing.
* Verify PDF font embedding or another reliable Tamil rendering approach.

A bundled font may be used when necessary, subject to its license.

## 7.4 Transaction Text

Transaction titles, descriptions, notes, names, and categories must support Unicode text.

The application must preserve Tamil characters through:

* Local storage.
* Cloud storage.
* Synchronization.
* Search.
* Editing.
* Backup.
* Restore.
* CSV export.
* XLSX export.
* PDF generation.

The application must never translate transaction descriptions automatically.

## 7.5 Tamil Search

Search must work with Tamil text.

Searchable fields should include:

* Transaction title.
* Description.
* Category name.
* Merchant.
* Source.
* Notebook title.
* Notebook content.

Search must not depend on English transliteration.

Transliterated search may be introduced later.

## 7.6 Tamil Formatting

Use locale-aware formatting for dates, numbers, and currency.

The default currency is INR.

The application should consistently display monetary values with the ₹ symbol.

Tamil and English reports must preserve the same underlying amounts.

Changing interface language must never change financial values.

## 7.7 Localization Acceptance Tests

Before release:

* Every screen must be tested in Tamil.
* Every screen must be tested in English.
* No essential label may appear as a missing-resource identifier.
* No critical button may be clipped.
* Tamil descriptions must survive export and restore.
* Search must return Tamil matches.
* PDF statements must display Tamil correctly.
* Language switching must not modify transaction records.

---

# 8. DATA MODEL

## 8.1 Core Entities

Required entities:

1. User.
2. Workspace.
3. Workspace Membership.
4. Account.
5. Category.
6. Transaction.
7. Notebook Entry.
8. Budget.
9. Recurrence Rule.
10. Attachment Metadata.
11. Audit Event, where implemented.

## 8.2 Transaction Model

Each transaction should contain fields equivalent to:

* ID.
* Workspace ID.
* Type.
* Amount in integer minor currency units.
* Currency.
* Title.
* Category ID.
* Account ID.
* Destination Account ID for transfers.
* Source or merchant.
* Payment Method.
* Transaction Date.
* Notes.
* Tags.
* Created By.
* Created At.
* Updated At.
* Schema Version.
* Synchronization or deletion metadata, as required.

Transaction type must distinguish:

* Income.
* Expense.
* Transfer.

Other financial concepts, such as loans, must be represented with explicit semantics rather than incorrectly categorized as ordinary income or expenses.

## 8.3 Monetary Precision

Store monetary amounts as integer minor units, such as paise, where supported by the chosen currency model.

Do not use ordinary binary floating-point values for authoritative monetary calculations.

All calculations must use a consistent rounding policy.

The UI may format values as rupees, but the stored representation must preserve exact amounts.

## 8.4 Stable IDs

Use stable transaction, account, category, and workspace IDs.

Do not rely solely on category names or display labels as database identifiers.

Renaming a category must not break its historical transactions.

## 8.5 Timestamps and Dates

Store transaction dates separately from creation and modification timestamps.

Transaction dates represent the financial date selected by the user.

Creation and modification timestamps represent when the record was created or changed.

The application must avoid unintended date shifts caused by timezone conversions.

## 8.6 Schema Versioning

The backup format and persistent data model must support schema versioning.

Future application versions must be able to detect unsupported backup versions and report the issue without corrupting existing data.

---

# 9. DATA INTEGRITY AND FINANCIAL RULES

These requirements are mandatory.

## 9.1 Balance Calculation

Account balances must be derived from opening balances and correctly classified transactions.

A simplified account balance calculation is:

Opening Balance + Income Posted to Account - Expenses Paid from Account + Transfers In - Transfers Out.

Additional transaction types must follow explicitly defined accounting rules.

The combined family balance is the sum of the balances of all included tracked accounts.

## 9.2 Transfer Integrity

Transfers must not be included in income or expense totals.

Both sides of a transfer must share a stable transfer identity.

If a transfer is edited or deleted, both account effects must be updated consistently.

## 9.3 Duplicate Prevention

Repeated taps, retries, and synchronization attempts must not create duplicate transactions.

Use stable IDs and idempotent write patterns.

## 9.4 Concurrent Changes

The application must handle simultaneous changes from both phones.

Different transactions should synchronize independently.

Conflicting edits to the same transaction must not silently destroy financial information.

Where practical, detect conflicting modifications and preserve enough information for reconciliation.

## 9.5 Historical Data

Editing or deleting a transaction must update affected reports.

Category changes must not invalidate historical transaction records.

Archiving an account must not silently remove its historical financial activity.

## 9.6 Validation

All financial operations must validate data before committing authoritative changes.

The application must display useful error messages rather than silently ignoring failed operations.

---

# 10. AUTHENTICATION AND ACCESS CONTROL

## 10.1 Authentication

Each parent should have an individual authenticated identity.

Firebase Authentication will provide the initial authentication mechanism.

Supported methods should be chosen based on simplicity and account recovery requirements.

## 10.2 Workspace Membership

Each workspace must have an explicit membership model.

Membership records must identify:

* User ID.
* Workspace ID.
* Role.
* Membership status.
* Creation timestamp.

## 10.3 Authorization

Cloud Firestore security rules must enforce workspace access.

Users must not gain access to another family's records merely by guessing a document ID.

Authorization must be enforced by the backend, not only by the Android interface.

## 10.4 Account Recovery

The application must provide a documented recovery process for authorized users.

The project must not rely exclusively on an authentication session stored on one phone.

## 10.5 Privacy

The application must collect only information required for its functionality.

Financial records must not be exposed through public database rules.

Logs must not include unnecessary financial details or authentication secrets.

---

# 11. SYNCHRONIZATION REQUIREMENTS

## 11.1 Primary Requirement

Both phones must access the same authorized workspace and receive cloud-confirmed changes automatically.

## 11.2 Synchronization States

The interface should distinguish:

* Synced.
* Pending Changes.
* Syncing.
* Offline.
* Synchronization Error.

## 11.3 Offline Behavior

The application must support offline access to locally available records.

New transactions must remain available locally while waiting for synchronization.

Pending changes must survive normal application restarts.

## 11.4 Reconnection

When connectivity returns:

* Pending changes should synchronize.
* Remote changes should be downloaded.
* Reports should update.
* Failed operations should be retried safely.

## 11.5 Conflict Policy

The application must define and document its conflict policy.

It must not assume that automatic last-write-wins behavior is sufficient for every financial operation.

Transfer operations and balance-affecting changes must remain internally consistent.

## 11.6 Synchronization Testing

Test:

* Two devices online simultaneously.
* One device offline.
* Both devices offline.
* Device restart with pending changes.
* Repeated synchronization retries.
* Duplicate submissions.
* Simultaneous edits.
* Deleted records.
* Interrupted attachment uploads.

---

# 12. EXPORTS, STATEMENTS, BACKUPS, AND RESTORATION

## 12.1 PDF Statements

Generate readable PDF statements containing:

* Workspace name.
* Reporting period.
* Generation date.
* Opening balance.
* Total income.
* Total expenses.
* Closing balance.
* Detailed transaction list.
* Category summary.
* Account summary, when selected.

PDF statements must support Tamil and English.

Use a reliable font-rendering strategy for Tamil, and test actual exported files.

## 12.2 CSV Export

Provide UTF-8 CSV export.

Suggested columns:

* Date.
* Transaction Type.
* Title.
* Category.
* Source or Merchant.
* Account.
* Payment Method.
* Income.
* Expense.
* Notes.
* Created By.

CSV output must preserve Tamil text.

Protect spreadsheet exports against formula injection when exporting untrusted text fields.

## 12.3 XLSX Export

Provide an Excel-compatible workbook with worksheets such as:

1. Summary.
2. Transactions.
3. Category Breakdown.
4. Account Summary.

The workbook must preserve dates, amounts, and Tamil text.

## 12.4 Export Filters

Allow selection of:

* Current Month.
* Previous Month.
* Current Year.
* Previous Year.
* Custom Date Range.
* Account.
* Category.
* Income.
* Expenses.
* Transfers.

## 12.5 Full Backup

The backup must preserve:

* Workspace settings.
* Accounts.
* Categories.
* Transactions.
* Notebook entries.
* Budgets.
* Recurrence settings.
* Relevant metadata.
* Attachment references.

If attachment files are included, the backup process must document whether they are embedded or exported separately.

## 12.6 Backup Destinations

Support:

* Local file export.
* Android's system file picker.
* User-selected cloud destinations where technically supported.

Google Drive integration may be introduced as an optional feature.

Google Drive must not replace the live transactional database.

## 12.7 Restoration

Before restoration, validate the backup.

The application must:

* Identify its schema version.
* Detect malformed files.
* Validate record references.
* Avoid silent partial imports.
* Provide a clear restore result.
* Prevent accidental overwriting of current data.

Where appropriate, offer a merge or replace option.

Restoration must be tested on a separate installation before release.

---

# 13. MIGRATION FROM THE EXISTING APPLICATION

## 13.1 Migration Objective

Preserve the father's historical financial information while replacing the existing application.

## 13.2 Migration Procedure

1. Back up the original application.
2. Export the available transaction data.
3. Inspect the exported schema.
4. Identify accounts, categories, balances, and dates.
5. Map existing transaction types.
6. Preserve Tamil descriptions.
7. Import into a staging workspace.
8. Compare record counts.
9. Compare income totals.
10. Compare expense totals.
11. Compare account balances.
12. Investigate discrepancies.
13. Approve the migration.
14. Import into the production workspace.
15. Verify both phones.

## 13.3 Migration Safety

Never overwrite the original backup.

Never delete the existing application before migration verification.

If the old format cannot be imported directly, implement a controlled CSV/XLSX import workflow.

The migration tool must generate a report describing:

* Records imported.
* Records rejected.
* Missing fields.
* Duplicate records detected.
* Category mappings.
* Balance discrepancies.

## 13.4 Migration Acceptance Criteria

The migration is accepted only when:

* Expected historical records are present.
* Income and expense totals match the source data, accounting for documented exclusions.
* Account balances reconcile.
* Tamil text is preserved.
* Transfer semantics remain correct.
* No unexplained duplicate transactions exist.

---

# 14. USER EXPERIENCE AND DESIGN SYSTEM

## 14.1 Design Goals

The interface must be:

* Simple.
* Readable.
* Consistent.
* Accessible.
* Responsive to different Android screen sizes.
* Suitable for Tamil and English.
* Fast to navigate.
* Comfortable for daily use.

## 14.2 Dashboard Design

Prioritize:

1. Current financial balance.
2. Income and expense summary.
3. Add Income.
4. Add Expense.
5. Recent Transactions.
6. Spending Overview.

The selected reporting period must be visible.

## 14.3 Transaction Form Design

The amount input must appear near the top.

Frequently used fields must remain visible.

Advanced options should be collapsible.

The interface must preserve entered values if a recoverable validation error occurs.

## 14.4 Visual Language

Use Material 3 design principles with a restrained visual system.

Income and expenses should have distinguishable visual treatments, but color must not be the only indicator of transaction type.

Use labels, icons, and clear text.

Provide adequate contrast and large tap targets.

## 14.5 Accessibility

Support:

* Adjustable system font sizes.
* Screen readers.
* Clear content descriptions.
* Adequate touch target sizes.
* High contrast.
* Predictable navigation.
* Tamil text scaling.
* Long transaction descriptions.
* Error messages that identify how to correct a problem.

Avoid relying on gestures for essential actions.

## 14.6 Performance

The application should remain responsive when the transaction history grows.

Use pagination or incremental loading.

Avoid recalculating every report unnecessarily after each UI recomposition.

Perform expensive exports and file processing outside the main UI thread.

---

# 15. TESTING REQUIREMENTS

## 15.1 Unit Tests

Required unit tests include:

* Income calculations.
* Expense calculations.
* Opening and closing balances.
* Account transfers.
* Monthly summaries.
* Date-range filters.
* Category aggregation.
* Loan principal and interest calculations, if implemented.
* Monetary rounding.
* Transaction validation.
* Duplicate prevention.
* Localization-related formatting.

## 15.2 Integration Tests

Test:

* Database persistence.
* Cloud synchronization.
* Workspace authorization.
* Transaction editing.
* Transaction deletion.
* Account transfers.
* Backup generation.
* Backup restoration.
* PDF generation.
* CSV generation.
* XLSX generation.
* Attachment uploads.
* Offline recovery.

## 15.3 UI Tests

Test:

* Navigation.
* Add Income.
* Add Expense.
* Transfer.
* Transaction search.
* Filtering.
* Language switching.
* Tamil text display.
* Statement export.
* Confirmation dialogs.
* Error states.

## 15.4 Financial Integrity Tests

The application must demonstrate that:

1. Adding income increases the relevant account balance.
2. Adding an expense decreases the relevant account balance.
3. Transferring money changes the two account balances but not the combined balance.
4. Editing a transaction updates the relevant reports.
5. Deleting a transaction updates the relevant reports.
6. Historical statements agree with transaction history.
7. Restoring a backup preserves the same financial totals.

## 15.5 Tamil Tests

Test Tamil text in:

* Titles.
* Notes.
* Categories.
* Search.
* Transaction history.
* PDF statements.
* CSV files.
* XLSX files.
* Backup and restore.

## 15.6 Device Tests

Test on both target phones.

At minimum, verify:

* Installation.
* Authentication.
* Synchronization.
* Offline entry.
* Restart recovery.
* Export.
* Restore.
* Tamil rendering.
* Font scaling.

---

# 16. RELEASE REQUIREMENTS

## 16.1 Private Distribution

The application will be distributed through a signed APK.

Public Play Store publication is not required.

## 16.2 Release Checklist

Before installation on the parents' phones:

* All critical tests pass.
* Production security rules are verified.
* Authentication works.
* Cloud synchronization works.
* Offline entry works.
* Tamil localization is complete.
* Financial summaries reconcile.
* Exports work.
* Backup and restore work.
* Migration is verified.
* Production configuration is separate from development configuration.
* Signing credentials are safely backed up.

## 16.3 Updates

Future versions must preserve application signing identity and data compatibility.

Database and backup migrations must be tested before release.

## 16.4 Rollback

Keep the previous signed APK and a verified data backup before deploying a new version.

A failed update must not leave the family unable to access financial records.

---

# 17. IMPLEMENTATION PHASES

## Phase 1: Foundation

* Android project setup.
* Kotlin and Compose configuration.
* Navigation.
* Design system.
* Data models.
* Financial calculation rules.

## Phase 2: Local Core Features

* Dashboard.
* Add Income.
* Add Expense.
* Transfers.
* Accounts.
* Categories.
* Transaction history.
* Calendar.
* Notebook.

## Phase 3: Localization

* Tamil resources.
* English resources.
* Language switching.
* Tamil search.
* Typography verification.

Localization must begin early and continue throughout development.

## Phase 4: Cloud

* Authentication.
* Workspace membership.
* Firestore schema.
* Security rules.
* Synchronization.
* Offline behavior.
* Conflict handling.

## Phase 5: Reporting

* Summary calculations.
* Charts.
* PDF statements.
* CSV export.
* XLSX export.
* Custom date filters.

## Phase 6: Backup and Migration

* Full backup.
* Restoration.
* Import utility.
* Historical data verification.

## Phase 7: Advanced Features

* Recurring transactions.
* Templates.
* Budgets.
* Receipt attachments.
* Loan tracking.

## Phase 8: Release

* Security audit.
* Regression testing.
* Device testing.
* Signed APK.
* Installation on both phones.

---

# 18. DEFINITION OF DONE

The application is considered ready for family use when all the following are true:

1. It installs on both Android phones.
2. Both users can authenticate.
3. Both phones access the same authorized family workspace.
4. Income can be recorded correctly.
5. Expenses can be recorded correctly.
6. Transfers preserve the combined balance.
7. Historical transactions can be searched and filtered.
8. Calendar and notebook features work.
9. Tamil and English interfaces are complete.
10. Tamil descriptions survive synchronization and export.
11. Reports match underlying transactions.
12. PDF, CSV, and XLSX exports work.
13. Backup and restore are verified.
14. Offline changes synchronize correctly.
15. Conflicting changes are handled explicitly.
16. Security rules prevent unauthorized access.
17. Existing historical records have been migrated and reconciled.
18. A signed APK can be installed privately.
19. Automated critical tests pass.
20. A recoverable backup exists before the application becomes the primary financial record system.

**Final product principle:** Financial correctness, Tamil usability, data preservation, and reliable synchronization take precedence over additional features or visual effects.
