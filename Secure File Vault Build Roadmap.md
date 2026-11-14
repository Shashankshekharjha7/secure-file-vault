# Secure File Vault: Build Roadmap

Oct 18, 2026 · @Rameshwar Jha

## The verdict

Build it: a Secure File Vault with React, Spring Boot microservices, MySQL + MongoDB and AWS is a strong fresher project, as long as you cap it at 4 services and ship a live MVP in about 7 weeks before adding anything else.

**Blockchain: no, not inside this project.** Add a tamper-evident hash-chain audit log instead. It teaches the same core idea in about 60 lines of Java and keeps you on the stack recruiters filter for (full reasoning in "Should you add blockchain?" below).

**Why this project gets interviews.** It hits the keywords Java backend job posts ask for: Spring Boot, REST, Spring Security + JWT, JPA/MySQL, MongoDB, Spring Cloud Gateway, Eureka, Docker, AWS, CI/CD, JUnit. It also has one real differentiator: an encryption design (AES-256-GCM with per-file keys) that you can explain on a whiteboard.

**The one rule that decides whether you finish.** The MVP is the seven features in the next section: register and login, encrypted upload, list, decrypted download, delete, expiring share links, and an activity log. Nothing else gets built until that MVP is deployed on AWS with a public URL.

**Effort assumed in this plan:** 4 to 5 focused hours a day, 6 days a week. Working full-time on it, cut the timeline by about a third; if Spring or React is new to you, plan 9 to 10 weeks.

## What you're building

A web app where a logged-in user uploads files, the backend encrypts each file with its own AES-256 key before storing it, and only the owner (or someone holding an expiring share link) can download it decrypted.

**MVP: build exactly these, nothing more**

| # | Feature | What the user sees | What it proves in an interview |
| --- | --- | --- | --- |
| 1 | Register + login | Email/password signup, stays logged in | Spring Security, BCrypt, JWT access + refresh tokens |
| 2 | Upload | Drag-drop, progress bar, max 10 MB, allowed types only | Multipart upload, AES-256-GCM, MongoDB GridFS |
| 3 | My files | Paginated list, search by name, sort by date/size | JPA, MySQL indexes, pagination |
| 4 | Download | Original file comes back intact | Streaming decryption, SHA-256 integrity check |
| 5 | Delete | File disappears, storage freed | Keeping two databases consistent |
| 6 | Share link | Link that expires (e.g. 24 h) and/or after N downloads | Secure random tokens, authorization rules |
| 7 | Activity log | "You uploaded X", "Link for Y was used" | Auditing; later becomes the hash chain |

**After the MVP is live (pick from the stretch list, in order):** folders, file versions, storage quota, Redis rate limiting, Kafka/RabbitMQ events, AWS KMS for the master key, S3 as a second storage backend.

**Not doing at all:** blockchain, Kubernetes/EKS (too costly for a demo), real-time collaboration, a mobile app. Saying "I deliberately left these out" in an interview is a strength, not a gap.

## Architecture

Four Spring Boot services sit behind one gateway; MySQL holds everything relational, MongoDB holds only encrypted file bytes, and the keys never sit next to the data they protect.

&#91;embedded content: deployed architecture · 4 services, 2 databases\]

Locally, the Vite dev proxy plays CloudFront's role and Docker Compose runs both databases; on AWS the same containers run on one EC2 instance.

**Which data goes where**

| Data | Stored in | Why there |
| --- | --- | --- |
| Users, Argon2id password hashes, refresh-token hashes | MySQL `auth_db` | Unique email constraint; owned only by auth-service |
| File metadata: owner, name, size, SHA-256, status, wrapped key, IVs | MySQL `vault_db` | Filter, sort and paginate by owner; foreign keys to shares |
| Share links, audit log | MySQL `vault_db` | Joins with files, atomic counters, ordered hash chain |
| Encrypted file bytes | MongoDB GridFS | Stores binaries in 255 KB chunks, past MongoDB's 16 MB document limit |
| Master key (protects every file key) | `.env` locally; AWS KMS on AWS, where it never leaves KMS | Never in Git, never in either database |
| JWT secret, database passwords | `.env` locally; SSM Parameter Store on AWS | Never in Git or in a container image |

**Encryption in one paragraph.** Each upload gets a fresh random AES-256 data key (DEK). The file is encrypted with AES-GCM using a random 12-byte IV and the file id as associated data. The DEK is then wrapped by the master key: locally by an AES key from `.env`, on AWS by KMS. MySQL stores the wrapped DEK and IVs, MongoDB stores the ciphertext, and the master key is in neither, so a stolen copy of either database reveals nothing.

**Security model (state it honestly).** This is application-level, server-side encryption, not end-to-end: vault-service can decrypt, because it must to serve downloads. It protects against a stolen database dump or backup, a leaked MongoDB cluster, and silent tampering with stored bytes (GCM detects it). It does not protect against an attacker who controls vault-service itself, or who steals a user's password or live token. True end-to-end encryption would mean encrypting in the browser with the Web Crypto API, giving up server-side previews, scanning and easy recovery. Put this paragraph in your README under "Known limitations"; interviewers respect a candidate who knows where their system's guarantees stop.

## Tech stack and versions

Use Spring Boot 4.1.x with Spring Cloud 2025.1.x, Java 21, React 19 + Vite, MySQL 8.4 and MongoDB 8.0. Let start.spring.io pick the exact patch versions so Boot and Cloud always match.

| Layer | Choice | Version | Why / note |
| --- | --- | --- | --- |
| Language | Java (Temurin or Amazon Corretto) | 21 LTS | Records, virtual threads; 25 LTS also works |
| Backend | Spring Boot | 4.1.x | 3.5 open-source support ended 30 Jun 2026 and 4.0's ends 31 Dec 2026 ([HeroDevs](https://www.herodevs.com/blog-posts/spring-boot-versions-eol-dates-and-latest-releases-april-2026)) |
| Microservices | Spring Cloud "Oakwood" | 2025.1.x | 2025.1.2 added Boot 4.1.0 support ([spring.io](https://spring.io/blog/2026/06/11/spring-cloud-2025-1-2-aka-oakwood-has-been-released/)) |
| Gateway | Spring Cloud Gateway Server WebFlux | from Cloud BOM | Starter is `spring-cloud-starter-gateway-server-webflux` |
| Service discovery | Eureka | from Cloud BOM | Classic and easy to explain; static URLs are a fine fallback in week 1 |
| Security | Spring Security + OAuth2 Resource Server (JWT) | from Boot | No hand-written JWT filter needed |
| Password hashing | Argon2id via Spring's `Argon2PasswordEncoder` | needs `org.bouncycastle:bcprov-jdk18on` | OWASP's first choice; bcrypt is listed for legacy systems ([OWASP](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)) |
| Relational DB | MySQL + Spring Data JPA + Flyway | 8.4 LTS | Users, file metadata, shares, audit |
| Document/blob DB | MongoDB + Spring Data MongoDB (GridFS) | 8.0 | Encrypted file bytes |
| File-type check | Apache Tika (`tika-core`) | latest | Detects the real type from the bytes, not the browser's claim |
| Key management | AWS SDK for Java v2, `kms` module | 2.x | Master key in AWS KMS on the deployed version |
| API docs | springdoc-openapi (Swagger UI) | 3.x (the Boot 4 line) | Live API docs for recruiters |
| Tests | JUnit, Mockito, Testcontainers | from Boot | Real MySQL/Mongo in tests |
| Frontend | React + Vite + TypeScript | React 19 | Plus React Router, Axios, TanStack Query, Tailwind CSS |
| Node | Node.js | 24 LTS | For the React build |
| Containers | Docker + Docker Compose | latest | Same setup locally and on AWS |
| CI/CD | GitHub Actions | n/a | Tests from week 1; deploys from week 8 |
| Cloud | AWS: EC2, RDS, S3, CloudFront, ECR, KMS, SSM, CloudWatch, IAM, Budgets | n/a | Region ap-south-1 (Mumbai); ECS Fargate + ALB as an optional second stage |

**Gateway trap that wastes a day:** since Spring Cloud Gateway 5.0, routes live under `spring.cloud.gateway.server.webflux.routes`. Old `spring.cloud.gateway.routes` keys from tutorials stop binding silently, so the gateway starts fine and routes nothing ([Broadleaf notes](https://developer.broadleafcommerce.com/utility-services/gateways/release-notes/release-notes-3-0-0)).

**Most YouTube tutorials are Spring Boot 3.** Differences you will hit on Boot 4: Jackson 3 (`tools.jackson` packages), Spring Security 7, `@MockitoBean` instead of `@MockBean`, and some renamed starters. When a tutorial's code doesn't compile, check these first.

**Install before Week 1:** JDK 21, IntelliJ IDEA Community, VS Code, Node 24, Docker Desktop, Git + GitHub account, Postman or Bruno, DBeaver (MySQL), MongoDB Compass, AWS CLI v2.

## Week-by-week roadmap

At 4 to 5 hours a day, 6 days a week, you have a live HTTPS link by the end of week 7 and start applying in week 9; each phase below has its own "Done when" test.

&#91;embedded content: 8-week roadmap · 7 phases, 3 gates\]

Don't start a phase until the previous one passes its "Done when" check. If a week slips, cut a stretch feature, never the AWS deployment or the tests. Start applying the day the link is live if you're confident; you don't need week 8 to be perfect.

## Phase 0: Setup (Week 1, days 1 to 2)

One GitHub repo holds four Spring Boot projects, one React app and one Docker Compose file that runs MySQL and MongoDB on your laptop.

**Step 1. Create the repo** `secure-file-vault` (public, so recruiters can see it) with this layout:

```
secure-file-vault/
├── backend/
│   ├── discovery-server/   # Eureka            port 8761
│   ├── api-gateway/        # Spring Cloud GW   port 8080
│   ├── auth-service/       # users + JWT       port 8081  (MySQL)
│   └── vault-service/      # files             port 8082  (MySQL + MongoDB)
├── frontend/               # React + Vite      port 5173
├── infra/
│   ├── docker-compose.yml
│   └── mysql-init.sql
├── .github/workflows/
└── README.md
```

**Step 2. Run both databases with Docker.** `infra/docker-compose.yml`:

```yaml
services:
  mysql:
    image: mysql:8.4
    environment:
      MYSQL_ROOT_PASSWORD: root
    ports: ["3306:3306"]
    volumes:
      - mysql-data:/var/lib/mysql
      - ./mysql-init.sql:/docker-entrypoint-initdb.d/init.sql
  mongo:
    image: mongo:8.0
    environment:
      MONGO_INITDB_ROOT_USERNAME: root
      MONGO_INITDB_ROOT_PASSWORD: root
    ports: ["27017:27017"]
    volumes:
      - mongo-data:/data/db
volumes:
  mysql-data:
  mongo-data:
```

`infra/mysql-init.sql` gives each service its own database (the "database per service" rule):

```sql
CREATE DATABASE IF NOT EXISTS auth_db;
CREATE DATABASE IF NOT EXISTS vault_db;
CREATE USER IF NOT EXISTS 'vault'@'%' IDENTIFIED BY 'vault';
GRANT ALL PRIVILEGES ON auth_db.*  TO 'vault'@'%';
GRANT ALL PRIVILEGES ON vault_db.* TO 'vault'@'%';
```

Run `docker compose -f infra/docker-compose.yml up -d`, then connect with DBeaver (localhost:3306) and Compass (`mongodb://root:root@localhost:27017`).

**Step 3. Generate the four projects** on start.spring.io: Maven, Java 21, Spring Boot 4.1.x, group `com.vault`, packaging Jar.

| Project | Dependencies to tick on start.spring.io |
| --- | --- |
| discovery-server | Eureka Server |
| api-gateway | Reactive Gateway, Eureka Discovery Client, OAuth2 Resource Server, Actuator |
| auth-service | Spring Web, Spring Security, OAuth2 Resource Server, Spring Data JPA, MySQL Driver, Flyway Migration, Validation, Eureka Discovery Client, Actuator, Lombok |
| vault-service | Same as auth-service, plus Spring Data MongoDB |

After generating api-gateway, open its `pom.xml` and confirm it contains `spring-cloud-starter-gateway-server-webflux`.

**Step 4. Git habits from day one.** One branch per feature (`feature/auth-login`), small commits every day with clear messages, merge through pull requests. Recruiters do open commit history; 60 meaningful commits beat 3 giant ones.

**Step 5. Tests run on every push from day one.** Add `.github/workflows/ci.yml` now, even while the apps are empty. A green badge from week 1 and a red build the moment something breaks is how real teams work, and it costs 10 minutes:

```yaml
name: ci
on: [push, pull_request]
jobs:
  backend:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        service: [discovery-server, api-gateway, auth-service, vault-service]
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21', cache: maven }
      - run: ./mvnw -B verify
        working-directory: backend/${{ matrix.service }}
  # add in week 5, once frontend/ exists
  frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: 24, cache: npm, cache-dependency-path: frontend/package-lock.json }
      - run: npm ci && npm run build
        working-directory: frontend
```

**Done when:** both databases run in Docker, you can see them in DBeaver and Compass, all four empty Spring Boot apps start, and the first GitHub Actions run is green.

## Phase 1: Discovery server and API gateway (Week 1, days 3 to 6)

The gateway is the only door into the backend: it checks the JWT, applies CORS, and forwards each path to the right service by name through Eureka.

**1. discovery-server.** Add `@EnableEurekaServer` to the main class, then `application.yml`:

```yaml
server:
  port: 8761
spring:
  application:
    name: discovery-server
eureka:
  client:
    register-with-eureka: false
    fetch-registry: false
```

**2. api-gateway routes.** Note the `server.webflux` prefix (see the trap in the tech stack section):

```yaml
server:
  port: 8080
spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      server:
        webflux:
          routes:
            - id: auth-service
              uri: lb://auth-service
              predicates:
                - Path=/api/auth/**
            - id: vault-service
              uri: lb://vault-service
              predicates:
                - Path=/api/files/**,/api/shares/**,/api/public/**,/api/activity/**
eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_URL:http://localhost:8761/eureka}
jwt:
  secret: ${JWT_SECRET}
app:
  cors:
    allowed-origin: ${FRONTEND_URL:http://localhost:5173}
```

**3. Gateway security.** The gateway is reactive (WebFlux), so it uses the reactive security classes:

```java
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

  @Bean
  SecurityWebFilterChain filterChain(ServerHttpSecurity http) {
    return http
        .csrf(ServerHttpSecurity.CsrfSpec::disable)
        .cors(Customizer.withDefaults())
        .authorizeExchange(ex -> ex
            .pathMatchers("/api/auth/register", "/api/auth/login",
                          "/api/auth/refresh", "/api/auth/logout",
                          "/api/public/**", "/actuator/health").permitAll()
            .anyExchange().authenticated())
        .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()))
        .build();
  }

  @Bean
  ReactiveJwtDecoder jwtDecoder(@Value("${jwt.secret}") String secret) {
    SecretKey key = new SecretKeySpec(Base64.getDecoder().decode(secret), "HmacSHA256");
    return NimbusReactiveJwtDecoder.withSecretKey(key).build();
  }

  @Bean  // org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource
  CorsConfigurationSource corsConfigurationSource(
      @Value("${app.cors.allowed-origin}") String origin) {
    CorsConfiguration c = new CorsConfiguration();
    c.setAllowedOrigins(List.of(origin));
    c.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    c.setAllowedHeaders(List.of("*"));
    c.setAllowCredentials(true);  // needed for the refresh-token cookie
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", c);
    return source;
  }
}
```

**4. The JWT secret.** Generate once with `openssl rand -base64 32` and pass it as the `JWT_SECRET` environment variable to the gateway, auth-service and vault-service. Never commit it. The gateway forwards the `Authorization` header unchanged, and each service validates the token again (defence in depth: a service reached directly is still protected).

**Interview upgrade (later, optional):** switch from HS256 (one shared secret) to RS256, where only auth-service holds the private key and the others fetch the public key from a JWKS endpoint.

**If week 1 runs long, park Eureka.** Use `uri: http://localhost:8081` and `http://localhost:8082` in the routes, finish auth-service, and add Eureka back in week 6. Only the two `uri` lines change. Eureka earns its keep once a service calls another service by name (stretch item 2) or runs more than one instance.

**Done when:** the Eureka dashboard at localhost:8761 lists API-GATEWAY, and `GET localhost:8080/api/files` with no token returns 401.

## Phase 2: auth-service on MySQL (Week 2)

auth-service owns users and tokens: it hashes passwords with Argon2id, issues a 15-minute JWT access token, and keeps a 7-day refresh token in an httpOnly cookie that rotates on every use.

**1. Configuration** (`application.yml`). Boot reads `${VAR:default}` from environment variables, which is how the same jar runs locally and on AWS.

```yaml
server:
  port: 8081
spring:
  application:
    name: auth-service
  datasource:
    url: jdbc:mysql://${MYSQL_HOST:localhost}:3306/auth_db
    username: ${MYSQL_USER:vault}
    password: ${MYSQL_PASSWORD:vault}
  jpa:
    hibernate:
      ddl-auto: validate      # Flyway owns the schema, Hibernate only checks it
    open-in-view: false
eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_URL:http://localhost:8761/eureka}
jwt:
  secret: ${JWT_SECRET}
```

**2. Schema** (`src/main/resources/db/migration/V1__init.sql`):

```sql
CREATE TABLE users (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  email         VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  full_name     VARCHAR(100) NOT NULL,
  role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
  created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE refresh_tokens (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id     BIGINT    NOT NULL,
  token_hash  CHAR(64)  NOT NULL UNIQUE,   -- SHA-256 of the token, never the token
  expires_at  DATETIME(6) NOT NULL,
  revoked     BOOLEAN   NOT NULL DEFAULT FALSE,
  FOREIGN KEY (user_id) REFERENCES users(id)
);
```

**3. Endpoints**

| Method | Path | Login needed | Request | Response |
| --- | --- | --- | --- | --- |
| POST | /api/auth/register | No | email, password, fullName | 201 + id, email |
| POST | /api/auth/login | No | email, password | 200 + accessToken, expiresIn; refresh cookie set |
| POST | /api/auth/refresh | No (cookie) | none | 200 + new accessToken; cookie rotated |
| POST | /api/auth/logout | No (cookie) | none | 204; token revoked, cookie cleared |
| GET | /api/auth/me | Yes | none | id, email, fullName |

**4. Issuing tokens with Spring Security's own JWT support** (no extra JWT library):

```java
@Service
public class TokenService {
  private final JwtEncoder encoder;

  public TokenService(@Value("${jwt.secret}") String secret) {
    SecretKey key = new SecretKeySpec(Base64.getDecoder().decode(secret), "HmacSHA256");
    this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
  }

  public String createAccessToken(User user) {
    Instant now = Instant.now();
    JwtClaimsSet claims = JwtClaimsSet.builder()
        .issuer("file-vault-auth")
        .subject(String.valueOf(user.getId()))   // services read the user id from here
        .issuedAt(now)
        .expiresAt(now.plus(15, ChronoUnit.MINUTES))
        .claim("email", user.getEmail())
        .claim("roles", List.of(user.getRole()))
        .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }
}
```

**5. Security config** (servlet version; vault-service uses the same pattern):

```java
@Bean
SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
  return http
      .csrf(csrf -> csrf.disable())   // token in header; refresh cookie is SameSite=Strict
      .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .authorizeHttpRequests(a -> a
          .requestMatchers("/api/auth/register", "/api/auth/login",
                           "/api/auth/refresh", "/api/auth/logout",
                           "/actuator/health").permitAll()
          .anyRequest().authenticated())
      .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()))
      .build();
}

// Argon2id at OWASP's minimum: 19 MiB memory, 2 iterations, parallelism 1.
// Needs org.bouncycastle:bcprov-jdk18on. Args: saltLength, hashLength, parallelism, memoryKiB, iterations
@Bean PasswordEncoder passwordEncoder() { return new Argon2PasswordEncoder(16, 32, 1, 19456, 2); }

@Bean
JwtDecoder jwtDecoder(@Value("${jwt.secret}") String secret) {
  SecretKey key = new SecretKeySpec(Base64.getDecoder().decode(secret), "HmacSHA256");
  return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
}
```

**6. Login sets the refresh cookie:**

```java
ResponseCookie cookie = ResponseCookie.from("refresh_token", result.refreshToken())
    .httpOnly(true).secure(true).sameSite("Strict")
    .path("/api/auth").maxAge(Duration.ofDays(7)).build();
return ResponseEntity.ok()
    .header(HttpHeaders.SET_COOKIE, cookie.toString())
    .body(new TokenResponse(result.accessToken(), 900));
```

**7. Rules that impress interviewers**

- Validate input with records + Bean Validation: `@Email`, password `@Size(min = 8, max = 128)`. The upper cap stops attackers sending megabyte-long passwords to burn CPU.
- Hash with Argon2id, OWASP's first choice; bcrypt is now listed only for legacy systems. Be ready to say why: Argon2id is memory-hard, which makes GPU cracking expensive.
- Wrong email and wrong password return the same message, "Invalid email or password", so attackers can't discover which emails exist.
- Log every failed login with email, IP and time, never the password. This is the data rate limiting and account lockout will use later.
- Refresh tokens are random 32 bytes, stored only as SHA-256 hashes, rotated on every refresh. If a revoked token is ever reused, revoke all of that user's tokens (stolen-token detection).
- Never return `passwordHash` in any response DTO.

**Done when:** in Postman, through the gateway on port 8080, you can register, log in, call `/api/auth/me` with `Authorization: Bearer <token>`, refresh, and log out; an expired or edited token gets 401.

## Phase 3: vault-service, the heart of the project (Weeks 3 and 4)

vault-service encrypts every upload with its own random AES-256 key, stores the encrypted bytes in MongoDB GridFS, and stores everything needed to find and decrypt it (owner, name, wrapped key, IVs, hash) in MySQL. This is the part interviewers will dig into, so understand every line.

**Build order inside these two weeks.** Week 3: upload, list, download and delete with plain bytes and the PENDING → ACTIVE flow, so the two-database logic works before any crypto. Week 4: switch on encryption, real-type checks, share links and the audit log. The schema below is the end state: create it in week 3 as Flyway `V1` without the key columns and add them in `V2`, which is also how real schemas evolve.

**1. Configuration.** On Spring Boot 4 the Mongo connection property is `spring.mongodb.uri`; the old `spring.data.mongodb.uri` from tutorials was renamed ([Boot 4.0 migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)).

```yaml
server:
  port: 8082
spring:
  application:
    name: vault-service
  datasource:
    url: jdbc:mysql://${MYSQL_HOST:localhost}:3306/vault_db
    username: ${MYSQL_USER:vault}
    password: ${MYSQL_PASSWORD:vault}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  mongodb:
    uri: ${MONGO_URI:mongodb://root:root@localhost:27017/vault_files?authSource=admin}
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 11MB
vault:
  master-key: ${VAULT_MASTER_KEY}     # openssl rand -base64 32
  max-user-storage-mb: 50
jwt:
  secret: ${JWT_SECRET}
```

**2. MySQL schema** (`V1__init.sql`). Binary columns are `VARBINARY` and times are `DATETIME(6)` so Hibernate's `validate` check passes.

```sql
CREATE TABLE files (
  id            CHAR(36)       PRIMARY KEY,          -- UUID; also the GridFS file name
  owner_id      BIGINT         NOT NULL,             -- from the JWT, never from the request
  original_name VARCHAR(255)   NOT NULL,             -- display only
  content_type  VARCHAR(100)   NOT NULL,             -- detected by Tika, not the browser's claim
  size_bytes    BIGINT         NOT NULL,
  sha256        CHAR(64)       NOT NULL,             -- hash of the original file
  gridfs_id     VARCHAR(24)    NULL,                 -- set once the bytes are stored
  wrapped_dek   VARBINARY(512) NOT NULL,             -- file key, encrypted; KMS output is longer than 48 bytes
  dek_iv        VARBINARY(12)  NULL,                 -- only for LOCAL wrapping
  file_iv       VARBINARY(12)  NOT NULL,
  key_provider  VARCHAR(10)    NOT NULL,             -- LOCAL or KMS
  key_version   INT            NOT NULL DEFAULT 1,   -- which master key wrapped it
  status        VARCHAR(20)    NOT NULL,             -- PENDING, ACTIVE, FAILED, DELETED, PURGED
  created_at    DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at    DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at    DATETIME(6)    NULL,
  INDEX idx_owner_status_created (owner_id, status, created_at),   -- the file list
  INDEX idx_owner_name           (owner_id, original_name),        -- search by name
  INDEX idx_status_updated       (status, updated_at)              -- the cleanup job
);

CREATE TABLE share_links (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  file_id        CHAR(36)    NOT NULL,
  token_hash     CHAR(64)    NOT NULL UNIQUE,
  expires_at     DATETIME(6) NOT NULL,
  max_downloads  INT         NULL,
  download_count INT         NOT NULL DEFAULT 0,
  revoked        BOOLEAN     NOT NULL DEFAULT FALSE,
  created_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  FOREIGN KEY (file_id) REFERENCES files(id)
);

CREATE TABLE audit_log (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id    BIGINT       NULL,
  action     VARCHAR(30)  NOT NULL,      -- UPLOAD, DOWNLOAD, DELETE, SHARE, SHARE_USED
  file_id    CHAR(36)     NULL,
  details    VARCHAR(500) NULL,
  created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  prev_hash  CHAR(64)     NULL,          -- filled in by the hash chain (later)
  entry_hash CHAR(64)     NULL
);
```

**3. Endpoints**

| Method | Path | Login needed | What it does |
| --- | --- | --- | --- |
| POST | /api/files | Yes | Multipart field `file`; encrypts and stores; 201 + metadata |
| GET | /api/files?page=0&size=20&q=report | Yes | Your files, paginated, optional name search |
| GET | /api/files/{id} | Yes | One file's metadata |
| GET | /api/files/{id}/download | Yes | Decrypted file as an attachment |
| DELETE | /api/files/{id} | Yes | Soft-delete metadata, remove encrypted bytes; 204 |
| POST | /api/files/{id}/shares | Yes | Body: expiresInHours, maxDownloads; returns share URL |
| DELETE | /api/shares/{id} | Yes | Revoke a share link |
| GET | /api/public/shares/{token} | No | Download through a valid share link |
| GET | /api/activity | Yes | Your audit log, newest first |

Rules: the owner always comes from the JWT (`jwt.getSubject()`), never from a request field. If a user asks for someone else's file, return **404, not 403**, so they can't even learn it exists. In vault-service's security config, `permitAll()` only `/api/public/**` and `/actuator/health`.

**4. Encryption code: one cipher plus a pluggable key wrapper**

`FileCipher` only does AES-256-GCM. `KeyWrapper` decides where file keys come from and how they're protected: `LocalKeyWrapper` (an AES master key from `.env`) on your laptop, `KmsKeyWrapper` (AWS KMS) when the `aws` Spring profile is active (code in Phase 6). Same interface, chosen by profile: a clean, explainable use of the strategy pattern.

```java
@Component
public class FileCipher {
  private static final int IV_BYTES = 12, TAG_BITS = 128;
  private final SecureRandom random = new SecureRandom();

  public byte[] newIv() {
    byte[] iv = new byte[IV_BYTES];
    random.nextBytes(iv);
    return iv;
  }

  public byte[] encrypt(SecretKey key, byte[] iv, byte[] plain, byte[] aad) throws GeneralSecurityException {
    Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
    c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
    c.updateAAD(aad);              // binds the ciphertext to this file id
    return c.doFinal(plain);       // ciphertext + 16-byte auth tag
  }

  public byte[] decrypt(SecretKey key, byte[] iv, byte[] cipherText, byte[] aad) throws GeneralSecurityException {
    Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
    c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
    c.updateAAD(aad);
    return c.doFinal(cipherText);  // throws AEADBadTagException if anything was altered
  }
}

public record DataKey(SecretKey key, byte[] wrappedKey, byte[] wrapIv, String provider) {}

public interface KeyWrapper {
  DataKey newDataKey(String fileId) throws GeneralSecurityException;
  SecretKey unwrap(byte[] wrappedKey, byte[] wrapIv, String fileId) throws GeneralSecurityException;
}

@Component
@Profile("!aws")                     // laptop and Docker Compose
public class LocalKeyWrapper implements KeyWrapper {
  private final SecretKey masterKey;
  private final FileCipher cipher;

  public LocalKeyWrapper(@Value("${vault.master-key}") String base64Key, FileCipher cipher) {
    byte[] k = Base64.getDecoder().decode(base64Key);
    if (k.length != 32) throw new IllegalStateException("Master key must be 256 bits");
    this.masterKey = new SecretKeySpec(k, "AES");
    this.cipher = cipher;
  }

  @Override
  public DataKey newDataKey(String fileId) throws GeneralSecurityException {
    KeyGenerator kg = KeyGenerator.getInstance("AES");
    kg.init(256);
    SecretKey dek = kg.generateKey();
    byte[] iv = cipher.newIv();
    byte[] wrapped = cipher.encrypt(masterKey, iv, dek.getEncoded(), aad(fileId));
    return new DataKey(dek, wrapped, iv, "LOCAL");
  }

  @Override
  public SecretKey unwrap(byte[] wrapped, byte[] iv, String fileId) throws GeneralSecurityException {
    return new SecretKeySpec(cipher.decrypt(masterKey, iv, wrapped, aad(fileId)), "AES");
  }

  private static byte[] aad(String fileId) { return fileId.getBytes(StandardCharsets.UTF_8); }
}
```

**5. Upload across two databases: PENDING first, ACTIVE last**

MongoDB and MySQL can't share one transaction, so MySQL is made the source of truth and every upload moves through states: write a `PENDING` row, store the encrypted bytes in GridFS named by the file id, then mark the row `ACTIVE`. If anything fails, the row becomes `FAILED` and a cleanup job deletes leftover bytes by name. A file is never `ACTIVE` without its bytes, and any GridFS object without an `ACTIVE` row is garbage that's easy to find.

```java
@Service
@RequiredArgsConstructor
public class FileService {
  private final FileCipher cipher;
  private final KeyWrapper keys;                // LocalKeyWrapper or KmsKeyWrapper
  private final GridFsTemplate gridFs;
  private final UploadValidator validator;
  private final FileMetadataService metadata;   // @Transactional MySQL methods + audit

  public FileResponse upload(long ownerId, MultipartFile file) throws Exception {
    byte[] plain = file.getBytes();             // OK because of the 10 MB cap
    String type = validator.check(ownerId, file, plain);   // size, extension, real type, quota
    String fileId = UUID.randomUUID().toString();
    byte[] aad = fileId.getBytes(StandardCharsets.UTF_8);
    String sha256 = HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(plain));

    DataKey key = keys.newDataKey(fileId);
    byte[] fileIv = cipher.newIv();
    byte[] cipherText = cipher.encrypt(key.key(), fileIv, plain, aad);

    metadata.createPending(fileId, ownerId, safeName(file.getOriginalFilename()),
        type, plain.length, sha256, key, fileIv);                      // 1. MySQL: PENDING
    try {
      ObjectId gridId = gridFs.store(new ByteArrayInputStream(cipherText),
          fileId, "application/octet-stream");                         // 2. MongoDB, named by file id
      return FileResponse.from(metadata.activate(fileId, gridId.toHexString())); // 3. MySQL: ACTIVE
    } catch (RuntimeException e) {
      metadata.markFailed(fileId);              // the cleanup job removes any leftover bytes
      throw e;
    }
  }

  public Download download(long userId, String fileId) throws Exception {
    VaultFile f = metadata.findActiveOwned(fileId, userId)       // owner check inside the query
        .orElseThrow(() -> new ResourceNotFoundException("File not found"));
    GridFSFile g = gridFs.findOne(
        Query.query(Criteria.where("_id").is(new ObjectId(f.getGridfsId()))));
    byte[] cipherText = gridFs.getResource(g).getInputStream().readAllBytes();
    SecretKey dek = keys.unwrap(f.getWrappedDek(), f.getDekIv(), fileId);
    byte[] plain = cipher.decrypt(dek, f.getFileIv(), cipherText,
        fileId.getBytes(StandardCharsets.UTF_8));
    metadata.audit(userId, "DOWNLOAD", fileId);
    return new Download(f.getOriginalName(), f.getContentType(), plain);
  }
}
```

If `markFailed` itself fails (say MySQL is down), the row simply stays `PENDING`, and the cleanup job treats a `PENDING` row older than 15 minutes exactly like `FAILED`. Every path ends clean.

&#91;embedded content: file lifecycle · 5 states, 1 cleanup path\]

Only `ACTIVE` files appear in lists or downloads; every other state is work for the cleanup job. Draw this on the whiteboard when asked "what if the second database write fails?"

**6. Controller: reading the user id from the token**

```java
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {
  private final FileService files;

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public FileResponse upload(@AuthenticationPrincipal Jwt jwt,
                             @RequestParam("file") MultipartFile file) throws Exception {
    return files.upload(userId(jwt), file);
  }

  @GetMapping("/{id}/download")
  public ResponseEntity<byte[]> download(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable String id) throws Exception {
    Download d = files.download(userId(jwt), id);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(d.contentType()))
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
            .filename(d.name(), StandardCharsets.UTF_8).build().toString())
        .body(d.bytes());
  }

  private long userId(Jwt jwt) { return Long.parseLong(jwt.getSubject()); }
}
```

**7. Share links that can't be over-used, even under concurrent clicks**

Generate 32 random bytes, Base64-URL encode them as the token, store only its SHA-256 hash, and return `https://<your-site>/s/<token>`. On each public download, consume one use with a single atomic SQL update:

```java
@Modifying
@Query("""
    UPDATE ShareLink s SET s.downloadCount = s.downloadCount + 1
    WHERE s.id = :id AND s.revoked = false AND s.expiresAt > CURRENT_TIMESTAMP
      AND (s.maxDownloads IS NULL OR s.downloadCount < s.maxDownloads)
    """)
int tryConsume(@Param("id") Long id);   // 0 rows updated -> 410 Gone
```

**8. Delete and the clean-up job**

- Delete: set the row to `DELETED` (a soft delete, so the audit trail still makes sense), delete the GridFS object named by the file id, then set `PURGED`.
- Add `@EnableScheduling` and a job that runs every 15 minutes. For rows that are `FAILED`, `DELETED`, or `PENDING` for over 15 minutes, it deletes the GridFS object by name (deleting something already gone is harmless), removes the `PENDING`/`FAILED` rows, and marks `DELETED` rows `PURGED`.
- Every step is safe to repeat (idempotent). That property, not luck, is what keeps two databases consistent.

**9. Validation checklist**

- Size: 10 MB per file (`spring.servlet.multipart`), 50 MB quota per user (the free Atlas cluster holds 512 MB in total).
- Extension allowlist: `.pdf`, `.png`, `.jpg`/`.jpeg`, `.txt`, `.docx`; anything else gets 415.
- Real type from the bytes: `new Tika().detect(bytes, filename)` must be a type allowed for that extension, so a renamed `.exe` is rejected. Map each extension to the types Tika reports for it and write one test per type (DOCX is a ZIP container, so check what Tika returns for your samples).
- Store under the generated file id, never the user's file name. Keep the original name only for display: last path segment only, control characters removed, max 255 characters.
- Downloads are sent as `Content-Disposition: attachment`, and Spring Security already adds `X-Content-Type-Options: nosniff`, so a browser never runs an uploaded file as a page.
- These rules follow OWASP's file upload guidance ([cheat sheet](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html)).

**Why GCM (say this in interviews):** GCM gives confidentiality and integrity together through its 16-byte authentication tag. ECB leaks patterns; CBC needs a separate MAC. A fresh random 12-byte IV is used for every encryption, and an IV is never reused with the same key.

**Why envelope encryption:** each file has its own key (DEK), and only that small key is protected by the master key. Rotating the master key means re-wrapping small keys, not re-encrypting every file, and `key_version` records which master key was used. Locally the master key is an environment variable; on AWS it lives in KMS and never leaves it.

**Done when:** you upload a PDF in Postman, see unreadable bytes in Compass and an `ACTIVE` row in DBeaver, and download it back byte-for-byte identical; a renamed `.exe` gets 415; stopping MongoDB mid-upload leaves a `FAILED` row that the cleanup job clears; and a share link stops working after its expiry or download limit.

## Phase 4: React frontend (Week 5)

The frontend is five pages talking only to the gateway through `/api`; the access token lives in memory, the refresh token in the httpOnly cookie, and one Axios interceptor silently refreshes expired tokens.

**1. Create the app**

```bash
npm create vite@latest frontend -- --template react-ts
cd frontend
npm install axios react-router @tanstack/react-query react-dropzone
npm install -D tailwindcss @tailwindcss/vite
```

Add the Tailwind plugin and a dev proxy in `vite.config.ts`. The proxy makes the browser see one origin, exactly like production behind CloudFront, so cookies and CORS behave the same in both places:

```ts
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: { proxy: { "/api": "http://localhost:8080" } },
});
```

Put `@import "tailwindcss";` at the top of `src/index.css`.

**2. Folder layout**

```
src/
├── api/        client.ts (Axios + interceptors), auth.ts, files.ts
├── auth/       AuthContext.tsx, ProtectedRoute.tsx
├── components/ UploadDropzone, FileTable, ShareDialog, Toast
├── pages/      Login, Register, Dashboard, Activity, PublicShare
├── App.tsx     routes
└── main.tsx    QueryClientProvider + Router
```

**3. Pages**

| Route | Page | What it does |
| --- | --- | --- |
| /login | Login | Email + password; stores access token in memory |
| /register | Register | Signup form with client-side validation |
| / | Dashboard | Drag-drop upload with progress, file table with search, paging, download, delete, share |
| /activity | Activity | Your audit log, newest first |
| /s/:token | PublicShare | Anyone with the link can download; shows a clear message when expired |

**4. Axios client with automatic token refresh**

```ts
import axios from "axios";

let accessToken: string | null = null;
export const setAccessToken = (t: string | null) => { accessToken = t; };

export const api = axios.create({ baseURL: "/api", withCredentials: true });

api.interceptors.request.use(cfg => {
  if (accessToken) cfg.headers.Authorization = `Bearer ${accessToken}`;
  return cfg;
});

let refreshing: Promise<string> | null = null;   // one refresh, even if 5 calls fail at once

api.interceptors.response.use(
  res => res,
  async error => {
    const original = error.config;
    const isAuthCall = original?.url?.startsWith("/auth/");
    if (error.response?.status === 401 && !original._retry && !isAuthCall) {
      original._retry = true;
      refreshing ??= api.post("/auth/refresh")
        .then(r => r.data.accessToken)
        .finally(() => { refreshing = null; });
      try {
        setAccessToken(await refreshing);
        return api(original);              // replay the failed request
      } catch {
        setAccessToken(null);
        window.location.href = "/login";
      }
    }
    return Promise.reject(error);
  }
);
```

On app start, call `/auth/refresh` once: if the cookie is valid the user stays logged in after a page reload.

**5. Upload with a progress bar, and download**

```ts
export function uploadFile(file: File, onProgress: (pct: number) => void) {
  const form = new FormData();
  form.append("file", file);               // don't set Content-Type; Axios adds the boundary
  return api.post("/files", form, {
    onUploadProgress: e => onProgress(Math.round((e.loaded * 100) / (e.total ?? 1))),
  });
}

export async function downloadFile(id: string, name: string) {
  // a plain <a href> can't send the Authorization header, so fetch a blob
  const res = await api.get(`/files/${id}/download`, { responseType: "blob" });
  const url = URL.createObjectURL(res.data);
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  a.click();
  URL.revokeObjectURL(url);
}
```

**6. Small things that make it feel professional**

- Use TanStack Query for the file list and invalidate it after upload, delete or share, so the table refreshes itself.
- Reject files over 10 MB in the browser before uploading, and show the server's error message on failure.
- Human-readable sizes ("2.4 MB"), relative dates ("3 hours ago"), empty states ("No files yet, drop one here"), disabled buttons while busy.
- Make it work on a phone screen; recruiters often open links on mobile.

**Done when:** in the browser you can register, upload with progress, see the file in the list, download it, share it, open the share link in an incognito window, and stay logged in after refreshing the page.

## Phase 5: Production quality and Docker (Week 6)

This week turns a working demo into code a senior engineer would approve: consistent errors, live API docs, real tests, and every service in a container.

**1. One error format everywhere** (`ProblemDetail`, RFC 9457):

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ResourceNotFoundException.class)
  ProblemDetail notFound(ResourceNotFoundException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ProblemDetail tooLarge() {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, "Max file size is 10 MB");
  }

  @ExceptionHandler(AEADBadTagException.class)
  ProblemDetail tampered(AEADBadTagException e) {
    log.error("Integrity check failed", e);   // should never happen: alert-worthy
    return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "File integrity check failed");
  }

  @ExceptionHandler(Exception.class)
  ProblemDetail fallback(Exception e) {
    log.error("Unhandled error", e);           // full detail in logs, never to the client
    return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong");
  }
}
```

Also handle `MethodArgumentNotValidException` (400 with each field's message), `BadCredentialsException` (401) and your own `UnsupportedFileTypeException` (415).

**2. Swagger UI.** Add `springdoc-openapi-starter-webmvc-ui` version 3.x (the line built for Spring Boot 4) to auth-service and vault-service, declare a bearer-token security scheme, and permit `/v3/api-docs/**` and `/swagger-ui/**` in each security config. Put the Swagger link in your README.

**3. Tests that prove the security claims** (aim for 25 to 40 meaningful tests, not a coverage number):

| Test | Type | Proves |
| --- | --- | --- |
| Encrypt then decrypt returns the original bytes | Unit | Crypto works |
| Flipping one ciphertext bit throws `AEADBadTagException` | Unit | Tampering is detected |
| Decrypting with a different file id (AAD) fails | Unit | Ciphertexts can't be swapped between records |
| LocalKeyWrapper wrap then unwrap returns the same key | Unit | Key wrapping works |
| Upload then download gives identical bytes and SHA-256 | Integration | End-to-end flow across both databases |
| GridFS store throws: row ends `FAILED`, no `ACTIVE` row | Integration | The PENDING → ACTIVE flow |
| Cleanup job removes the GridFS object of a `FAILED` row | Integration | No orphaned bytes |
| A `.exe` renamed to `.pdf` gets 415 | Integration | Real-type checking |
| User B gets 404 for user A's file | Integration | Ownership checks |
| Share link with maxDownloads=1 works once, then 410 | Integration | Atomic consume |
| Login with wrong password returns the generic message | Controller | No user enumeration |
| Request with no or expired JWT returns 401 | Controller | Resource server config |

```java
@Test
void tamperedCiphertextIsRejected() throws Exception {
  CryptoService crypto = new CryptoService(Base64.getEncoder().encodeToString(new byte[32]));
  SecretKey dek = crypto.newDataKey();
  byte[] iv = crypto.newIv(), aad = "file-1".getBytes(StandardCharsets.UTF_8);
  byte[] ct = crypto.encrypt(dek, iv, "top secret".getBytes(StandardCharsets.UTF_8), aad);
  ct[0] ^= 1;                                   // flip one bit
  assertThrows(AEADBadTagException.class, () -> crypto.decrypt(dek, iv, ct, aad));
}
```

For integration tests, use Testcontainers' MySQL and MongoDB containers with `@ServiceConnection`, so Spring wires the connection automatically and tests run against real databases. For controller tests, `spring-security-test` gives you a `jwt()` request post-processor so you don't need real tokens. Package names differ between Testcontainers 1.x and 2.x; let IntelliJ resolve the imports.

**4. Logs and health.** Expose only `/actuator/health` publicly (AWS uses it). Log one line per upload/download with user id, file id and duration, never file contents, keys or tokens.

**5. A Dockerfile per service** (multi-stage, so the final image has no build tools):

```dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

**6. Whole stack in Docker Compose.** Extend `infra/docker-compose.yml` with the four services; secrets come from a git-ignored `.env` file:

```yaml
  discovery-server:
    build: ../backend/discovery-server
    ports: ["8761:8761"]
    mem_limit: 384m
  api-gateway:
    build: ../backend/api-gateway
    ports: ["8080:8080"]
    mem_limit: 384m
    environment:
      EUREKA_URL: http://discovery-server:8761/eureka
      JWT_SECRET: ${JWT_SECRET}
    depends_on: [discovery-server]
  auth-service:
    build: ../backend/auth-service
    mem_limit: 384m
    environment:
      EUREKA_URL: http://discovery-server:8761/eureka
      MYSQL_HOST: mysql
      JWT_SECRET: ${JWT_SECRET}
    depends_on: [mysql, discovery-server]
  vault-service:
    build: ../backend/vault-service
    mem_limit: 512m
    environment:
      EUREKA_URL: http://discovery-server:8761/eureka
      MYSQL_HOST: mysql
      MONGO_URI: mongodb://root:root@mongo:27017/vault_files?authSource=admin
      JWT_SECRET: ${JWT_SECRET}
      VAULT_MASTER_KEY: ${VAULT_MASTER_KEY}
    depends_on: [mysql, mongo, discovery-server]
```

Set `eureka.instance.prefer-ip-address: true` in each client service so containers register with reachable addresses. `depends_on` does not wait for MySQL to be ready; add a MySQL healthcheck with `condition: service_healthy`, or let Spring retry on startup.

**Done when:** `docker compose up --build` starts the whole backend from scratch, `mvn verify` passes in every service, and Swagger UI shows all endpoints.

## Phase 6: Deploy on AWS (Week 7)

Deploy to one EC2 instance running your four containers, with RDS for MySQL, KMS guarding the master key, the free MongoDB Atlas cluster for files, and S3 + CloudFront serving React and proxying `/api` on a single HTTPS domain. It's cheap, covers the AWS services interviewers ask about, and takes about 5 days the first time.

**Cost safety first (do this before launching anything)**

- Sign up on the **Free plan**. New accounts get $100 in credits at sign-up and can earn up to $100 more by trying services like EC2 ([AWS announcement](https://aws.amazon.com/about-aws/whats-new/2025/07/aws-free-tier-credits-month-free-plan/)).
- The Free plan ends after 6 months or when credits run out, whichever comes first, and the account then closes unless you upgrade; credits themselves expire 12 months after sign-up ([AWS Free Tier FAQs](https://aws.amazon.com/free/faqs/)). Record your demo video well before month 6.
- Turn on MFA for the root user, create an admin user for daily work, and never use root again.
- Create an AWS Budget of $5/month with email alerts at 50%, 80% and 100%.
- Work in region **ap-south-1 (Mumbai)**. Stop EC2 and RDS when you're not demoing. A stopped RDS instance restarts by itself after 7 days, so check it weekly.

**What runs where**

| AWS service | Used for | Setting |
| --- | --- | --- |
| EC2 | Gateway, Eureka, auth-service, vault-service in Docker Compose | Amazon Linux 2023, t3.small; move to m7i-flex.large if memory is tight (both are Free-plan eligible types ([EC2 docs](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-free-tier-usage.html))) |
| ECR | Private Docker images | One repository per service |
| RDS | MySQL 8.4 (auth\_db, vault\_db) | db.t4g.micro, single-AZ, 20 GB, not publicly accessible |
| KMS | Master key that wraps every file key | One customer managed key, alias `alias/file-vault`; small monthly fee, check KMS pricing |
| MongoDB Atlas (not AWS) | GridFS encrypted files | Free cluster on AWS Mumbai: 512 MB storage ([Studio 3T](https://studio3t.com/?p=27575)) |
| S3 | Built React files | Private bucket, read only via CloudFront |
| CloudFront | HTTPS, React from S3, `/api/*` forwarded to EC2 | Free `*.cloudfront.net` domain and certificate |
| SSM Parameter Store | JWT\_SECRET, DB passwords, Atlas connection string | SecureString (Secrets Manager also works and adds rotation, for a fee) |
| IAM role on EC2 | Pull from ECR, read SSM, write logs, use the KMS key | No access keys stored on the server |
| CloudWatch Logs | Container logs | `awslogs` Docker log driver |

**Step by step**

1. **Push images to ECR.** Create four repositories, log in with `aws ecr get-login-password | docker login`, then tag and push each image. On an Apple Silicon Mac, build with `docker buildx build --platform linux/amd64` because t3/m7i instances are x86.
2. **Create RDS MySQL.** Same VPC as EC2; its security group allows port 3306 only from the EC2 security group. From the EC2 box, run the same `mysql-init.sql` once; Flyway creates the tables when the services start.
3. **Create the Atlas free cluster** in AWS Mumbai, add a database user, and allow only your EC2 Elastic IP in the Atlas IP access list. Store the `mongodb+srv://` connection string in SSM.
4. **Create the KMS key** (`alias/file-vault`). The EC2 role may call only `kms:GenerateDataKey` and `kms:Decrypt` on this key. Add the AWS SDK v2 `kms` dependency to vault-service and the `KmsKeyWrapper` below. Never schedule this key for deletion: without it, every stored file is unreadable.
5. **Launch EC2** with the IAM role, 20 GB gp3, and an Elastic IP. Security group: SSH from your IP only (or use SSM Session Manager and open no SSH at all); port 8080 only from the CloudFront origin-facing managed prefix list, so nobody can bypass CloudFront. Install Docker and the Compose plugin.
6. **Start the backend.** A `docker-compose.prod.yml` uses the ECR images and an `.env` written by a small script (`aws ssm get-parameters --with-decryption`, file mode 600). For vault-service set `SPRING_PROFILES_ACTIVE=aws`, `AWS_REGION=ap-south-1` and `VAULT_KMS_KEY_ID=alias/file-vault`. Run `docker compose up -d` and check `curl localhost:8080/actuator/health`.
7. **Upload the frontend.** `npm run build`, then `aws s3 sync dist/ s3://<bucket> --delete`.
8. **Create the CloudFront distribution.**
   - Default behaviour → the S3 origin with Origin Access Control.
   - Behaviour `/api/*` → the EC2 public DNS name, port 8080, HTTP, caching disabled, all methods allowed, forward cookies, query strings and headers (except Host).
   - React routing: add a CloudFront Function on the default behaviour that rewrites paths without a file extension to `/index.html`. Don't use distribution-wide custom error pages, because they would also replace your API's 404 responses.
9. **Point the app at its domain.** Set `FRONTEND_URL` to the CloudFront URL (used in share links and CORS) and restart the gateway and vault-service.
10. **Smoke test** the whole flow in the browser, including a share link in incognito, and watch the logs in CloudWatch.

**The KMS key wrapper.** KMS returns a fresh data key in two forms: plaintext (used once, then discarded) and encrypted (stored in `wrapped_dek`). The encryption context does for KMS what AAD does for GCM: the wrapped key only unwraps for the same file id, and every unwrap appears in CloudTrail.

```java
@Component
@Profile("aws")
public class KmsKeyWrapper implements KeyWrapper {
  private final KmsClient kms = KmsClient.create();   // credentials come from the EC2 IAM role
  private final String keyId;

  public KmsKeyWrapper(@Value("${vault.kms-key-id}") String keyId) { this.keyId = keyId; }

  @Override
  public DataKey newDataKey(String fileId) {
    GenerateDataKeyResponse r = kms.generateDataKey(b -> b
        .keyId(keyId)
        .keySpec(DataKeySpec.AES_256)
        .encryptionContext(Map.of("fileId", fileId)));
    return new DataKey(new SecretKeySpec(r.plaintext().asByteArray(), "AES"),
        r.ciphertextBlob().asByteArray(), null, "KMS");
  }

  @Override
  public SecretKey unwrap(byte[] wrapped, byte[] iv, String fileId) {
    DecryptResponse r = kms.decrypt(b -> b
        .keyId(keyId)
        .ciphertextBlob(SdkBytes.fromByteArray(wrapped))
        .encryptionContext(Map.of("fileId", fileId)));
    return new SecretKeySpec(r.plaintext().asByteArray(), "AES");
  }
}
```

Add `vault.kms-key-id: ${VAULT_KMS_KEY_ID:}` to vault-service's `application.yml`. The AWS database starts empty, so every file there is wrapped by KMS from the first upload; `key_provider` records which wrapper each row needs.

**Why one domain matters:** the browser sees the React app and the API on the same origin, so the SameSite=Strict refresh cookie works and CORS is never involved, exactly as with the Vite proxy locally.

**Done when:** a public `https://xxxx.cloudfront.net` link works end to end, new uploads show `key_provider = KMS`, the EC2 port 8080 is unreachable directly from your laptop, and your Budget alert is active.

**Stage 2 (optional, 3 to 4 days after week 8): ECS Fargate behind a load balancer**

"Deployed on ECS Fargate behind an ALB" is a stronger resume line than Docker Compose on EC2, and comparing the two bills gives you a cost-awareness story. It isn't the first deployment because a load balancer and Fargate tasks bill every hour against your credits, so learn on EC2, upgrade for a week, capture the evidence, then scale down.

1. ECS and Elastic Load Balancing are available on the Free plan ([AWS docs](https://docs.aws.amazon.com/accounts/latest/reference/supported-services-sign-up-new.html)), paid from credits. Price your setup in the AWS Pricing Calculator first.
2. Create an ECS cluster and one task definition per service from the same ECR images. Secrets come from SSM through the task definition's `secrets` field, logs go to CloudWatch, and the task role gets the same KMS permissions.
3. Run tasks in public subnets with a public IP, locked down by security groups. Private subnets would need a NAT gateway, which bills hourly and would quickly eat your credits.
4. Put an Application Load Balancer in front of the gateway service only, health check `/actuator/health`, and point CloudFront's `/api/*` behaviour at the ALB instead of EC2.
5. Discovery: Eureka keeps working if services register by IP (`prefer-ip-address: true`). Replacing it with ECS Service Connect is a good trade-off to discuss.
6. The Atlas catch: the free cluster supports no private networking, and Fargate task IPs change on every deploy, so you would have to allow `0.0.0.0/0` in Atlas, protected only by credentials and TLS. Say this openly; the production fix is a paid Atlas tier with private endpoints, or moving file bytes to S3.
7. Commit the task definitions to the repo, screenshot the running service, then scale the services to 0 and delete the load balancer.

## Phase 7: Automatic deployment with GitHub Actions (Week 8, days 1 to 3)

Tests have guarded every push since week 1; this phase adds deployment, so every merge to `main` builds images, pushes them to ECR and redeploys EC2 and the frontend, with no AWS keys stored in GitHub.

**1. Let GitHub log in to AWS without keys.** In IAM, add GitHub as an OIDC identity provider and create a deploy role that only your repo's `main` branch can assume, with permissions for ECR push, `ssm:SendCommand` on your instance, S3 sync to the frontend bucket, and CloudFront invalidation. Save its ARN and your EC2 instance id as GitHub repository secrets.

**2. Backend workflow, replacing the week-1 backend job with the same tests plus a deploy job** (`.github/workflows/backend.yml`):

```yaml
name: backend
on:
  pull_request:
  push:
    branches: [main]
permissions:
  id-token: write      # for the AWS OIDC login
  contents: read
jobs:
  test:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        service: [discovery-server, api-gateway, auth-service, vault-service]
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21', cache: maven }
      - run: ./mvnw -B verify          # Testcontainers works on GitHub runners
        working-directory: backend/${{ matrix.service }}

  deploy:
    if: github.ref == 'refs/heads/main'
    needs: test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: ${{ secrets.AWS_DEPLOY_ROLE_ARN }}
          aws-region: ap-south-1
      - id: ecr
        uses: aws-actions/amazon-ecr-login@v2
      - name: Build and push images
        run: |
          for s in discovery-server api-gateway auth-service vault-service; do
            docker build -t ${{ steps.ecr.outputs.registry }}/$s:${{ github.sha }} backend/$s
            docker push ${{ steps.ecr.outputs.registry }}/$s:${{ github.sha }}
          done
      - name: Redeploy on EC2 through SSM (no SSH key needed)
        run: |
          aws ssm send-command \
            --instance-ids ${{ secrets.EC2_INSTANCE_ID }} \
            --document-name AWS-RunShellScript \
            --parameters "commands=[\"cd /opt/vault && TAG=${{ github.sha }} ./deploy.sh\"]"
```

Use the newest major version of each action when you set this up.

**3. `deploy.sh` on the server** logs in to ECR, then runs `docker compose -f docker-compose.prod.yml pull` and `up -d`. The prod compose file references images as `<registry>/auth-service:${TAG}`, so every deploy is tied to one commit and rolling back means redeploying an older SHA.

**4. Frontend workflow** (runs when `frontend/**` changes on `main`): `npm ci`, `npm run build`, `aws s3 sync dist/ s3://<bucket> --delete`, then `aws cloudfront create-invalidation --paths "/index.html"`. Vite puts a hash in every asset file name, so only `index.html` needs invalidating.

**5. Show it off.** Add the workflow status badges to the top of your README.

**Done when:** merging a pull request updates the live site within about 10 minutes, and a pull request with a failing test is blocked from merging (turn on branch protection for `main`).

## Should you add blockchain?

No, not into this project and not before you have a job. Add a hash-chained audit log instead: it delivers the one idea from blockchain that fits a file vault (tamper evidence) in 2 days, using only Java and MySQL.

**Why not a real blockchain**

1. **It doesn't match the jobs you're applying for.** Java backend roles screen for Spring Boot, SQL, REST, security, Docker and cloud. Solidity, web3j, wallets and testnet gas fees are a separate skill set.
2. **It invites a question you can't win.** "Why does a private file vault need a blockchain?" One organisation runs the system and nobody else needs to verify it, so a blockchain adds cost and complexity without adding value. Interviewers spot resume decoration quickly.
3. **It costs 3 to 6 weeks** that are better spent on tests, AWS and interview practice.

**What to build instead: a tamper-evident audit log**

Each audit row stores the previous row's hash and its own hash. Editing or deleting any old row breaks every hash after it, which a verify endpoint detects. That is the core mechanism of a blockchain, without consensus or tokens. Keep one chain per user (share-link downloads go on the file owner's chain), so users can verify their own history.

```java
@Transactional
public void append(long userId, String action, String fileId, String details) {
  ChainHead head = heads.lockForUser(userId);   // SELECT ... FOR UPDATE: one writer at a time
  Instant at = Instant.now().truncatedTo(ChronoUnit.MICROS);  // DATETIME(6) keeps micros only
  String prev = head.getLastHash();             // "GENESIS" for a user's first entry
  String hash = sha256(prev + "|" + userId + "|" + action + "|" + fileId + "|" + details + "|" + at);
  audit.save(new AuditEntry(userId, action, fileId, details, at, prev, hash));
  head.setLastHash(hash);
}

public VerifyResult verify(long userId) {
  String prev = "GENESIS";
  for (AuditEntry e : audit.findByUserIdOrderByIdAsc(userId)) {
    String expected = sha256(prev + "|" + e.getUserId() + "|" + e.getAction() + "|"
        + e.getFileId() + "|" + e.getDetails() + "|" + e.getCreatedAt());
    if (!e.getPrevHash().equals(prev) || !e.getEntryHash().equals(expected)) {
      return VerifyResult.brokenAt(e.getId());
    }
    prev = e.getEntryHash();
  }
  return VerifyResult.ok();
}
```

The lock matters: without it, two simultaneous uploads read the same previous hash and the chain forks. Explaining that race, and how you fixed it, is a stronger interview moment than any blockchain demo. Add a small `chain_heads (user_id, last_hash)` table and a "Verify my history" button on the Activity page.

**When blockchain would make sense:** only if you later target Web3 or crypto-fintech companies. Then, as a time-boxed one-week extension after you're getting interviews, publish a daily hash of all chain heads to the Ethereum Sepolia testnet with web3j ("anchoring"), so outsiders could verify your logs. Only hashes ever go on-chain: never file contents, keys, emails or any personal data.

**How to say it in an interview:** "I considered blockchain for tamper evidence. Because one organisation controls the system, a hash chain gives the same tamper evidence without consensus cost. If third parties ever needed to verify the logs, I'd anchor the chain head on a public chain."

## Stretch features after the MVP is live

Add these one at a time, in this order, only after the live link works; each one is a separate resume or interview talking point, and you can stop at any point.

1. **Hash-chained audit log** (2 days). Described in the blockchain section above.
2. **Share with another registered user** (2 to 3 days). The project's first service-to-service call: vault-service asks auth-service to turn an email into a user id, using Spring's HTTP interface client with `lb://auth-service` through Eureka and forwarding the caller's JWT. Adds a "Shared with me" list. It answers the classic "how do your services talk to each other?"
3. **ECS Fargate + load balancer** (3 to 4 days). Stage 2 of the AWS phase.
4. **Rate limiting on login and upload** (1 to 2 days). Redis container plus Spring Cloud Gateway's `RequestRateLimiter` filter, e.g. 5 login attempts per minute per IP, using the failed-login log from Phase 2.
5. **Storage behind an interface** (2 days). `StorageService` with `GridFsStorageService` and `S3StorageService` (with presigned URLs), chosen by a property. This answers "why not S3?" with working code.
6. **Events with Kafka** (3 to 4 days). vault-service publishes `FileUploaded` and `ShareLinkUsed`; a small notification-service emails the owner. Kafka appears in many Indian job posts; if EC2 memory is tight, run it locally only, or use RabbitMQ.
7. **Resilience4j** (1 day). Circuit breaker and timeouts on gateway routes, with a friendly fallback message.
8. **Folders and file versions** (3 days). Mostly MySQL modelling: a self-referencing `folders` table and a `file_versions` table.
9. **Chunked streaming encryption** (3 days). Encrypt 1 MB chunks separately so large files never sit fully in memory, then raise the size limit.
10. **Metrics dashboard** (2 days). Micrometer with Prometheus and Grafana locally, or CloudWatch metrics on AWS.
11. **Two-factor login with TOTP** (2 days). Works with Google Authenticator.
12. **Terraform for the AWS setup** (3 to 4 days). Rebuild everything from code with one command; strong for DevOps-leaning roles.

## Turning the project into interviews

A recruiter spends under a minute on your GitHub, so the README, a live link and a 2-minute video do more for you than any extra feature.

**README must have, top to bottom**

1. One-line pitch: "Encrypted file vault: React + Spring Boot microservices + MySQL/MongoDB, deployed on AWS with KMS."
2. Live link, plus a demo login (e.g. demo@vault.dev) so nobody has to register.
3. A 2-minute demo video: upload, list, download, a share link in incognito, and two blocked attempts (another user's file gets 404, a renamed `.exe` gets 415).
4. The architecture and file-lifecycle diagrams from this doc.
5. "How encryption works" in 5 bullet points, followed by the security model and known limitations.
6. Tech stack badges, CI status badges, Swagger link, and a Postman collection in the repo.
7. Run locally: `docker compose up` and two commands.
8. Design decisions and trade-offs: why two databases, why GridFS, why EC2 before ECS, why no blockchain.
9. What I'd do next (from the stretch list).

**Resume bullets** (replace the bracketed numbers with your real ones; never invent numbers)

- Built a secure file-vault platform with React and 4 Spring Boot microservices (Spring Cloud Gateway, Eureka), deployed on AWS (EC2, RDS, S3, CloudFront, ECR, KMS) with a GitHub Actions CI/CD pipeline. (If you complete Stage 2, add: "later migrated to ECS Fargate behind an ALB".)
- Implemented AES-256-GCM envelope encryption with per-file data keys wrapped by AWS KMS and bound to each file through encryption context, detecting any tampering on download.
- Designed dual-database storage (MySQL for metadata, MongoDB GridFS for encrypted files) with a PENDING → ACTIVE upload state machine and an idempotent cleanup job, keeping both stores consistent without distributed transactions.
- Secured the platform with Spring Security JWT, Argon2id password hashing, rotating httpOnly refresh tokens with reuse detection, content-based file-type validation, and expiring share links with atomic download limits; \[N\] tests including Testcontainers integration tests.

**Questions you will be asked, and the core of a strong answer**

| Question | Core of a strong answer |
| --- | --- |
| Why microservices for a project this size? | To learn and show service boundaries: auth and storage change and scale independently. I kept it to 2 business services; at this size a modular monolith is also valid, and I can explain the trade-off. |
| Why two databases? | MySQL for relational data with constraints (users, shares, quotas, audit); GridFS splits large binaries into chunks and avoids MongoDB's 16 MB document limit. Cost: no shared transaction, so uploads move PENDING → ACTIVE with an idempotent cleanup job. |
| What if the MongoDB write succeeds and MySQL fails? | MySQL is the source of truth: the row was written as PENDING first. Any failure leaves it FAILED (or stuck PENDING), and the cleanup job deletes leftover bytes by file id. Every step is safe to repeat. |
| Walk me through an upload. | Check size, extension and real type; get a per-file data key (from KMS on AWS); encrypt with GCM using the file id as AAD; save a PENDING row; store the ciphertext in GridFS; mark it ACTIVE. |
| Where's the master key? | In AWS KMS; the app only ever holds short-lived plaintext data keys. IAM limits the EC2 role to GenerateDataKey and Decrypt on one key, and CloudTrail logs each use. Locally it's an environment variable. |
| Is it end-to-end encrypted? | No, and the README says so: it's server-side, application-level encryption. It protects stolen databases and backups, not a compromised app server. End-to-end would mean encrypting in the browser. |
| Why GCM over CBC? | GCM is authenticated: its tag detects any modification. CBC alone gives no integrity and needs a separate MAC. |
| How do you stop a malicious upload? | Size cap, extension allowlist, real type detected from the bytes with Tika, generated storage names, and downloads served as attachments with nosniff. |
| Why Argon2id for passwords? | It's memory-hard, which makes GPU cracking expensive; OWASP lists it first and bcrypt only for legacy systems. I used OWASP's minimum parameters. |
| How do your services communicate? | The gateway routes by path and every service validates the same JWT, so the MVP needs no service-to-service calls. Sharing with a user adds one synchronous call through Spring's HTTP interface client and Eureka; side effects like emails would go through Kafka events. |
| JWT is stateless; how do you log out? | Access tokens live 15 minutes; logout revokes the refresh token in the database, so no new access tokens can be issued. |
| Where does the browser keep tokens? | Access token in memory only; refresh token in an httpOnly, Secure, SameSite=Strict cookie that JavaScript can't read. |
| How does the gateway find services? | Eureka registry plus `lb://` load-balanced URIs; on ECS I'd consider Service Connect instead. |
| Two people use a 1-download share link at the same moment? | A single conditional UPDATE consumes the use; only one gets 1 row updated, the other gets 410. |
| How would you scale to a million users? | Stateless services on ECS with auto scaling behind an ALB, S3 plus presigned URLs for files, RDS read replicas, Redis cache, Kafka for async work. |
| What would you do differently? | Start with a modular monolith, use S3 from day one, and use RS256 so only auth-service holds the signing key. |

**Practise out loud.** Draw the architecture and the upload flow on paper in under 3 minutes, from memory. If you can't explain a line of code (including anything copied from this guide or an AI), rewrite it until you can.

## Pitfalls and final checklist

Most fresher projects fail from scope creep and copied code, not from hard problems; these are the traps to watch.

**Pitfalls**

- **Building every service before anything works.** Get one vertical slice working first: register → login → upload → download, end to end. Then widen.
- **Following Spring Boot 3 tutorials blindly.** Gateway routes need the `server.webflux` prefix, Mongo uses `spring.mongodb.uri`, tests use `@MockitoBean`.
- **Committing secrets.** Put `.env` in `.gitignore` from day one. If a key ever reaches GitHub, treat it as leaked and generate a new one.
- **Losing the master key.** Locally, back up `.env`; on AWS, never schedule the KMS key for deletion. Without the key, every stored file is unreadable forever.
- **Time zones in hashes and dates.** Set `spring.jpa.properties.hibernate.jdbc.time_zone: UTC` in every service.
- **`ddl-auto: update`.** Let Flyway own the schema; if `validate` complains about a column, fix the migration to match the entity.
- **Out of memory on a small EC2.** Set `mem_limit` per container, add a 2 GB swap file, or move to m7i-flex.large.
- **Forgotten AWS resources.** Stopped RDS restarts after 7 days; load balancers and NAT gateways bill every hour even when idle; Elastic IPs and snapshots cost money too. Check Billing every Sunday.
- **Copying code you can't explain**, from this guide, a tutorial or an AI. Retype it, change it, break it, and fix it until you could rebuild it on a whiteboard.
- **Endless UI polishing.** Clean and responsive is enough; recruiters judge the backend and the deployment.

**Final checklist**

- [ ] MVP features 1 to 7 work end to end locally
- [ ] CI green on every push since week 1, with 25+ meaningful tests
- [ ] A test proves a failed upload ends FAILED and the cleanup job clears it
- [ ] Live HTTPS link on AWS with a demo login, file keys wrapped by KMS
- [ ] AWS Budget alert active, MFA on root
- [ ] README with both diagrams, video, Swagger link, security model and known limitations
- [ ] Hash-chained audit log with a "Verify my history" button
- [ ] Resume bullets updated with your real numbers
- [ ] Can draw the architecture, upload flow and file lifecycle from memory in 3 minutes
- [ ] Practised every interview question in the table out loud
- [ ] LinkedIn post with the demo video and repo link

## Revision log

Version 2 folds in the strongest ideas from a second roadmap you reviewed; the rest of that plan was already covered here.

| Date | Change | Why |
| --- | --- | --- |
| 18 Oct 2026 | Upload writes a PENDING row first, then the bytes, then marks ACTIVE; cleanup is status-based | MySQL becomes the source of truth; failures are easy to find and safe to retry |
| 18 Oct 2026 | KMS moved from the stretch list into the AWS phase, behind a `KeyWrapper` interface | Key management is the core of a vault's security story |
| 18 Oct 2026 | Argon2id replaces bcrypt | OWASP's first choice; bcrypt is for legacy systems |
| 18 Oct 2026 | Real-type check with Apache Tika moved into the MVP | OWASP file upload guidance |
| 18 Oct 2026 | Tests run in GitHub Actions from week 1; Phase 7 adds deployment only | Breakage is caught the day it happens |
| 18 Oct 2026 | Security model and known limitations added | Server-side encryption, not end-to-end, stated honestly |
| 18 Oct 2026 | Optional ECS Fargate + load balancer stage added | Resume keyword plus a cost trade-off story |
| 18 Oct 2026 | Share-with-user stretch item added | The project's first service-to-service call |
| 18 Oct 2026 | Kept as before: Eureka (with a static-URL fallback), EC2 before ECS, share links in the MVP, hash chain instead of blockchain | Interview keyword, credit budget, concurrency talking point, fit to the problem |

## Sources

Versions and AWS details checked on 18 October 2026.

- [Spring Boot versions and support dates (HeroDevs)](https://www.herodevs.com/blog-posts/spring-boot-versions-eol-dates-and-latest-releases-april-2026)
- [Spring Cloud 2025.1.2 release (spring.io)](https://spring.io/blog/2026/06/11/spring-cloud-2025-1-2-aka-oakwood-has-been-released/)
- [Spring Cloud 2025.0 release notes: Gateway starter and property renames](https://github.com/spring-cloud/spring-cloud-release/wiki/Spring-Cloud-2025.0-Release-Notes)
- [Gateway 5.0 legacy keys stop binding silently (Broadleaf)](https://developer.broadleafcommerce.com/utility-services/gateways/release-notes/release-notes-3-0-0)
- [Spring Boot 4.0 migration guide: MongoDB property renames](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
- [OWASP File Upload Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html)
- [AWS Free Tier: $200 credits and 6-month Free plan](https://aws.amazon.com/about-aws/whats-new/2025/07/aws-free-tier-credits-month-free-plan/)
- [AWS Free Tier FAQs](https://aws.amazon.com/free/faqs/)
- [Services available on the AWS Free plan](https://docs.aws.amazon.com/accounts/latest/reference/supported-services-sign-up-new.html)
- [EC2 Free-plan eligible instance types](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-free-tier-usage.html)
- [MongoDB Atlas free cluster limits](https://mongodb.com/docs/atlas/reference/free-shared-limitations/)
- [Atlas free tier storage and regions (Studio 3T)](https://studio3t.com/?p=27575)
