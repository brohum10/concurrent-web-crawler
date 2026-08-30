# Concurrent Web Crawler & Search Engine

[![CI](https://github.com/brohum10/concurrent-web-crawler/actions/workflows/ci.yml/badge.svg)](https://github.com/brohum10/concurrent-web-crawler/actions/workflows/ci.yml)
[![Java 17](https://img.shields.io/badge/Java-17-ED8B00.svg)](https://adoptium.net/)
[![Spring Boot 4.1](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F.svg)](https://spring.io/projects/spring-boot)

A responsible, multithreaded Java crawler paired with an in-process BM25 search index and a Spring Boot REST API. The project emphasizes concurrency control, URL safety, deterministic tests, and performance that can be reproduced locally.

## What it demonstrates

- Thread-safe breadth-first crawling with a bounded worker pool
- URL canonicalization, duplicate suppression, robots.txt rules, retries, and per-host rate limiting
- Private-network and localhost blocking for safer user-supplied crawl targets
- Inverted indexing and BM25 top-k ranking with hash maps and a bounded priority queue
- In-memory development mode plus optional PostgreSQL persistence
- Docker Compose, automated tests, CI, and a deterministic benchmark

## Quick start

Requirements: Java 17 or newer. The Gradle wrapper downloads the correct build tool automatically.

```bash
./gradlew bootRun
```

Start a bounded crawl:

```bash
curl -X POST http://localhost:8080/api/crawl \
  -H 'Content-Type: application/json' \
  -d '{"seed":"https://example.com","maxPages":25}'
```

Search indexed pages:

```bash
curl 'http://localhost:8080/api/search?q=distributed+systems&limit=10'
```

For persistent storage:

```bash
docker compose up --build
```

## Architecture

```text
REST API -> crawl coordinator -> BFS frontier -> worker pool
                                         |-> safety / robots / rate limit
                                         |-> HTTP fetch + HTML parsing
                                         |-> document store (memory or PostgreSQL)
                                         `-> BM25 inverted index -> top-k search
```

See [docs/architecture.md](docs/architecture.md) for component boundaries, concurrency decisions, safety constraints, and scaling extensions.

## Tests

```bash
./gradlew test
```

The test suite covers URL normalization, robots.txt parsing, duplicate/cycle handling under concurrency, BM25 ranking, document replacement, and end-to-end indexing through the service boundary. Network access is replaced with deterministic fixtures.

## Reproducible performance check

```bash
./gradlew benchmark --args='25000 1000'
```

The benchmark creates 25,000 deterministic synthetic documents, runs 1,000 top-10 queries, and prints Recall@10 plus p50 and p95 latency. Results depend on CPU and JVM warm-up; the command above is the source of truth rather than a hard-coded claim.

Latest local run on an arm64 Mac (August 30, 2026):

| Documents | Queries | Recall@10 | p50 | p95 |
|---:|---:|---:|---:|---:|
| 25,000 | 1,000 | 1.000 | 12.108 ms | 13.399 ms |

These numbers measure the deterministic search benchmark, not network crawling. Real crawl throughput is intentionally constrained by host politeness settings and network latency.

## API

| Method | Route | Purpose |
|---|---|---|
| `POST` | `/api/crawl` | Crawl a seed URL up to the configured page limit |
| `GET` | `/api/search?q=...&limit=...` | Run BM25 search over indexed pages |
| `GET` | `/api/health` | Return status and indexed-document count |

## Responsible-use note

Only crawl sites you are allowed to access. The built-in controls reduce accidental load and common unsafe targets, but they do not replace legal review, site-specific terms, authentication controls, or production network isolation.

## License

MIT
