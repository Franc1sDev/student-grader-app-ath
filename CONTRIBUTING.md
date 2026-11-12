# How we work on this repository

This project uses a simple, professional Git branching model (a light version of **Git Flow**).

## Branches

| Branch | What lives there | Who changes it |
|---|---|---|
| `main` | Stable code only. Every commit here builds and runs. Releases are tagged here (`v1.0`, `v1.1` ...). | Only through a Pull Request from `develop` (or a `hotfix/...`). Direct pushes are blocked. |
| `develop` | The next version, where finished features come together. | Through Pull Requests from `feature/...` and `bugfix/...` branches. |
| `feature/<short-name>` | One new feature, e.g. `feature/pdf-report`. | You, while you work. Deleted after it is merged. |
| `bugfix/<short-name>` | A fix for something on `develop`. | You. Deleted after merge. |
| `hotfix/<short-name>` | An urgent fix for something already on `main`. Branch from `main`, merge into `main` AND `develop`. | You. Deleted after merge. |

```
main     ──●───────────────●──────────●  (v1.0)        (v1.1)
            \             /          /
develop      ●──●───●───●──────●────●
                 \     /        \  /
feature/x         ●──●           ●
```

## Everyday steps in Android Studio

1. **Start from the latest `develop`**: bottom-right branch widget > `develop` > **Checkout**, then **Git > Pull** (Ctrl+T).
2. **Make a branch**: branch widget > **New Branch** > `feature/what-you-are-doing`.
3. **Work and commit often**: **Git > Commit** (Ctrl+K). Write a clear message (see below).
4. **Push**: **Git > Push** (Ctrl+Shift+K). The first push creates the branch on GitHub.
5. **Open a Pull Request** on GitHub from your branch into `develop`, fill in the template, merge when it builds and works.
6. **Release**: when `develop` is stable, open a Pull Request `develop` > `main`, merge it, and tag the release (`v1.1`).

## Commit messages

Use a short type prefix and the present tense:

- `feat: add sortable report columns`
- `fix: keep fields visible when the keypad opens`
- `refactor: move field colours into FieldStyler`
- `docs: explain the database tables`
- `chore: update Gradle`

## Never commit

`local.properties` (it holds the SDK path and the Firebase settings), keystores (`*.jks`, `*.keystore`), `google-services.json`, or anything under `build/`. The `.gitignore` already blocks these.
