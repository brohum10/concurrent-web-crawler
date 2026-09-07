# Contributing to Concurrent Web Crawler

Contributions should preserve bounded concurrency, responsible crawling, and safe handling of untrusted URLs and responses.

## Local checks

```bash
./gradlew clean test --no-daemon
./gradlew bootJar --no-daemon
```

PostgreSQL-backed development is available through `docker compose up --build`. The default in-memory mode should remain usable for tests and local exploration.

## Expectations

- Keep network safety checks in front of every request and redirect.
- Preserve explicit page, depth, body-size, retry, queue, and concurrency limits.
- Add deterministic tests for scheduler, URL, parsing, indexing, or API behavior.
- Avoid live internet dependencies in the automated test suite.
- Update `docs/openapi.yaml` and `docs/architecture.md` when contracts or boundaries change.

Pull requests should include the behavior changed, safety implications, tests run, and benchmark evidence for performance claims.
