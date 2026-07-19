# TEST CASE EXCEL TEMPLATE — FORMAT SPECIFICATION FOR LLM

## 1. Purpose

Use this document as the required output specification when generating software test cases.

The generated content will be copied into an Excel workbook that contains these sheets:

1. `Cover`
2. `Test case List`
3. One or more module sheets, for example:
   - `CheckDate`
   - `Module2`
4. `Test Report`

The LLM must keep the same sheet structure, field names, column order, and result values described below.

---

# 2. General rules for generating test cases

- Write in **simple and clear English**.
- Create **small, focused test cases**. One test case should verify one main behavior.
- Group test cases by **Function Name**.
- Every test case must have a unique ID.
- Use detailed numbered steps in `Test Case Procedure`.
- Write measurable and specific results in `Expected Output`.
- Do not write vague expected results such as:
  - `System works correctly`
  - `Success`
  - `Display properly`
- Use only these values for `Result`:
  - `Pass`
  - `Fail`
  - `Untested`
  - `N/A`
- If the test cases have not been executed, set `Result` to `Untested`.
- Leave `Test date` blank when the test case has not been executed.
- Leave `Note` blank unless additional information is necessary.
- Use `Inter-test case Dependence` only when another test case must be completed first.
- Include relevant test types:
  - Happy path
  - Negative case
  - Boundary value
  - Empty input
  - Invalid format
  - Permission or authorization
  - Duplicate data
  - Not-found data
  - Server or network error
  - Database error
  - Validation error
  - UI behavior, when applicable
- Do not invent features that are not described in the requirements or source code.
- If a requirement is unclear, write the assumption in the `Note` column.

---

# 3. Workbook visual style

The Excel template uses the following visual style:

- Main title:
  - Large, bold, black text
  - Center aligned
- Table header:
  - Dark blue background
  - White bold text
  - Center aligned
- Function or test group row:
  - Light cyan background
  - Bold text
- Form labels:
  - Bold text
  - Some labels use dark orange font
- Placeholder or instruction text:
  - Green italic font
- All tables:
  - Thin black borders
  - Wrapped text
  - Top vertical alignment for long content

The LLM mainly needs to generate the cell content. Excel styling will be applied from the existing template.

---

# 4. Sheet: Cover

## 4.1 Main title

```text
TEST CASE
```

## 4.2 Project information

| Field | Value |
|---|---|
| Project Name | `<Project Name>` |
| Project Code | `<Project Code>` |
| Document Code | `<Project Code>_XXX_vx.x` |
| Creator | `<Creator Name>` |
| Reviewer/Approver | `<Reviewer or Approver Name>` |
| Issue Date | `<YYYY-MM-DD>` |
| Version | `<Version, for example 1.0>` |

## 4.3 Record of change

| Effective Date | Version | Change Item | *A,D,M | Change description | Reference |
|---|---|---|---|---|---|
| `<Date when these changes are effective>` | `<Version>` | `<Changed section or item>` | `<A, D, or M>` | `<Description of the change>` | `<Related documents referred to in this version>` |

### Meaning of `*A,D,M`

- `A` = Add
- `D` = Delete
- `M` = Modify

Add one row for every document revision.

---

# 5. Sheet: Test case List

## 5.1 Main title

```text
TEST CASE LIST
```

## 5.2 Project and environment information

| Field | Value |
|---|---|
| Project Name | `<Project Name>` |
| Project Code | `<Project Code>` |
| Test Environment Setup Description | `<Environment information>` |

The `Test Environment Setup Description` should be written as a numbered list.

Example:

```text
1. Backend server: Spring Boot 3.x
2. Database: PostgreSQL 16
3. Frontend: React
4. Web browser: Microsoft Edge 150 or Google Chrome
5. Operating system: Windows 11
6. Test account: Manager role
7. API documentation: Swagger UI
```

## 5.3 Function list table

Use exactly this column order:

| No | Function Name | Sheet Name | Description | Pre-Condition |
|---:|---|---|---|---|
| 1 | `<Function name>` | `<Exact module sheet name>` | `<Short description of the function>` | `<Conditions required before testing>` |

### Rules

- `No` starts from 1 and increases by 1.
- `Function Name` is the feature or function being tested.
- `Sheet Name` must exactly match the related module sheet name.
- `Description` summarizes what the function does.
- `Pre-Condition` describes required setup, data, login role, or system state.
- A module sheet can contain multiple functions.
- Each function shown in this list must also appear as a function group in its module sheet.

### Example

| No | Function Name | Sheet Name | Description | Pre-Condition |
|---:|---|---|---|---|
| 1 | Check valid date | CheckDate | Validate a date entered by the user | Application is running |
| 2 | Validate date input | CheckDate | Reject empty, non-numeric, or out-of-range values | User is on the Date Time Checker form |
| 3 | Clear input | CheckDate | Clear all entered values and messages | User entered data in at least one field |
| 4 | User login | Module2 | Authenticate a user with email and password | An active user account exists |
| 5 | Logout | Module2 | End the current user session | User is logged in |

---

# 6. Sheet: Module test cases

Each feature module has its own sheet.

Examples:

- Sheet `CheckDate` may use module code `CheckDate`.
- Sheet `Module2` may use module code `Module2`.

---

## 6.1 Module information section

| Field | Value |
|---|---|
| Module Code | `<Module Code>` |
| Test requirement | `<Brief description of the requirements tested in this sheet>` |
| Tester | `<Tester Name>` |

Example:

| Field | Value |
|---|---|
| Module Code | `CheckDate` |
| Test requirement | `Verify the user interface, date validation, date checking, and clear function of the Date Time Checker application.` |
| Tester | `Nguyen Van A` |

---

## 6.2 Test summary section

Use exactly these fields:

| Pass | Fail | Untested | N/A | Number of Test cases |
|---:|---:|---:|---:|---:|
| `<Count>` | `<Count>` | `<Count>` | `<Count>` | `<Total>` |

### Calculation

```text
Number of Test cases = Pass + Fail + Untested + N/A
```

When test cases are newly generated and have not been executed:

```text
Pass = 0
Fail = 0
Untested = Number of Test cases
N/A = 0
```

---

## 6.3 Detailed test case table

Use exactly this column order:

| ID | Test Case Description | Test Case Procedure | Expected Output | Inter-test case Dependence | Result | Test date | Note |
|---|---|---|---|---|---|---|---|

### Column definitions

#### `ID`

Recommended format:

```text
[<ModuleCode>-<SequenceNumber>]
```

Examples:

```text
[CheckDate-01]
[CheckDate-02]
[Module2-01]
```

Rules:

- ID must be unique in the workbook.
- Use two-digit sequence numbers.
- Do not reuse IDs.

#### `Test Case Description`

Write a short sentence describing exactly what is tested.

Good examples:

```text
Verify the form layout
Check a valid leap-year date
Reject an empty day value
Reject a month greater than 12
Clear all input fields
```

Bad examples:

```text
Test function
Check system
Date test
```

#### `Test Case Procedure`

Use numbered steps.

Example:

```text
1. Open the Date Time Checker application.
2. Enter `29` in the Day field.
3. Enter `02` in the Month field.
4. Enter `2024` in the Year field.
5. Click the `Check` button.
```

Procedure rules:

- Start from the required precondition.
- Include exact input values.
- Include the exact button, menu, page, API, or action.
- Put one action in each step.
- Do not combine many actions into one unclear sentence.

#### `Expected Output`

Write all observable expected results.

Example:

```text
- The system accepts the entered values.
- The system displays a message indicating that `29/02/2024` is a valid date.
- No validation error is displayed.
- The entered values remain visible in their input fields.
```

Expected-output rules:

- State the exact message when the requirement provides it.
- State the expected HTTP status for API tests.
- State expected database changes when relevant.
- State fields that must or must not appear.
- State whether data is created, updated, deleted, or unchanged.
- Mention security behavior for unauthorized access.
- Avoid subjective words such as `nice`, `good`, or `proper`.

#### `Inter-test case Dependence`

Write the IDs of test cases that must pass first.

Example:

```text
[Login-01]
```

For multiple dependencies:

```text
[Login-01], [User-03]
```

Leave blank if the test case is independent.

#### `Result`

Allowed values:

```text
Pass
Fail
Untested
N/A
```

Default value for newly generated cases:

```text
Untested
```

#### `Test date`

Use this format:

```text
YYYY-MM-DD
```

Leave blank for unexecuted test cases.

#### `Note`

Use this column for:

- Assumptions
- Bug IDs
- Failure reasons
- Environment problems
- Data-cleanup instructions
- Extra evidence

Leave blank when no note is needed.

---

## 6.4 Function group row

Before the test cases of each function, add a separate function header row.

Example:

```text
Function A: Graphic user interface
```

or, preferably, use the real feature name:

```text
Function: Validate date input
```

The function group row should be shown with a light cyan background in Excel.

---

## 6.5 Example module table

### Function: Graphic user interface

| ID | Test Case Description | Test Case Procedure | Expected Output | Inter-test case Dependence | Result | Test date | Note |
|---|---|---|---|---|---|---|---|
| [CheckDate-01] | Verify the Date Time Checker form layout | 1. Launch the application.<br>2. Open the Date Time Checker form. | - The FU logo appears at the top-left corner.<br>- The title `Date Time Checker` is visible.<br>- The title uses Arial font with size 26.<br>- The labels `Day`, `Month`, and `Year` are left aligned.<br>- Three input fields are displayed.<br>- The `Clear` and `Check` buttons are displayed.<br>- Maximize and Minimize buttons are not displayed. |  | Untested |  |  |

### Function: Validate date input

| ID | Test Case Description | Test Case Procedure | Expected Output | Inter-test case Dependence | Result | Test date | Note |
|---|---|---|---|---|---|---|---|
| [CheckDate-02] | Check a valid normal date | 1. Open the Date Time Checker form.<br>2. Enter `15` in Day.<br>3. Enter `07` in Month.<br>4. Enter `2026` in Year.<br>5. Click `Check`. | - The date is accepted.<br>- A valid-date message is displayed.<br>- No error message is displayed. | [CheckDate-01] | Untested |  |  |
| [CheckDate-03] | Reject a month greater than 12 | 1. Open the Date Time Checker form.<br>2. Enter `15` in Day.<br>3. Enter `13` in Month.<br>4. Enter `2026` in Year.<br>5. Click `Check`. | - The system rejects the value `13` as an invalid month.<br>- An error message is displayed.<br>- The application does not crash. | [CheckDate-01] | Untested |  |  |

---

# 7. Sheet: Test Report

## 7.1 Main title

```text
TEST REPORT
```

## 7.2 Report information

| Field | Value |
|---|---|
| Project Name | `<Project Name>` |
| Project Code | `<Project Code>` |
| Document Code | `<Project Code>_Test Report_vx.x` |
| Creator | `<Creator Name>` |
| Reviewer/Approver | `<Reviewer or Approver Name>` |
| Issue Date | `<YYYY-MM-DD>` |
| Notes | `<List of modules included in this release>` |

Example `Notes`:

```text
Release 1 includes 2 modules: CheckDate and Module2.
```

---

## 7.3 Module result summary

Use exactly this column order:

| No | Module code | Pass | Fail | Untested | N/A | Number of test cases |
|---:|---|---:|---:|---:|---:|---:|
| 1 | `<Module Code>` | `<Count>` | `<Count>` | `<Count>` | `<Count>` | `<Total>` |
| 2 | `<Module Code>` | `<Count>` | `<Count>` | `<Count>` | `<Count>` | `<Total>` |
|  | **Sub total** | `<Total Pass>` | `<Total Fail>` | `<Total Untested>` | `<Total N/A>` | `<Grand Total>` |

### Calculation rules

For each module:

```text
Number of test cases = Pass + Fail + Untested + N/A
```

For the subtotal:

```text
Total Pass = Sum of Pass from all modules
Total Fail = Sum of Fail from all modules
Total Untested = Sum of Untested from all modules
Total N/A = Sum of N/A from all modules
Grand Total = Sum of Number of test cases from all modules
```

---

## 7.4 Coverage calculations

### Test coverage

```text
Test coverage = (Pass + Fail) / Number of test cases × 100%
```

This measures how many applicable test cases have been executed.

### Test successful coverage

```text
Test successful coverage = Pass / Number of test cases × 100%
```

This measures how many total test cases have passed.

### Example

Given:

```text
Pass = 2
Fail = 0
Untested = 12
N/A = 0
Number of test cases = 14
```

Then:

```text
Test coverage = (2 + 0) / 14 × 100% = 14.29%
Test successful coverage = 2 / 14 × 100% = 14.29%
```

Use two decimal places.

---

# 8. REQUIRED OUTPUT FORMAT FROM THE LLM

The LLM must return the generated workbook content in the following Markdown structure.

Do not remove any section.

---

## SHEET: Cover

### Project Information

| Field | Value |
|---|---|
| Project Name | |
| Project Code | |
| Document Code | |
| Creator | |
| Reviewer/Approver | |
| Issue Date | |
| Version | |

### Record of Change

| Effective Date | Version | Change Item | *A,D,M | Change description | Reference |
|---|---|---|---|---|---|

---

## SHEET: Test case List

### Project and Environment Information

| Field | Value |
|---|---|
| Project Name | |
| Project Code | |
| Test Environment Setup Description | |

### Function List

| No | Function Name | Sheet Name | Description | Pre-Condition |
|---:|---|---|---|---|

---

## SHEET: `<Module Sheet Name>`

### Module Information

| Field | Value |
|---|---|
| Module Code | |
| Test requirement | |
| Tester | |

### Summary

| Pass | Fail | Untested | N/A | Number of Test cases |
|---:|---:|---:|---:|---:|

### Function: `<Function Name 1>`

| ID | Test Case Description | Test Case Procedure | Expected Output | Inter-test case Dependence | Result | Test date | Note |
|---|---|---|---|---|---|---|---|

### Function: `<Function Name 2>`

| ID | Test Case Description | Test Case Procedure | Expected Output | Inter-test case Dependence | Result | Test date | Note |
|---|---|---|---|---|---|---|---|

Repeat the function section until all functions in the module are covered.

Repeat the module sheet section until all modules are covered.

---

## SHEET: Test Report

### Report Information

| Field | Value |
|---|---|
| Project Name | |
| Project Code | |
| Document Code | |
| Creator | |
| Reviewer/Approver | |
| Issue Date | |
| Notes | |

### Module Result Summary

| No | Module code | Pass | Fail | Untested | N/A | Number of test cases |
|---:|---|---:|---:|---:|---:|---:|
|  | **Sub total** |  |  |  |  |  |

### Coverage

| Metric | Value |
|---|---:|
| Test coverage | |
| Test successful coverage | |

---

# 9. COPY-PASTE PROMPT FOR ANOTHER LLM

Copy the prompt below and attach or paste your requirements, source code documentation, API documentation, or user stories after it.

```text
You are a senior software tester.

Read all provided project documents and generate a complete manual test case workbook.

Follow the Excel test case format specification in the attached Markdown file exactly.

Important requirements:

1. Use simple and clear English.
2. Do not invent functions that are not present in the provided documents.
3. Split large behaviors into small test cases.
4. Group test cases by function and module.
5. Include happy path, negative, boundary, empty-input, invalid-format, authorization, not-found, duplicate-data, and error-handling cases when relevant.
6. Use this exact detailed test case column order:
   ID | Test Case Description | Test Case Procedure | Expected Output | Inter-test case Dependence | Result | Test date | Note
7. Use numbered steps in Test Case Procedure.
8. Use specific and measurable statements in Expected Output.
9. Set Result to Untested for every newly generated test case.
10. Leave Test date blank.
11. Make every ID unique using this format:
    [<ModuleCode>-<TwoDigitNumber>]
12. Ensure the summary numbers match the detailed test cases.
13. Ensure the Test case List contains every function.
14. Ensure the Test Report contains every module.
15. Calculate:
    Test coverage = (Pass + Fail) / Total test cases × 100%
    Test successful coverage = Pass / Total test cases × 100%
16. Return only the completed Markdown workbook structure. Do not add an introduction or explanation outside the workbook.
17. Keep all Markdown table columns in the exact order defined in the specification.
18. Use <br> inside table cells when multiple procedure steps or expected outputs must appear on separate lines.
19. If information is missing, use `<TBD>` and explain the assumption in the Note column.
20. Do not mark a test case as Pass unless actual execution evidence is provided.

Project documents begin below:
```

After that line, paste the project requirements or technical documents.

---

# 10. Final validation checklist for the LLM

Before returning the answer, verify all of the following:

- [ ] All workbook sheets are included.
- [ ] All project information fields are included.
- [ ] All functions appear in `Test case List`.
- [ ] Every sheet name matches its module.
- [ ] Every function has at least one test case.
- [ ] All IDs are unique.
- [ ] All test cases use the required eight columns.
- [ ] Procedures are numbered and detailed.
- [ ] Expected outputs are specific.
- [ ] New test cases use `Untested`.
- [ ] Test dates are blank unless execution evidence exists.
- [ ] Module totals match the number of detailed test cases.
- [ ] Test Report totals match all module totals.
- [ ] Coverage values are correctly calculated.
- [ ] No undocumented feature was invented.
