# Privacy Policy — CodingTestKit

_Last updated: 2026-04-28_

CodingTestKit is an IntelliJ IDE plugin that helps users fetch, test, and submit
algorithm problems from public competitive programming platforms. This document
describes what data the plugin handles and where it goes.

## Summary

- **No data is sent to any server owned by the plugin author.**
- **No analytics, telemetry, or tracking.**
- All secrets (site login cookies, GitHub access tokens) are stored **locally**
  using IntelliJ's `PasswordSafe` (the OS keychain on macOS / Windows
  Credential Manager / `KWallet` or an encrypted file store on Linux).
- Non-secret settings (usernames, repository name, feature toggles) are stored
  in IntelliJ's per-user settings directory like any other plugin state.

## Data stored on your machine

| Item | Where | Purpose |
| --- | --- | --- |
| Site login cookies (Programmers, SWEA, LeetCode, Codeforces) | `PasswordSafe` | Authenticate when submitting or listing your solved problems. |
| GitHub personal access token | `PasswordSafe` | Push accepted solutions to your repository, if configured. |
| Usernames, repository name, feature toggles | IntelliJ settings XML | Remember your preferences between sessions. |
| Fetched problem HTML, test cases, images | Project files you opened in your IDE | Let you view and solve problems offline. |

## Network requests the plugin makes

The plugin communicates **only** with the following first-party endpoints, and
only when the user triggers the corresponding feature:

- `https://school.programmers.co.kr/` — Programmers problem fetch, search,
  submit.
- `https://swexpertacademy.com/` — SWEA problem fetch, search, submit.
- `https://leetcode.com/` — LeetCode problem fetch, search, submit, solved
  lookup.
- `https://codeforces.com/` — Codeforces problem fetch, search, submit.
- `https://api.github.com/` — GitHub repo listing and push (only if GitHub
  integration is enabled).
- `https://translate.googleapis.com/` — optional problem title / description
  translation (only if you click the Translate button).

No other network destinations are contacted. No payload from your machine is
sent anywhere else.

## Optional feedback form (Google Forms)

The Settings tab includes a "Report Bug / Suggest Feature" button that opens
a Google Form (<https://forms.gle/Qqi5gDoHSi2HU1Xs5>) in your default
browser. The plugin itself does **not** transmit any data to Google Forms —
the form opens only when you click the button, and any data you choose to
submit is entered manually by you on Google's website. Google's own privacy
policy applies once you are on the form page. The form supports anonymous
submission; no personally identifiable information is required.

## Third-party content

All problem content fetched by the plugin remains copyrighted by its respective
platform. Content is stored only in local project files for the user's personal
study. The plugin does not redistribute, republish, or transmit fetched content
to any other party.

## Removing stored data

- **Log out** per platform from `CodingTestKit → Settings → Login` to remove
  the corresponding cookie from `PasswordSafe`.
- **Remove GitHub integration** from `CodingTestKit → Settings → GitHub
  Integration → Logout` to clear the GitHub token.
- Uninstalling the plugin also removes its entries from `PasswordSafe`.

## Contact

- Issues: <https://github.com/dj258255/codingtestkit/issues>
- Email: <dj258255@naver.com>
