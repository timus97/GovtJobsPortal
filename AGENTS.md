# GovtJobsPortal — agent rules

Java 21 + Spring Boot 3.4 + Thymeleaf is the product host. Node stays for collectors until slices 8–10. Do not delete `client/`, `server/`, or `scripts/collect/` yet.

## JDK

Default `JAVA_HOME` on this machine is JDK 17. Always load Temurin 21 first:

```powershell
. .\scripts\java21.ps1
.\scripts\start-web.ps1
```

Path is stored in `.grok/java-home.txt`. Details: `.grok/rules/java21.md`.

## Work order

Owner decision 2026-09-26:

1. **UI and design first** on the Java host (jobs, prepare, match, desk, ops).
2. **Coaching is out of scope.** Do not add syllabus packs, mocks, or plan UX. Leave the four existing packs untouched.
3. **Do not deploy.** GitHub Pages is not the product host. Hosting waits until the UI is complete.
4. **Then** migrate collectors from Node to Java, then cutover. Do not delete `client/`, `server/`, or `scripts/collect/` until that cutover.

## Product locks

- Catalog is git JSON. Students are gitignored. Never put PII in health, git, or Pages.
- Matcher: unknown ≠ fail. Never invent age relaxations. Never say “you are eligible.”
- Reservation category is required to match.
- Official links must be `https://`. Student files: owner-only, magic-byte PDF/JPEG/PNG ≤ 5 MB.
