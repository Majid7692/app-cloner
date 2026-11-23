# Security Policy

## What this app does with your data

- Everything stays on your phone. App Cloner makes no network calls
  and sends nothing anywhere.
- The clone list (package names, custom names, icon files, frozen
  state) is stored as a private JSON file only this app can read.
  A backup file is created only when you export it yourself, and it
  goes only where you save it.
- Files cross between the personal and work profiles only as
  FileProvider `content://` URIs with a one-time read grant. Raw
  file paths are never used.
- The app asks for no password, no account, and no lock policy. The
  device admin receiver only owns the work profile so clones can be
  installed, hidden and removed inside it.

## Reporting a problem

If you find a security problem, open an issue with the title starting
`SECURITY:` and describe what happens, on which phone and Android
version. Do not include personal data, accounts, or credentials in
the report.

## If a credential ever leaks

If a password, token or key ever ends up in the repo, in an issue, or
in a screenshot: rotate it first, then tell the maintainer so the
exposed copy can be removed.
