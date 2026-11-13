# Tally

**Tally** is an Android app (Java, XML layouts) for lecturers to record students' marks, grade them and produce class reports.
It is the worked example for **DICT 229 Mobile Programming** at Daystar University (Athi River class).

## Features

- **Lecturer accounts:** sign up with name, staff number, email and password; account activation and
  "Forgot password?" by email (Firebase Authentication, see [EMAIL_SETUP.md](EMAIL_SETUP.md)).
  Passwords are stored hashed and salted.
- **Live input checking** on every form: fields turn red or green as you type, and buttons stay grey
  and locked until every detail is correct.
- **Marks entry per trimester** for 5 subjects (C++, Networking, Android App Dev, Database, Cyber Security),
  with a check that blocks a student who already has marks in the trimester. Retakes go into a new trimester.
- **Confirm, save and grade:** a Yes/No pop-up, then the average, grade (A-F) and remark (PASS/FAIL)
  are worked out and stored.
- **Summary** of the student just graded, and a **report** of all students with ▲/▼ sorting on every column,
  a layout that fits portrait and landscape, and **PDF download** to the phone's Downloads folder.
- Works with the **keypad open** and in **landscape** on every screen.

## Grading

| Average | Grade | Remark |
|---|---|---|
| 70 - 100 | A | PASS |
| 60 - 69 | B | PASS |
| 50 - 59 | C | PASS |
| 40 - 49 | D | PASS |
| below 40 | F | FAIL |

## Data

All app data is stored on the phone in **SQLite** (`tally.db`, see `DatabaseHelper.java`):
`lecturers`, `students`, `subjects`, `marks` and `results`. Firebase is only used to send and check the emails.

## Getting started

1. Clone the repository and open the folder in **Android Studio**.
2. Let Gradle sync, then press **Run** (Shift+F10) on an emulator or phone (Android 9 / API 28 or newer).
3. Optional: to send real activation and reset emails, follow [EMAIL_SETUP.md](EMAIL_SETUP.md).
   Without it, debug builds activate new accounts straight away (Test Mode).

## Working on the code

See [CONTRIBUTING.md](CONTRIBUTING.md): `main` is always stable, work happens on `feature/...` branches
that are merged into `develop` through Pull Requests.
