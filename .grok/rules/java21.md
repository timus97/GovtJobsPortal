# Java 21 host — steering

This machine’s default `JAVA_HOME` is **JDK 17**. The product host needs **Temurin 21**.

Canonical JDK path (one line, no quotes): `.grok/java-home.txt`

```
C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot
```

## Always do this

1. Before **any** `mvnw`, `java`, or Spring Boot command, load the JDK:

   ```powershell
   . .\scripts\java21.ps1
   ```

2. Start the product UI with:

   ```powershell
   .\scripts\start-web.ps1
   ```

   That script sets `JAVA_HOME` from `.grok/java-home.txt`, packages `web` if needed, and binds **8090** (8080 is taken on this machine). Open http://localhost:8090

3. Do **not** run `mvn` from PATH (Apache Maven 3.8.5 + JDK 17). Use `.\mvnw.cmd` **after** `java21.ps1` (wrapper is Maven 3.9.9).

4. Do **not** assume `$env:JAVA_HOME` is already 21.

5. Student desk SoR is **Postgres 16** (`docker compose up -d student-db`). Do not add a JSON student store back. Admit/result bytes stay on disk (`STUDENT_FILES_DIR`).

## Slice order

Owner decision 2026-09-26: **UI and design first**. Coaching is out of scope. Do not deploy to GitHub Pages. After the UI is complete, migrate collectors from Node to Java, then cutover. Do not delete `client/`, `server/`, or `scripts/collect/` until cutover.
