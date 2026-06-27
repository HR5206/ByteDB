# ByteDB

A key-value storage engine built from scratch in Java 17, based on the **LSM-tree** (Log-Structured Merge-tree) architecture — the same data structure that powers Google's LevelDB, Meta's RocksDB, and Apache Cassandra.

This project was written part-by-part as a deep dive into how real databases persist and retrieve data. No external libraries. No frameworks. Just raw Java and systems thinking.

---

## Why LSM trees?

Most developers interact with databases at a high level — they write SQL or call an ORM and forget about what happens underneath. But when you're building something that needs to handle millions of writes per second without breaking a sweat, the data structure you choose matters enormously.

LSM trees solve a specific problem: **how do you make writes fast when disks are slow?**

The answer is clever — you don't write to disk immediately. You absorb writes into memory, log them to an append-only file for safety, and then periodically flush to disk in large sorted batches. Reads are a bit more work (you have to check both memory and disk), but writes become blazing fast.

This project implements the whole thing from scratch, so you can see exactly how each piece fits together.

---

## What's built

### Part 1 – MemTable + Write-Ahead Log
The write path starts here. Every key-value pair lands in an in-memory **SkipList** (hand-rolled, not `TreeMap`) and is simultaneously appended to a **Write-Ahead Log (WAL)** on disk. If the process crashes, we replay the WAL to rebuild the MemTable. The SkipList gives O(log n) insert and lookup with very simple code.

### Part 2 – Immutable SSTable
When the MemTable gets too large, it's flushed to disk as a sorted, immutable file called an **SSTable**. Each file includes a **Bloom filter** (also hand-implemented) that lets us quickly say "this key definitely isn't here" without reading the whole file, and a binary-searchable index for fast point lookups.

### Part 3 – Compaction Manager
Over time you accumulate a lot of small SSTables. A background **Compaction Manager** periodically merges the oldest files into a single larger file, discarding overwritten or deleted keys. This is what keeps read performance from degrading as the database grows. Merges happen atomically — the old files are replaced only after the new file is fully written.

### Part 4 – LRU Cache + ByteDB façade
Hot keys get served from an **LRU cache** (doubly linked list + hashmap, O(1) get and put) before touching the MemTable or disk. The `ByteDB` class ties everything together into a clean public API: `put`, `get`, `delete`. It also handles flushing the MemTable when it exceeds the configured size threshold.

### Part 5 – Range Scans
The engine supports efficient **range queries** — give it a start key and an end key, and it returns all matching entries in sorted order. This works by merging ordered streams from the MemTable and each SSTable using a priority queue, so you never load the entire dataset into memory.

### Part 6 – Interactive CLI
A simple command-line interface that lets you interact with the database in real time. Type `PUT`, `GET`, `DELETE`, or `SCAN` commands and see the result along with a timing measurement in milliseconds. Good for demos, debugging, and understanding how the layers interact.

---

## Getting started

You need **JDK 17** installed. That's it.

```bash
git clone https://github.com/HR5206/ByteDB.git
cd ByteDB
javac --release 17 *.java
java ByteDBCLI
```

Once the CLI is running:
```
PUT city Hyderabad
PUT lang Java
GET city
SCAN city lang
DELETE city
GET city
EXIT
```

Each command prints its result and a line like `[1.203 ms (831.25 ops/sec)]` so you can see the performance live.

---

## Project structure

```
ByteDB/
├── SkipList.java            # In-memory sorted structure (the MemTable's backbone)
├── WriteAheadLog.java       # Append-only durability log
├── MemTable.java            # Combines SkipList + WAL into the write buffer
├── BloomFilter.java         # Probabilistic membership check (no false negatives)
├── SSTableWriter.java       # Flushes sorted entries to disk
├── SSTableReader.java       # Binary-search reads + Bloom filter check
├── CompactionManager.java   # Background SSTable merge (k-way merge)
├── LRUCache.java            # O(1) get/put cache with doubly-linked list
├── ByteDB.java              # Public API – put, get, delete, flush
├── SkipListIterator.java    # Forward iterator for MemTable range scans
├── SSTableIterator.java     # Sequential reader for SSTable range scans
├── RangeScanner.java        # Merges MemTable + SSTable streams into one ordered view
├── ByteDBCLI.java           # Interactive command-line interface
└── data/
    ├── wal.log              # Write-ahead log (created at runtime)
    └── sst/                 # SSTable files (created at runtime)
```

---

## Design rules (intentionally strict)

- **Java 17 standard library only.** No Maven. No Gradle. No external jars.
- **All data structures are hand-implemented.** The SkipList, Bloom filter, and LRU cache are written from scratch — not wrappers around `java.util`.
- **No TODO stubs.** Every method in every class is fully implemented.
- **Benchmarks use `System.nanoTime()`** and report in ops/sec.

These constraints exist on purpose. The goal isn't to build the fastest possible key-value store — it's to understand exactly how each component works at the code level.

---

## Performance (rough numbers on a mid-range laptop)

| Operation | Throughput |
|-----------|-----------|
| `PUT` (MemTable + WAL) | ~80,000 – 150,000 ops/sec |
| `GET` (cache hit) | ~2,000,000+ ops/sec |
| `GET` (MemTable, no cache) | ~200,000 – 400,000 ops/sec |
| `GET` (SSTable, binary search) | ~20,000 – 60,000 ops/sec |
| Range scan (in memory) | Limited by iterator speed, not I/O |

Real-world systems like RocksDB do orders of magnitude better, but they've been tuned for years by large teams. The architecture is the same.

---

## What I learned building this

The most surprising thing was how much the write-ahead log matters. Without it, any crash between a write being accepted and it being flushed to disk means data loss. With it, you can recover to a consistent state every time — and it barely costs anything because it's append-only.

The second thing that clicked was why compaction exists. It's not optional. If you skip it, every read eventually has to check every SSTable ever written, and your read performance falls off a cliff. Compaction is the "cleanup crew" that makes LSM trees actually practical.

---

## CI/CD

Every push runs a GitHub Actions workflow that compiles the project and smoke-tests the CLI:

```yaml
- name: Compile
  run: javac --release 17 *.java
- name: Smoke-test CLI
  run: |
    printf 'PUT x 1\nGET x\nEXIT\n' | java ByteDBCLI
```

Free tier. No configuration needed beyond the YAML file in `.github/workflows/`.

---

## Acknowledgements

This project was built part-by-part as a mentored systems engineering exercise. The design is inspired by the LevelDB and RocksDB documentation, the original LSM-tree paper by O'Neil et al. (1996), and the book *Designing Data-Intensive Applications* by Martin Kleppmann.

---

*Built with curiosity, Java, and no external dependencies.*
