# SupplySphere — Global Supply Chain Management Platform

SupplySphere is a self-contained Java platform that turns six classic algorithm
families into real supply-chain tasks. It ships a small document corpus and a
small supply scenario, and exposes everything through **two front ends that
share one engine**: a line-oriented console and a web application (dashboard +
JSON API).

Everything runs on the standard JDK alone — no database, no build tool and no
third-party libraries.

## Features

- **Document search** over a knowledge base in four modes:
  exact phrase, multi-keyword, fuzzy (typo-tolerant) and similar (topical),
  with relevance scores and highlighted snippets.
- **Knowledge base** browsing and corpus statistics.
- **Duplicate detection**: near-duplicate documents ranked by shingle similarity.
- **Supply network analysis**: maximum daily throughput and the bottleneck links
  (minimum cut), with a correctness certificate.
- **Delivery scheduling**: recommended plan vs. baseline, exact optimum and
  best-possible lower bound.
- **Batch / part code validation**: primality and digit-structure check.
- **Algorithm report**: the main and benchmark algorithm chosen for each module.

## Modules

Each module ships one **main** algorithm and one independent **benchmark** that
must agree on the same input, plus documented rejected alternatives.

| Module | CO  | Task | Main | Benchmark |
|--------|-----|------|------|-----------|
| 1 | CO1 | Exact phrase retrieval | Knuth-Morris-Pratt (KMP) | Rabin-Karp |
| 2 | CO2 | Multi-pattern & suffix search | Aho-Corasick | Suffix array + Kasai LCP |
| 3 | CO3 | Approximate matching | Bounded edit distance | Sequence alignment |
| 4 | CO4 | Network flow & cuts | Dinic max-flow | Ford-Fulkerson / Edmonds-Karp |
| 5 | CO5 | Scheduling & NP approximation | LPT scheduling | Vertex-cover 2-approx |
| 6 | CO6 | Randomised & parallel | Miller-Rabin primality | Parallel prefix scan |

## Project structure

```
SupplySphere/
├── src/
│   ├── SupplySphereApp.java
│   └── com/supplysphere/
│       ├── algorithm/    # dp, flow, np, parallel, randomised, stringalgorithms, suffixstructures
│       ├── core/         # config, util (Preconditions, DomainException, Text)
│       ├── dataset/      # Article, CorpusLoader
│       ├── engine/       # QueryEngine, ModuleReport, EngineList/Set/Sort
│       ├── ops/          # SupplyNetwork, JobSet, OperationsService
│       ├── repository/   # ArticleRepository
│       ├── service/      # SearchService
│       ├── ui/           # SearchConsole
│       └── web/          # WebApp
├── test/                 # seven smoke suites
├── data/
│   ├── corpus/           # wiki_01.txt .. wiki_05.txt
│   └── supply/           # network.csv, jobs.csv
├── .classpath
└── .project
```

## Requirements

- JDK 17 or newer (records + text APIs are used).

## Build

Compile the main sources into `bin/`.

Windows (PowerShell):
```powershell
javac -encoding UTF-8 -d bin (Get-ChildItem -Recurse src -Filter *.java).FullName
```

macOS / Linux:
```bash
javac -encoding UTF-8 -d bin $(find src -name "*.java")
```

## Run — Console

```bash
java -cp bin SupplySphereApp
```

A bare line with no verb is treated as `exact`, so you can just type words and
press Enter. Commands:

```
stats                       corpus statistics
exact   <query>             literal phrase scan (KMP)
keywords <terms...>         multi-term scan (Aho-Corasick)
fuzzy   <query>             typo-tolerant scan (bounded edit distance)
similar <query>             topical ranking (shingle similarity)
scan    <query>             main vs benchmark matcher must agree
network                     supply-network throughput and bottleneck
schedule [machines]         schedule delivery tasks (LPT vs optimum)
validate <code>             primality / digit check on a batch code
duplicates                  near-duplicate documents
report                      main/benchmark choice per module
help                        this text
quit | exit                 leave
```

## Run — Web

```bash
java -cp bin com.supplysphere.web.WebApp data/corpus 8080
```

If the port is busy, the next free port is chosen automatically and the URL is
printed. Open that URL for the dashboard.

JSON endpoints:

```
GET /api/stats
GET /api/search?q=<query>&mode=<exact|keywords|fuzzy|similar>
GET /api/network
GET /api/schedule?machines=<n>
GET /api/validate?code=<code>
GET /api/duplicates
GET /api/report
```

## Tests

Compile and run the smoke suites (separate output dir keeps them isolated):

Windows (PowerShell):
```powershell
javac -encoding UTF-8 -cp bin -d bin-tests (Get-ChildItem -Recurse test -Filter *.java).FullName
java -cp "bin;bin-tests" SmokeDp
```

macOS / Linux:
```bash
javac -encoding UTF-8 -cp bin -d bin-tests $(find test -name "*.java")
java -cp "bin:bin-tests" SmokeDp
```

Available suites: `SmokeDp`, `SmokeStrings`, `SmokeFlow`, `SmokeNp`,
`SmokeRandomParallel`, `SmokeOps`, `SmokeEngine`.

## Data

- **Knowledge base** — `data/corpus/wiki_01.txt` .. `wiki_05.txt`:
  Procurement, Warehouse management, Shipment tracking, Supplier evaluation,
  Inventory management.
- **Supply network** — `data/supply/network.csv`: 12 nodes, 19 links.
  Maximum throughput 90 units/day; bottleneck links `M1→TARGET` (40) and
  `M2→TARGET` (50).
- **Delivery workload** — `data/supply/jobs.csv`: 12 tasks, 129 minutes total.
  Over 3 warehouses: baseline 45, recommended 45, optimum 43, lower bound 43.

## Notes

- Module and algorithm names appear only on the **Algorithm report** screen, so
  the task screens stay focused on supply-chain work.
- The exact scheduling optimum is computed only for small workloads; larger ones
  report the heuristic plus the lower bound.
