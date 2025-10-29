# JPMC Midas Core

## Overview
This repository contains the **Midas Core** service built for the J.P. Morgan Software Engineering virtual experience program. The project simulates a production-ready workflow where Kafka events drive real-time balance updates, an external incentive service enriches transactions, and Spring Boot exposes the resulting account state through a REST API. The goal is to showcase engineering practices that align with how software is delivered inside JPMorgan Chase & Co.

## What the Service Does
- Listens to the Kafka topic defined as `general.kafka-topic` (default: `transactions`) for inbound transaction events.
- Validates each event, filters out suspicious data (non-positive amounts, unknown users, insufficient funds), and persists approved transfers.
- Calls an incentive microservice (`services/transaction-incentive-api.jar`) to fetch promotional bonuses that are applied to the recipient.
- Maintains user balances and transaction history in an embedded H2 database for rapid local development.
- Exposes `GET /balance?userId={id}` so consumers—or QA reviewers—can confirm the current balance that reflects Kafka traffic plus any incentive adjustments.

## Repository Layout
```
.
├── application.yml                         # Central Spring configuration (Kafka topic, port, incentive URL)
├── pom.xml                                 # Maven build config (Java 17, Spring Boot 3.2)
├── services/transaction-incentive-api.jar  # Companion service that returns incentive amounts
├── src/main/java/com/jpmc/midascore/       # Spring Boot application (controllers, services, entities, config)
├── src/test/resources/test_data/           # CSV-like fixtures used during automated tests
└── target/                                 # Compiled bytecode and Surefire reports from previous builds
```

Key Spring components you will find under `src/main/java/com/jpmc/midascore/`:
- `MidasCoreApplication` boots the service.
- `TransactionListener` consumes Kafka messages via a custom listener container.
- `TransactionService` enforces business logic, retrieves incentives, updates account balances, and records successful transfers.
- `IncentiveClient` wraps `RestTemplate` to call the incentive API at `/incentive`.
- `BalanceController` exposes `GET /balance?userId=...` so balances can be inspected at runtime.
- `UserRecord` and `TransactionRecord` (JPA entities) map to the H2 schema, while `UserRepository` and `TransactionRecordRepository` provide CRUD access.
- `KafkaConsumerConfig` prepares the JSON-deserialising consumer factory scoped to `com.jpmc.midascore.foundation`.

## Prerequisites
- Java 17 (JDK) on your PATH.
- Maven (the project ships with `./mvnw`/`mvnw.cmd`, so a standalone Maven install is optional).
- A local or remote Kafka cluster reachable from your environment.
- (Optional) Docker or the Confluent CLI if you prefer containerised Kafka.

> **Tip:** The repo already contains compiled classes under `target/`, but you should rebuild once the correct JDK is installed to ensure compatibility with your machine.

## Getting Started
1. **Install dependencies**
   ```bash
   sdk install java 17-tem           # or use Homebrew, asdf, etc.
   ```
   (Any method that provides a Java 17 runtime is acceptable.)

2. **Install/launch Kafka (one-time)**
   ```bash
   # using Confluent Platform
   confluent local services start
   confluent kafka topic create transactions --if-not-exists
   ```
   Adapt the commands to your preferred Kafka distribution or Docker image.

3. **Start the incentive service**
   ```bash
   java -jar services/transaction-incentive-api.jar
   ```
   The jar listens on port `8080` by default; you can point `incentive.api.base-url` elsewhere in `application.yml`.

4. **Run the Spring Boot application**
   ```bash
   ./mvnw spring-boot:run
   ```
   The API will come up on `http://localhost:33400`.

5. **Produce a test transaction**
   ```bash
   kafka-console-producer \
     --topic transactions \
     --broker-list localhost:9092 \
     --property parse.key=false \
     --property value.serializer=org.apache.kafka.common.serialization.StringSerializer
   > {"senderId":1,"recipientId":2,"amount":42.50}
   ```
   The listener expects JSON payloads matching `com.jpmc.midascore.foundation.Transaction`.

6. **Query a balance**
   ```bash
   curl "http://localhost:33400/balance?userId=2"
   ```
   Successful processing returns JSON similar to:
   ```json
   {"amount": 142.5}
   ```

> **Note:** If Kafka is offline when you run `./mvnw clean verify`, the build still succeeds but you will see repeated `Broker may not be available` warnings. Either start Kafka beforehand or disable the listener for test runs (e.g., run with `-Dspring.kafka.bootstrap-servers=localhost:9092` pointing to a live broker). This helps anyone cloning the repo avoid confusing log noise.

## Configuration Notes
- `application.yml` contains the default port (33400), Kafka consumer group (`midas-core`), serializers, and incentive API URL. Override these using environment variables or command-line arguments when deploying.
- The Kafka listener uses a custom container factory named `transactionKafkaListenerContainerFactory`, so avoid renaming it unless the configuration is updated.
- The embedded H2 database is in-memory by default. For persistence between runs, add the usual Spring `spring.datasource.*` overrides.

## Automated Testing & Reports
- Execute unit and integration tests via:
  ```bash
  ./mvnw clean test
  ```
- Surefire HTML/XML outputs are written to `target/surefire-reports/`. Notable suites include:
  - `TaskOneTests` (smoke test for balance retrieval),
  - `TaskTwoTests` (transaction ingest and validation),
  - `TaskFiveTests` (end-to-end incentive flow).
- Test fixtures live in `src/test/resources/test_data/` and can be repurposed for manual sanity checks.

## Verification Checklist (For Maintainers & Reviewers)
Use the following steps when you want to confirm someone else’s submission or pull request:
1. **Build & Test**
   - `./mvnw clean verify` must succeed on a clean workspace.
   - Inspect `target/surefire-reports/*.txt` for skipped or flaky tests.
2. **Static Review**
   - Check for new Kafka topics or configuration changes in `application.yml`.
   - Review `TransactionService` updates for new guard rails (look for log messages such as “Discarding transaction…” and “Recorded transaction …”).
3. **Functional Walkthrough**
   - Launch the incentive jar and Spring Boot app as described above.
   - Produce at least one valid transaction and one invalid transaction (e.g., negative amount) to confirm only the valid one updates the balance.
   - Hit `GET /balance?userId=...` to ensure the response matches expectations.
4. **Logging & Observability**
   - Verify SLF4J logs surface the expected statements: `Recorded transaction {}` for success, `Discarding transaction …` for rejections, and `Failed to retrieve incentive…` if the incentive jar is offline.
5. **Database State**
   - (Optional) Connect to the H2 console (`jdbc:h2:mem:testdb`) to inspect `user_record` and `transaction_record` tables if deeper verification is needed.
6. **Security & Dependency Hygiene**
   - Confirm new dependencies are added via `pom.xml` with versions compatible with Spring Boot 3.2.5.
   - Ensure no secrets or tokens are committed; configuration should rely on environment overrides.

Document the outcome of each step when reviewing contributions so that the GitHub history clearly demonstrates compliance with J.P. Morgan’s software engineering standards.

## Troubleshooting
- **Missing Java Runtime:** Install a Java 17 JDK; the repo’s compiled classes require at least Java 17.
- **Kafka deserialisation failures:** Confirm the producer is sending plain JSON with camelCase field names (`senderId`, `recipientId`, `amount`) and numeric values.
- **No incentive added:** Make sure `transaction-incentive-api.jar` is running; otherwise `IncentiveClient` logs a warning and falls back to zero incentives.

## Attribution
This repository contains work completed for the **J.P. Morgan Chase & Co. Software Engineering Virtual Experience** (Forage). It is shared for educational and portfolio purposes only and does not carry an open-source licence. Please respect the Forage and J.P. Morgan usage guidelines when referencing or reusing any of these materials.

## Next Steps
When publishing to GitHub, include this README, add a license if appropriate, and consider adding CI (GitHub Actions) that runs `./mvnw verify` and maybe spins up Kafka/Testcontainers for a full integration scenario. That mirrors the oversight expected within J.P. Morgan Software Engineering teams.
