# Backend build baseline

## Resolved baseline

| Component | Version / policy |
|---|---|
| Java | 25 LTS; compiler and Maven Enforcer require Java 25 |
| Spring Boot | 4.1.2 parent and dependency management |
| Spring Modulith | 2.1.2 BOM; API at compile scope and architecture core at test scope |
| Maven | 3.9.16 through Apache Maven Wrapper 3.3.4 |
| Spotless Maven plugin | 3.10.4; Google Java Format default supplied by this pinned plugin |
| Maven Enforcer | 3.6.2 |
| CI JDK | Eclipse Temurin 25; patch updates remain enabled |
| CI runner | ubuntu-24.04 |

Spring MVC, validation, Actuator, and test dependencies inherit the Spring Boot dependency management. Do not independently override their transitive versions without a documented compatibility reason. Persistence, Flyway, and Security dependencies will be resolved when their features are introduced. Modulith core and its ArchUnit dependency remain test-scoped; runtime carries only the module annotation API.

## Official references checked during bootstrap

- [Spring Boot system requirements](https://docs.spring.io/spring-boot/system-requirements.html): 4.1.2 supports Java 25 and Maven 3.6.3 or newer.
- [Spring Boot artifact versions](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-starter-parent/maven-metadata.xml): 4.1.2 stable release available; 4.2 RC intentionally excluded.
- [Apache Maven downloads](https://maven.apache.org/download.cgi).
- [Apache Maven Wrapper](https://maven.apache.org/tools/wrapper/): official only-script wrapper, no checked-in wrapper JAR.
- [Spotless releases](https://github.com/diffplug/spotless/releases): Maven 3.10.4.

## Configuration and scope

The default local profile binds to loopback. Shared configuration exposes only minimal health and omits error details. No production profile is claimed; deployment and authentication are later milestones. No database auto-configuration is disabled to make tests pass: database dependencies simply are not introduced yet.

The Maven Wrapper verifies its distribution using a pinned SHA-256 checksum. First use requires network access. CI uses the same wrapper and verify command as development. Build caches are optimizations, not prerequisites.

## Verification scope

HTTP smoke tests start the real servlet server on a random port and check minimal health, absence of the environment endpoint, and safe errors for unknown routes. Business-rule unit tests start when business rules are implemented. No arbitrary coverage percentage is claimed for this bootstrap.

Workspace-specific proxy settings and the host trust store are supplied outside the repository when running in the build environment; neither TLS verification bypasses nor machine-specific settings belong in the project.

## Bootstrap validation result

Validated with Temurin 25.0.5 and Maven 3.9.16: clean verify succeeded, all three HTTP smoke tests passed, and the repackaged executable JAR returned healthy status on a random local port. XML/YAML parsing and git whitespace checks passed. Remote CI results are available in GitHub Actions; this document does not assume a run outcome before publication.

Mockito is configured as an explicit test JVM agent for Java 25. Spotless's formatter integration emits an upstream Unsafe deprecation warning; it is not suppressed and does not prevent verification.

## Commit 4 validation

The 2.1.2 stable Modulith release was resolved from [Maven Central metadata](https://repo.maven.apache.org/maven2/org/springframework/modulith/spring-modulith-bom/maven-metadata.xml), within the Boot 4.1-aligned 2.1 release family. [Official verification documentation](https://docs.spring.io/spring-modulith/reference/verification.html) describes the enforced cycle, encapsulation, and allowlist rules. The clean Java 25/Boot 4.1.2 build passes 18 tests. Negative architecture fixtures explicitly include test classes in the import scope; the production architecture scan continues to exclude test classes.
