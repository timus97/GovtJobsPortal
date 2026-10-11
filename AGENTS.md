# GovtJobsPortal — agent rules

Java 21 + Spring Boot 4.1.1 + Thymeleaf is the product host. Collectors and the daily job are Java as of 2026-10-10.

## JDK

Default `JAVA_HOME` on this machine is JDK 17. Always load Temurin 21 first:

```powershell
. .\scripts\java21.ps1
.\scripts\start-web.ps1
```

Path is stored in `.grok/java-home.txt`. Details: `.grok/rules/java21.md`.

## Work order

Owner decision 2026-09-26. Collector cutover 2026-10-10:

1. **UI and design first** on the Java host (jobs, prepare, match, desk, ops).
2. **Coaching is out of scope.** Do not add syllabus packs, mocks, or plan UX. Leave the four existing packs untouched.
3. **Do not deploy.** GitHub Pages is not the product host. Hosting waits until the UI is complete.
4. Collectors and the daily job are already Java. Do not add a Node collector, a React client, or an Express server back.

## Product locks

- Catalog is git JSON. Students are gitignored. Never put PII in health, git, or Pages.
- Matcher: unknown ≠ fail. Never invent age relaxations. Never say “you are eligible.”
- Reservation category is required to match.
- Official links must be `https://`. Student files: owner-only, magic-byte PDF/JPEG/PNG ≤ 5 MB.


# Repository-Wide Agent Instructions

- Inspect the existing project structure and relevant documentation before making conclusions.
- Preserve existing user changes and the working tree.
- Do not overwrite or revert work that you did not create.
- Distinguish verified facts from assumptions and unverified risks.
- Follow the specific task instructions and any more specific instructions in nested `AGENTS.md` files.
- Do not expose secret values found in the repository.
- Before running commands, consider whether they can change project state; avoid destructive operations unless explicitly authorized.
- Report what was actually inspected, executed, and verified.
