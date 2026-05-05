# Development

`kotlin-parsing-charinput` is a Kotlin Multiplatform library. In the monorepo workspace, build and
test it through the `dev` tooling:

```bash
./dev build kotlin-parsing-charinput
```

From the project directory, or when working from a standalone checkout, run Gradle directly:

```bash
./gradlew build
```

## Documentation

Generate Dokka output with:

```bash
./gradlew dokkaGeneratePublicationHtml
```

In the monorepo workspace, validate hand-written documentation with:

```bash
./dev verify docs kotlin-parsing-charinput
```

## Publication Check

Before publishing, run the workspace dry-run:

```bash
./dev publish --dry-run kotlin-parsing-charinput
```

## KDoc Standards

Public KDoc should explain:

- whether a function consumes input or only looks ahead
- whether returned spans include text, positions, metrics, or no data
- when marks may expire for buffered inputs
- how `CharInput.EOB` is used as the end-of-input sentinel
- exact failure modes for token parsers

Hand-written docs should include runnable examples for the common in-memory workflow and notes about
JVM/Android-only reader and file inputs. Generated Dokka output remains the source of truth for exact
signatures and platform availability.
