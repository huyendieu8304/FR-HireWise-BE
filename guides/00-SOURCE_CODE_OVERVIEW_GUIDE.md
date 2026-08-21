# AI Code Generation Guide - HireWise-BE

Tài liệu này gom lại **toàn bộ ngữ cảnh kỹ thuật của backend HireWise-BE**
(dependency, kiến trúc layer, coding convention, authN/authZ, error handling,
logging, comment, testing) thành **1 file duy nhất**, mục đích chính:
**dán vào prompt cho AI (Claude, Copilot...) khi nhờ code 1 API nghiệp vụ
mới**, để code AI sinh ra khớp với convention thật của dự án ngay từ đầu,
không phải sửa lại nhiều.

Tài liệu này **tóm tắt** từ các guide chi tiết hơn đã có sẵn trong
`guides/` - khi cần đọc sâu 1 chủ đề, xem thêm:

- `CODING_CONVENTION.md` - format, đặt tên, DRY, complexity
- `COMMENT_CONVENTION.md` - quy tắc viết comment/Javadoc
- `ERROR_HANDLING.md` - kiến trúc xử lý lỗi đầy đủ
- `LOGGING_CONVENTION.md` - quy tắc ghi log
- `TESTING_CONVENTION.md` - quy tắc viết test (JUnit 5) đầy đủ
- `GIT_WORKFLOW.md` - branching, commit, pull request

Nội dung dưới đây đọc trực tiếp từ source code hiện tại của
`com.hirewise.be` (không phải đề xuất lý thuyết).

---

## Mục lục

1. [Dependency / Tech stack](#1-dependency--tech-stack)
2. [Layer architecture](#2-layer-architecture)
3. [Coding convention (tóm tắt)](#3-coding-convention-tóm-tắt)
4. [Authentication](#4-authentication)
5. [Authorization (RBAC 4 lớp)](#5-authorization-rbac-4-lớp)
6. [Error handling](#6-error-handling)
7. [Logging](#7-logging)
8. [Comment](#8-comment)
9. [Testing (JUnit 5)](#9-testing-junit-5)
10. [Prompt template khi nhờ AI code 1 API nghiệp vụ mới](#10-prompt-template-khi-nhờ-ai-code-1-api-nghiệp-vụ-mới)
11. [Checklist tổng hợp trước khi merge](#11-checklist-tổng-hợp-trước-khi-merge)

---

## 1. Dependency / Tech stack

Đọc từ `pom.xml`. Khi sinh code, **chỉ dùng các dependency đã có sẵn**,
không tự thêm thư viện mới trừ khi được yêu cầu rõ ràng.

| Nhóm | Dependency | Dùng để |
|---|---|---|
| Runtime | Java 21, Spring Boot 4.1.0 (`spring-boot-starter-parent`) | nền tảng |
| Web | `spring-boot-starter-webmvc` | REST controller (Spring MVC, không phải WebFlux) |
| Persistence | `spring-boot-starter-data-jpa` | Entity/Repository (Hibernate) |
| Migration | `spring-boot-starter-flyway`, `flyway-database-postgresql` | schema migration, file `db/migration/V<n>__*.sql` |
| DB driver | `org.postgresql:postgresql` | PostgreSQL (production DB duy nhất, có dùng recursive CTE - xem `DepartmentRepository`) |
| Security | `spring-boot-starter-security`, `spring-boot-starter-security-oauth2-resource-server` | Spring Security + xác thực JWT (giữ lại phần Nimbus JWT encode/decode để **tự phát hành** access token, KHÔNG còn dùng Keycloak - xem mục 4) |
| AOP | `spring-boot-starter-aspectj` | `OwnershipAspect` (RBAC layer 4, xem mục 5) |
| Validation | `spring-boot-starter-validation` | Bean Validation (`@NotBlank`, `@Size`...) trên DTO |
| Actuator | `spring-boot-starter-actuator` | `/actuator/health` (permitAll, dùng làm healthcheck) |
| Mail | `spring-boot-starter-mail` | gửi email (activation link...), SMTP local dùng MailHog |
| Cache | `com.github.ben-manes.caffeine:caffeine` | cache trong RBAC (role-permission, session, user status - có TTL, xem `application.properties` nhóm `app.rbac.*`) |
| Log | `net.logstash.logback:logstash-logback-encoder` | JSON log ở profile dev/prod (xem `logback-spring.xml`) |
| Boilerplate | `org.projectlombok:lombok` | `@Data`, `@Builder`, `@Slf4j`, `@AllArgsConstructor`, `@FieldDefaults`... |
| Test | `spring-boot-starter-*-test` (data-jpa, flyway, security-oauth2-resource-server, security, validation, webmvc) | hạ tầng test đã sẵn trong `pom.xml` cho unit/slice test (xem mục 9) |

Config quan trọng (`application.properties`, giá trị thật lấy từ biến môi
trường qua `.env.local`/`.env.dev`/`.env.prod`, **không hardcode**):

- `app.jwt.secret`, `app.jwt.access-token-ttl-seconds` (mặc định 8h)
- `app.auth.refresh-token-ttl-days` (mặc định 7 ngày)
- `app.auth.lockout-*` (khoá tài khoản sau 5 lần login sai / 15 phút)
- `app.auth.ip-rate-limit-*` (rate limit theo IP cho `/api/auth/login`, `/api/auth/google`)
- `app.rbac.*-cache-ttl-seconds` (TTL cache của RBAC, xem mục 5)
- `app.google.client-id` (Google SSO, rỗng = tắt tính năng)
- `app.bootstrap.admin-*` (tạo tài khoản HR_ADMIN đầu tiên khi DB trống)

---

## 2. Layer architecture

Package gốc: `com.hirewise.be`. Kiến trúc layer truyền thống (Controller
-> Service -> Repository), có thêm 2 package chuyên biệt cho
Authorization và Logging chạy xen giữa các layer qua filter/AOP.

```
com.hirewise.be
├── controller/      REST endpoint (@RestController), nhận request, gọi service, build ResponseEntity
├── service/         business logic, @Transactional, điều phối repository + RBAC layer 2/3
├── repository/      Spring Data JPA interface (extends JpaRepository)
├── domain/          JPA entity (@Entity) + enum domain (JobStatus, UserStatus...)
├── dto/
│   ├── request/     DTO nhận input, có Bean Validation annotation
│   └── response/    DTO trả ra ngoài
├── mapper/          convert Entity <-> DTO, static method, KHÔNG query/gọi service (xem CODING_CONVENTION.md mục 3)
├── authorization/   RBAC 4 lớp: AccessControlService, OwnershipAspect, ResourceContext, PermissionCodes...
├── security/        authentication: JWT, CurrentUser, filter chain, rate limit, session
│   └── token/       phát hành/verify token (JwtTokenService, GoogleIdTokenVerifier, ActivationToken...)
├── exception/       BaseException + các exception con, ErrorCode, GlobalExceptionHandler
├── logging/         CorrelationIdFilter, LogMaskUtils
├── event/           transactional outbox pattern (OutboxEvent, OutboxEventPublisher, OutboxDispatcher) - dùng khi 1 hành động cần side-effect bất đồng bộ (gửi email...) mà không được rollback theo transaction chính
└── config/          Spring @Configuration (SecurityConfig, ValidationConfig, WebMvcConfig, ClockConfig, JacksonConfig, BootstrapAdminInitializer)
```

### Luồng xử lý 1 request

```
HTTP Request
  │
  ▼
SecurityFilterChain (config/SecurityConfig)
  │  BearerTokenAuthenticationFilter (verify JWT chữ ký/hạn dùng)
  │  -> UserContextMdcFilter        (gắn userId/userRoles vào MDC cho log)
  │  -> AuthenticationFreshnessFilter (RBAC layer 1 - xem mục 5)
  ▼
Controller (@Valid @RequestBody, @CurrentUserPrincipal CurrentUser)
  │  @RequiresOwnership (nếu có) -> OwnershipAspect chạy TRƯỚC method (AOP @Around)
  │  thực hiện RBAC layer 2+3+4 xong mới cho vào body method
  ▼
Service (@Transactional nếu có ghi DB)
  │  - Nếu KHÔNG có @RequiresOwnership ở controller: tự gọi
  │    accessControlService.checkAccess(...) (RBAC layer 2+3)
  │  - Validate nghiệp vụ (BusinessConflictException, ...)
  │  - Gọi Repository, map qua Mapper
  │  - log.info(...) khi tạo/sửa/xoá bản ghi
  ▼
Repository (Spring Data JPA) -> Domain entity
  ▼
Mapper -> Response DTO
  ▼
Controller trả ResponseEntity<...>
```

Lỗi ở **bất kỳ bước nào** ở trên (kể cả filter, trước khi vào Controller)
đều được `exception/GlobalExceptionHandler` bắt và trả về **cùng 1 format
JSON** (xem mục 6).

### Vai trò từng layer khi sinh code mới

| Layer | Được làm | KHÔNG được làm |
|---|---|---|
| Controller | nhận input, `@Valid`, resolve `CurrentUser`, gọi 1 service method, build `ResponseEntity` đúng HTTP status | chứa business logic, tự query DB, tự check quyền bằng `if` tay (dùng `@RequiresOwnership` hoặc để service gọi `AccessControlService`) |
| Service | business logic, transaction, gọi RBAC layer 2/3 nếu controller không có `@RequiresOwnership`, throw exception nghiệp vụ, log state quan trọng | tự log rồi throw (mục 6/7), convert DTO thủ công trùng lặp (dùng Mapper) |
| Repository | interface `extends JpaRepository`, custom `@Query` khi cần | chứa business logic |
| Mapper | convert Entity <-> DTO thuần tuý, `static` method | gọi repository/service, validate nghiệp vụ |
| Domain (entity) | field, quan hệ JPA, method tiện ích đơn giản (`isXxx()`) | business logic phức tạp nhiều bước (đặt trong Service) |

---

## 3. Coding convention (tóm tắt)

Chi tiết đầy đủ + ví dụ BAD/GOOD: xem `CODING_CONVENTION.md`. Tóm tắt các
điểm AI cần tuân theo khi sinh code:

- Format: reformat trước commit, dòng không quá 80 ký tự, cách dòng theo
  từng "bước" logic trong method (không viết dính thành 1 cục).
- Đặt tên: biến/hàm `camelCase` (biến = danh từ, hàm = động từ hoặc
  `is`/`has`/`can` cho boolean), hằng số `UPPER_SNAKE_CASE`, class
  `PascalCase`. Boolean đặt tên như câu hỏi đúng/sai.
- Tên phải phản ánh đúng nội dung: `XxxMapper` chỉ convert, `XxxService`
  chứa logic, `findById` không có side-effect.
- DRY: tách logic dùng chung, nhưng không gộp code giống nhau tình cờ của
  2 nghiệp vụ khác nhau.
- Complexity: method ngắn (~40-50 dòng), ưu tiên early return thay vì
  lồng `if/else` sâu, không quá 4-5 tham số/method (nhiều hơn thì gom
  thành DTO/record).
- Lombok pattern dùng xuyên suốt dự án: `@Data @NoArgsConstructor
  @AllArgsConstructor` cho DTO, thêm `@Builder` cho response DTO;
  `@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
  @AllArgsConstructor` cho Controller/Service (constructor injection qua
  Lombok, field `private final` không cần viết tay); `@Slf4j` cho log.

---

## 4. Authentication

Dự án **tự phát hành và tự verify JWT**  tài khoản lưu trong
chính DB nghiệp vụ (bảng `users`, `auth_identities`, `user_sessions`).

- **Đăng nhập**: `POST /api/auth/login` (email/password, hash BCrypt) hoặc
  `POST /api/auth/google` (Google Identity Services ID token, verify qua
  `GoogleIdTokenVerifier`). Cả 2 endpoint đều bị rate-limit theo IP
  (`LoginRateLimiter`, mặc định 20 lần/15 phút) và tài khoản tự khoá sau 5
  lần sai trong 15 phút (khoá 15 phút - `AuthService`).
- **Access token**: JWT tự ký HS256 (`app.jwt.secret`), TTL mặc định 8
  giờ, verify lại chính bằng `JwtDecoder` cùng secret (đóng vai trò vừa
  issuer vừa resource server qua `oauth2ResourceServer().jwt(...)`).
- **Refresh token**: TTL 7 ngày, đổi lấy access token mới qua
  `POST /api/auth/refresh`, gắn với 1 `user_sessions` record (`sid` claim
  trong JWT).
- **Logout**: `POST /api/auth/logout` - revoke session hiện tại
  (`SessionRegistryService`) dựa trên `sid` claim, JWT cũ vẫn còn hạn
  nhưng bị coi là không hợp lệ (xem RBAC layer 1 dưới đây).
- **Activation**: tài khoản mới tạo (`UserStatus.INVITED`) phải kích hoạt
  qua link chứa `ActivationToken` (TTL 72h) trước khi login được.
- **Lấy user hiện tại trong Controller**: tham số
  `@CurrentUserPrincipal CurrentUser currentUser` (resolve bởi
  `CurrentUserResolver` từ claim `sub` của JWT + roles fetch tươi mỗi
  request qua `ActiveRolesService`, cache TTL ngắn ~30s - **không** đọc
  roles từ token để role bị thu hồi có hiệu lực gần như ngay lập tức).

  ```java
  public record CurrentUser(Long userId, String email, String fullName, Set<String> roles) {
      public boolean hasRole(String role) { ... }
  }
  ```

- **Public endpoint** (không cần token, khai báo trong `SecurityConfig`):
  `/actuator/health/**`, `GET /api/public/**`, `/api/auth/**`. Mọi
  endpoint khác mặc định **phải có access token hợp lệ**
  (`anyRequest().authenticated()`).
- **RBAC layer 1 - Authentication Freshness** (`AuthenticationFreshnessFilter`,
  chạy sau khi JWT đã xác thực xong nhưng trước Controller): dù JWT còn
  hạn, request vẫn bị từ chối (`AccountNotActiveException` -> 403) nếu:
  - tài khoản không còn `ACTIVE` (bị khoá/chưa kích hoạt - kiểm tra qua
    `UserDirectoryService`, cache TTL ~45s), hoặc
  - session (`sid`) đã bị revoke (logout, hoặc admin thu hồi -
    `SessionRegistryService`, cache TTL ~20s).

  Đây là lý do tài khoản bị khoá/logout có hiệu lực gần như ngay, không
  phải chờ JWT hết hạn.

---

## 5. Authorization (RBAC 4 lớp)

Dự án có pipeline phân quyền cố định **4 lớp**, đọc từ package
`authorization/`. Khi thêm 1 API nghiệp vụ mới, luôn phải xác định API đó
cần bao nhiêu lớp trong 4 lớp này.

| Layer | Kiểm tra gì | Exception khi chặn | Nơi thực thi |
|---|---|---|---|
| 1. Authentication Freshness | Tài khoản còn `ACTIVE`? Session còn hiệu lực? | `AccountNotActiveException` | `security/AuthenticationFreshnessFilter` (tự động, mọi request) |
| 2. Role-Permission | User có role nào được cấp `permissionCode` yêu cầu không | `PermissionDeniedException` | `AccessControlServiceImpl` |
| 3. Access Scope | Resource (phòng ban/job) có nằm trong scope được gán, đủ `can_write` không | `OutOfScopeException` | `AccessControlServiceImpl` (qua `AccessScopeService`) |
| 4. Ownership | User có đúng là chủ sở hữu resource cụ thể không | `NotResourceOwnerException` | `OwnershipAspect` (chỉ chạy khi có `@RequiresOwnership`) |

**Layer 2 (`RolePermissionCache`)**: mỗi role map tới tập
`permissionCode -> canWrite(boolean)`, nguồn dữ liệu là bảng
`role_permissions` trong DB (KHÔNG hardcode trong Java), cache TTL theo
`app.rbac.role-permission-cache-ttl-seconds` (mặc định 300s).

**Layer 3 (`AccessScopeService`)**: mỗi scope thuộc 1 trong 3 loại
`SYSTEM` (toàn quyền) / `DEPARTMENT` (kèm cờ `includeSubDepartments`,
tính đệ quy phòng ban con qua CTE) / `JOB` (gắn với 1 job cụ thể); action
ghi (`requiresWrite=true`) chỉ pass nếu scope có `canWrite=true`.

**Permission code**: khai báo hằng số trong `authorization/PermissionCodes.java`
(vd `JOB_CREATE`, `JOB_EDIT`, `USER_CREATE`, `ROLE_ASSIGN`...) - dùng
hằng số này khi gọi `checkAccess`/`@RequiresOwnership`, KHÔNG hardcode
chuỗi. Nếu API mới cần 1 permission chưa có, thêm hằng số mới vào file
này + thêm dữ liệu tương ứng vào migration `role_permissions`.

### 2 cách áp dụng RBAC trong code - CHỌN 1, không dùng cả 2 cho cùng 1 method

**Cách 1 - Chỉ cần layer 2+3** (endpoint không gắn với 1 resource cụ thể
đã tồn tại, vd `create`, `search`, hoặc resource đã biết chắc scope từ
input): gọi trực tiếp trong Service.

```java
public JobPostingResponseDto create(CreateJobPostingRequestDto request, CurrentUser currentUser) {
    accessControlService.checkAccess(currentUser, PermissionCodes.JOB_CREATE,
            ResourceContext.department(request.getDepartmentId()));
    ...
}
```

**Cách 2 - Cần cả layer 4 (ownership)** (endpoint thao tác trên 1 resource
đã tồn tại mà chỉ chủ sở hữu mới được sửa/xoá, vd `close`, `delete`):
annotate ở Controller bằng `@RequiresOwnership`, `OwnershipAspect` tự
chạy đủ layer 2+3+4 trước khi vào method - **Service KHÔNG gọi
`checkAccess` nữa** (tránh check trùng).

```java
@RequiresOwnership(resourceType = "JOB_POSITION", idParam = "id", permission = PermissionCodes.JOB_EDIT)
@PatchMapping("/{id}/close")
public ResponseEntity<JobPostingResponseDto> close(@PathVariable UUID id, @CurrentUserPrincipal CurrentUser currentUser) {
    return ResponseEntity.ok(jobPostingService.close(id));
}
```

Muốn dùng `@RequiresOwnership` cho 1 resource type mới, cần thêm 1
`OwnershipResolver` tương ứng (xem `JobPostingOwnershipResolver` làm mẫu)
và đăng ký vào `OwnershipResolverRegistry`.

**Bảo mật lỗi 403**: cả 4 exception RBAC (`AccountNotActiveException`,
`PermissionDeniedException`, `OutOfScopeException`,
`NotResourceOwnerException`) đều trả về **cùng 1 message chung**
(`ErrorCode.FORBIDDEN`) cho client - không lộ ra lớp nào chặn request, để
tránh dò quyền hệ thống từ bên ngoài. Không tạo `ErrorCode` riêng cho
từng trường hợp 403.

---

## 6. Error handling

Xem đầy đủ tại `ERROR_HANDLING.md`. Tóm tắt các quy tắc AI phải theo khi
sinh code mới:

1. **Không bao giờ** `throw new RuntimeException(...)` trần trụi cho lỗi
   nghiệp vụ - luôn dùng 1 exception con của `exception/BaseException`.

   ```
   BaseException (abstract, giữ ErrorCode + HttpStatus + args cho i18n)
     ├── BadRequestException          -> 400
     ├── UnauthorizedActionException  -> 401
     ├── ForbiddenActionException     -> 403
     │     ├── AccountNotActiveException / PermissionDeniedException
     │     ├── OutOfScopeException / NotResourceOwnerException
     ├── ResourceNotFoundException     -> 404
     ├── BusinessConflictException     -> 409
     └── KeycloakSyncException         -> 502 (mẫu cho lỗi phụ thuộc hệ thống ngoài)
   ```

2. **Not found** dùng chuẩn `.orElseThrow(...)` ngay tại điểm query:

   ```java
   private JobPosting findOrThrow(UUID id) {
       return jobPostingRepository.findById(id)
               .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.JOB_POSTING_NOT_FOUND, id));
   }
   ```

3. **Validate field đơn giản** (bắt buộc/độ dài/định dạng) dùng Bean
   Validation trên DTO + `@Valid` ở Controller, message qua key
   `message = "{validation.<domain>.<field>.<constraint>}"` trỏ về
   `messages.properties`, **không** tự viết `if` kiểm tra tay trong
   service.

4. **Validate nghiệp vụ** (không diễn tả được bằng annotation, vd "email
   đã tồn tại") throw `BusinessConflictException`/`BadRequestException`
   kèm `ErrorCode` phù hợp, càng sớm càng tốt (fail fast trước khi ghi
   DB).

5. **Không tự `log.error()`/`log.warn()` rồi mới `throw`** trong service -
   `GlobalExceptionHandler` đã log tập trung (xem mục 7).

6. **Thêm 1 lỗi mới**: thêm `ErrorCode` vào đúng nhóm domain trong
   `exception/ErrorCode.java` + thêm key `error.<ten>` vào
   `messages.properties` (dùng `{0}`, `{1}`... nếu cần chèn id/tên).

   ```java
   JOB_POSTING_ALREADY_CLOSED("error.job_posting_already_closed"),
   ```
   ```properties
   error.job_posting_already_closed=Job posting {0} is already closed.
   ```

Mọi response lỗi có cùng shape JSON (`dto/response/ErrorResponse.java`):

```json
{
  "timestamp": "2026-08-19T03:12:45.123Z",
  "status": 404,
  "code": "JOB_POSTING_NOT_FOUND",
  "message": "Job posting with ID 42 was not found.",
  "path": "/api/job-postings/42"
}
```

`code` (không phải `message`) là thứ FE dùng để switch logic - vì message
có thể đổi theo locale.

---

## 7. Logging

Xem đầy đủ tại `LOGGING_CONVENTION.md`. Tóm tắt:

- Luôn `@Slf4j` (Lombok), không tự tạo `Logger` thủ công.
- Luôn dùng placeholder `{}`, không nối chuỗi bằng `+`.
- **Không tự log rồi throw** trong service/usecase -
  `GlobalExceptionHandler` đã log tập trung (WARN cho lỗi nghiệp vụ đã
  lường trước, ERROR cho lỗi hệ thống không lường trước, DEBUG cho lỗi
  validation).
- `correlationId`, `userId`, `userRoles`, `traceId`, `spanId` đã tự động
  gắn vào MỌI dòng log của 1 request (MDC, xem `CorrelationIdFilter`,
  `UserContextMdcFilter`) - **không** cần tự truyền `userId` vào message.

| Khi nào | Level |
|---|---|
| Tạo/sửa/xoá bản ghi, đổi trạng thái | `INFO` (kèm id bản ghi bị tác động) |
| Gọi API/service ngoài | `INFO` trước + sau khi gọi (cả 2 nhánh thành công/thất bại) |
| Đọc dữ liệu thông thường (GET) | thường không log |
| Debug logic nội bộ | `DEBUG` |

```java
jobPostingRepository.save(jobPosting);
log.info("Created job posting: {} (title={})", jobPosting.getId(), jobPosting.getTitle());
```

- **Không bao giờ log** password, OTP, token đầy đủ, số thẻ, CCCD/hộ
  chiếu. Field nhạy cảm khác (email, sđt) muốn log ở `DEBUG` phải mask
  qua `logging/LogMaskUtils` (`maskEmail`, `maskPhone`).

---

## 8. Comment

Xem đầy đủ tại `COMMENT_CONVENTION.md`. Tóm tắt:

- **Luôn viết comment bằng tiếng Anh**
- Không comment lại những gì code đã hiển nhiên; comment phải giải thích
  **vì sao**, không phải **đang làm gì**.
- **Javadoc bắt buộc** cho mọi `public` class/method trong `service`,
  `controller`, `repository`, `util`/`logging`, `security`,
  `authorization`, và mọi custom exception - dùng `@param`/`@return`/
  `@throws` khi có giá trị bổ sung (bỏ qua nếu tên tham số đã tự giải
  thích).
- **Inline comment bắt buộc** trước mọi regex, và tại các nhánh
  if/else/vòng lặp mang ý nghĩa nghiệp vụ không hiển nhiên.
- Task tag khi cần: `TODO`, `FIXME`, `BUG`, `HACK`, `NOTE`/`INFO`, `XXX`,
  `REVIEW`, `OPTIMIZE` - format `// TAG: mô tả ngắn gọn`.
- Không để code chết dưới dạng comment, không để Javadoc/comment lỗi thời
  so với code hiện tại.

---

## 9. Testing (JUnit 5)

Xem đầy đủ tại `TESTING_CONVENTION.md`. Tóm tắt:

- **JUnit 5** + **Mockito** (`@ExtendWith(MockitoExtension.class)`,
  `@Mock`) + **AssertJ** (`assertThat`/`assertThatThrownBy`/
  `assertThatCode`) - KHÔNG dùng `org.junit.jupiter.api.Assertions`.
- Tên method test = **tình huống + kết quả mong đợi**, theo 1 trong 2
  dạng đã dùng nhất quán trong dự án: câu camelCase liền mạch
  (`deniesWhenNoRoleGrantsPermission`) hoặc `<tình huống>_<kết quả>`
  (`ownerMismatch_throwsNotResourceOwner`) - không đặt tên chung chung
  (`test1`, `testSuccess`).
- Mỗi test tách rõ Arrange/Act/Assert bằng dòng trống, class test tên
  `<ClassĐượcTest>Test`, cùng package với class gốc trong `src/test/java`.
- 3 test level: **Unit** (mock toàn bộ dependency, đã áp dụng cho
  `authorization/`, `security/` - **bắt buộc** cho mọi Service mới),
  **Slice** (`@WebMvcTest`/`@DataJpaTest` - đã có sẵn dependency trong
  `pom.xml` nhưng **chưa dùng**, nên bổ sung khi Controller/Repository có
  logic đáng test), **Full integration** (`@SpringBootTest` +
  Testcontainers Postgres - dùng cho luồng rủi ro cao như login/RBAC).
- `mvn -B clean test` phải pass local trước khi tạo Pull Request (CI
  cũng chạy lại trên mọi PR vào `dev`/`main`).

---

## 10. Prompt template khi nhờ AI code 1 API nghiệp vụ mới

Copy khối dưới đây, điền vào phần `[...]`, đính kèm cùng file này khi nhờ
AI sinh code:

```
Bạn là backend dev của HireWise-BE (Spring Boot 4.1, Java 21, kiến trúc
Controller -> Service -> Repository, RBAC 4 lớp). Hãy code API nghiệp vụ
sau, tuân thủ NGHIÊM NGẶT convention mô tả trong AI_CODE_GENERATION_GUIDE.md
(layer architecture, coding convention, authN/authZ, error handling,
logging, comment, test).

## Nghiệp vụ
- Mô tả chức năng: [...]
- Endpoint: [METHOD] /api/[...]
- Ai được phép gọi (role nào): [...]
- Permission code cần dùng (đã có trong PermissionCodes hay cần thêm mới): [...]
- Có cần check ownership (RBAC layer 4, @RequiresOwnership) không, vì sao: [...]
- Request input: field, kiểu dữ liệu, validate rule (bắt buộc/độ dài/định dạng)
- Response output: field, kiểu dữ liệu
- Business rule / điều kiện lỗi nghiệp vụ (vd trạng thái không hợp lệ, trùng dữ liệu...): [...]

## Yêu cầu code ra
1. DTO request/response (nếu cần) trong dto/request, dto/response -
   @Data/@NoArgsConstructor/@AllArgsConstructor (+ @Builder cho response),
   Bean Validation message = "{validation.<domain>.<field>.<constraint>}".
2. Domain/entity thay đổi (nếu cần) + migration Flyway mới
   db/migration/V<n+1>__<mo_ta>.sql (n = số migration tiếp theo).
3. Repository method mới nếu cần custom query (@Query + Javadoc).
4. Mapper: static method thuần convert, không query/gọi service.
5. Service: business logic, @Transactional nếu có ghi DB, gọi
   accessControlService.checkAccess(...) HOẶC để controller dùng
   @RequiresOwnership (chỉ chọn 1 trong 2, không dùng cả 2), throw đúng
   exception con của BaseException (thêm ErrorCode + message key mới nếu
   cần), log.info(...) khi tạo/sửa/xoá bản ghi, KHÔNG tự log rồi throw.
6. Controller: @Valid @RequestBody, @CurrentUserPrincipal CurrentUser,
   trả ResponseEntity đúng HTTP status (201 + Location khi tạo mới, 204
   khi xoá...).
7. Javadoc cho mọi public method ở service/controller/repository, inline
   comment cho nhánh nghiệp vụ không hiển nhiên.
8. Unit test cho Service (JUnit 5 + MockitoExtension + AssertJ), tên
   method theo dạng <tình huống>_<kết quả mong đợi> hoặc câu mô tả
   camelCase liền mạch, bao phủ happy path + các nhánh lỗi + đúng
   permission/scope được gọi. Nếu có logic RBAC mới, thêm test tương ứng
   trong authorization/.

Không tự thêm dependency/thư viện mới ngoài pom.xml hiện có trừ khi được
yêu cầu rõ ràng. Nếu thiếu thông tin để code (vd permission code chưa rõ
tên, chưa rõ entity liên quan), hỏi lại trước khi code thay vì tự đoán.
```

---

## 11. Checklist tổng hợp trước khi merge

Gộp từ tất cả checklist ở các guide riêng lẻ - dùng khi review PR có API
nghiệp vụ mới:

- [ ] Đúng layer architecture (mục 2) - không có business logic lọt vào
      Controller/Mapper, không có convert DTO lọt vào Service.
- [ ] Format/đặt tên theo `CODING_CONVENTION.md`.
- [ ] RBAC dùng đúng 1 trong 2 cách (`checkAccess` trực tiếp HOẶC
      `@RequiresOwnership`), permission code lấy từ `PermissionCodes`,
      không tạo `ErrorCode` 403 riêng.
- [ ] Mọi lỗi nghiệp vụ đều throw exception con của `BaseException`, có
      `ErrorCode` + message key trong `messages.properties`.
- [ ] Log đúng level, dùng `{}` placeholder, không tự log rồi throw,
      không log dữ liệu nhạy cảm.
- [ ] Comment bằng tiếng Anh, có Javadoc cho method public ở layer bắt
      buộc, comment giải thích flow nghiệp vụ không hiển nhiên.
- [ ] Có unit test cho Service (+ authorization nếu có RBAC mới), tên
      test rõ tình huống/kết quả, dùng AssertJ + Mockito.
- [ ] `mvn -B clean test` pass local trước khi tạo Pull Request.
- [ ] Đã tự resolve conflict với `dev` trước khi tạo PR, assign đúng
      reviewer, đủ 2 approve (xem `GIT_WORKFLOW.md`).

---

*Tài liệu này tổng hợp từ source code + các guide chi tiết tại thời điểm
2026-08-19. Khi convention trong `guides/*.md` thay đổi, cập nhật lại file
này tương ứng để không bị lệch khi dùng làm ngữ cảnh prompt AI.*
