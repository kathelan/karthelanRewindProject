# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Minimal Java 21 Maven project (`pl.kathelan:CORE`). Currently a starter project with a single main class.

## Build & Run Commands

```bash
# Compile
mvn clean compile

# Run main class
mvn exec:java -Dexec.mainClass="pl.kathelan.Main"

# Package JAR
mvn package

# Full build
mvn clean install
```

## Testing

No test framework is configured yet. To add tests, declare JUnit 5 or similar in `pom.xml` and add test classes under `src/test/java/pl/kathelan/`.

## Project Structure

- `src/main/java/pl/kathelan/` — main source root
- `src/test/java/` — test source root (empty)
- `pom.xml` — Maven config; Java 21, no external dependencies yet
- `target/` — build output (gitignored)