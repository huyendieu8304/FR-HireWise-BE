# Quy tắc xử lý lỗi (Error Handling) - HireWise-BE

Tài liệu này mô tả **kiến trúc xử lý lỗi hiện tại** của `com.hirewise.be`
(đọc trực tiếp từ source code, không phải đề xuất mới) và **quy tắc cần
tuân theo** khi thêm lỗi mới: ném lỗi (throw) ở đâu, dùng exception nào,
message/error code đặt ra sao.

Liên quan: `LOGGING_CONVENTION.md` (log lỗi ở mức nào, ai chịu trách
nhiệm log), `COMMENT_CONVENTION.md`, `CODING_CONVENTION.md`.

---

## 1. Kiến trúc tổng quan

Toàn bộ lỗi trong app - dù ném ra từ Controller, Service, hay từ chính
Spring Security filter chain (trước khi vào tới Controller) - đều được
gom về **1 nơi duy nhất xử lý và trả JSON đồng nhất**:
`exception/GlobalExceptionHandler.java` (`@RestControllerAdvice`).

```
Request
  │
  ▼
SecurityFilterChain (SecurityConfig)
  │  - JWT thiếu/hết hạn      -> AuthenticationException
  │  - JWT hợp lệ nhưng thiếu role (method security) -> AccessDeniedException
  │       CustomAuthenticationEntryPoint / CustomAccessDeniedHandler
  │       ủy quyền lại cho HandlerExceptionResolver, KHÔNG tự trả response
  ▼
Controller
  │  - @Valid trên @RequestBody -> MethodArgumentNotValidException (400)
  │  - @RequiresOwnership (OwnershipAspect, AOP @Around) -> RBAC layer 2/3/4
  ▼
Service / usecase
  │  - Nghiệp vụ throw các exception con của BaseException
  │    (ResourceNotFoundException, BusinessConflictException,
  │     KeycloakSyncException, ForbiddenActionException...)
  ▼
GlobalExceptionHandler (@RestControllerAdvice)
  │  - Bắt tất cả loại lỗi ở trên bằng các @ExceptionHandler tương ứng
  │  - Build ErrorResponse thống nhất (timestamp, status, code, message,
  │    path, fieldErrors) + LOG (WARN/ERROR/DEBUG tuỳ loại - xem mục 6)
  ▼
Response JSON (cùng 1 format cho MỌI loại lỗi)
```

Điểm quan trọng: **`CustomAuthenticationEntryPoint` và
`CustomAccessDeniedHandler`** (package `security`) không tự trả response
- chúng ủy quyền (`resolver.resolveException(...)`) lại cho
`HandlerExceptionResolver` của Spring MVC, để lỗi 401/403 phát sinh
**ngoài** Controller (ở tầng filter, trước `DispatcherServlet`) vẫn đi
qua `GlobalExceptionHandler` và có **cùng format JSON** với lỗi phát
sinh trong Controller/Service. Nhờ vậy FE chỉ cần xử lý 1 format
`ErrorResponse` duy nhất cho mọi trường hợp.

---

## 2. Phân cấp exception (`exception/BaseException.java`)

```
RuntimeException
  └── BaseException (abstract)          - errorCode + HttpStatus + args (cho i18n)
        ├── BadRequestException          -> 400
        ├── UnauthorizedActionException  -> 401
        ├── ForbiddenActionException     -> 403
        │     ├── AccountNotActiveException   (RBAC layer 1 - Authentication Freshness)
        │     ├── PermissionDeniedException   (RBAC layer 2 - Role-Permission)
        │     ├── OutOfScopeException         (RBAC layer 3 - Access Scope)
        │     └── NotResourceOwnerException   (RBAC layer 4 - Ownership)
        ├── ResourceNotFoundException     -> 404
        ├── BusinessConflictException     -> 409
        └── KeycloakSyncException         -> 502 (lỗi đồng bộ Keycloak, phụ thuộc hệ thống ngoài)
```

`BaseException` giữ 3 thứ: `ErrorCode` (mã lỗi + key i18n),
`HttpStatus` (do class con quyết định cứng, không cần truyền tay mỗi
lần throw), và `args` (tham số điền vào message, vd id bản ghi).

```java
protected BaseException(ErrorCode errorCode, HttpStatus status, Object... args) {
    super(errorCode.name());
    this.errorCode = errorCode;
    this.status = status;
    this.args = args;
}
```

| Exception | HTTP Status | Khi dùng |
|---|---|---|
| `BadRequestException` | 400 | Input sai định dạng/logic mà Bean Validation (`@Valid`) không bắt được |
| `UnauthorizedActionException` | 401 | Thiếu/sai thông tin xác thực phát sinh trong code nghiệp vụ (hiếm dùng - phần lớn 401 đến từ Spring Security, xem mục 1) |
| `ForbiddenActionException` (và 4 lớp con RBAC) | 403 | Không đủ quyền - xem chi tiết mục 3 |
| `ResourceNotFoundException` | 404 | Không tìm thấy bản ghi theo id, dùng chuẩn với `.orElseThrow(...)` |
| `BusinessConflictException` | 409 | Vi phạm ràng buộc nghiệp vụ trên state hiện tại (vd đóng job đã đóng, email đã tồn tại) |
| `KeycloakSyncException` | 502 | Gọi Keycloak Admin API thất bại - cố tình dùng 502 (Bad Gateway) thay vì 500, để phân biệt rõ "lỗi hệ thống ngoài (Identity Provider)" với "bug của chính app", tránh FE/QA hiểu nhầm là bug nghiệp vụ |

### 3 exception "constant" của RBAC

`AccountNotActiveException`, `PermissionDeniedException`,
`OutOfScopeException`, `NotResourceOwnerException` **không nhận
`ErrorCode`/`args` khi khởi tạo** - luôn hardcode `ErrorCode.FORBIDDEN`:

```java
public class PermissionDeniedException extends ForbiddenActionException {
    public PermissionDeniedException() {
        super(ErrorCode.FORBIDDEN);
    }
}
```

Đây là **chủ đích bảo mật**: dù lỗi 403 phát sinh ở layer nào trong 4
layer RBAC, client luôn chỉ thấy 1 message chung ("Access is denied...")
- không lộ ra layer nào chặn (permission thiếu? sai scope? không phải
owner?) để tránh dò quyền hệ thống từ bên ngoài. Tên class riêng
(`OutOfScopeException`...) chỉ phục vụ audit/log nội bộ - xem
`GlobalExceptionHandler#handleBaseException` log kèm
`ex.getClass().getSimpleName()`.

---

## 3. RBAC 4 lớp và lỗi tương ứng

Dự án có 1 pipeline phân quyền cố định 4 lớp (đọc từ `authorization/`),
mỗi lớp ném 1 loại lỗi riêng khi chặn request - áp dụng qua
`AccessControlService.checkAccess(...)` (layer 2+3, gọi trực tiếp trong
service) hoặc `@RequiresOwnership` + `OwnershipAspect` (layer 2+3+4,
chạy tự động qua AOP trước khi vào Controller method):

| Layer | Kiểm tra gì | Exception | Nơi thực thi |
|---|---|---|---|
| 1. Authentication Freshness | JWT hợp lệ nhưng `users.status != ACTIVE`, hoặc chưa có bản ghi nội bộ tương ứng | `AccountNotActiveException` | `security/AuthenticationFreshnessFilter` |
| 2. Role-Permission | User có role nào được cấp `permissionCode` yêu cầu không | `PermissionDeniedException` | `AccessControlServiceImpl` |
| 3. Access Scope | Resource mục tiêu (phòng ban/job) có nằm trong phạm vi được gán, đủ quyền ghi (`can_write`) không | `OutOfScopeException` | `AccessControlServiceImpl` (qua `AccessScopeService`) |
| 4. Ownership | User có đúng là chủ sở hữu resource cụ thể không (vd đúng `recruiter_id` của job) | `NotResourceOwnerException` | `OwnershipAspect` (chỉ chạy khi có `@RequiresOwnership`) |

```java
// OwnershipAspect - thứ tự bắt buộc: load resource 1 lần -> layer 2+3 -> layer 4
accessControlService.checkAccess(currentUser, requiresOwnership.permission(), resolved.toResourceContext());

if (policyRegistry.requiresOwnership(requiresOwnership.permission(), grantingRoles)
        && !Objects.equals(resolved.ownerId(), currentUser.userId())) {
    throw new NotResourceOwnerException();
}
```

Endpoint nào chỉ cần layer 2+3 (không cần check chủ sở hữu cụ thể, vd
`create`/`getById`/`search`) thì service tự gọi
`accessControlService.checkAccess(...)` trực tiếp, **không** dùng
`@RequiresOwnership` (tránh check trùng - xem comment đầu
`JobPostingService`).

---

## 4. ErrorCode và message (i18n)

`exception/ErrorCode.java` là enum liệt kê toàn bộ mã lỗi nghiệp vụ, mỗi
giá trị map với 1 key trong `resources/messages.properties`
(`MessageSource`, dùng `MessageFormat` nên message có thể có placeholder
`{0}`, `{1}`... khớp với `args` truyền vào khi throw):

```java
JOB_POSTING_NOT_FOUND("error.job_posting_not_found"),
```
```properties
error.job_posting_not_found=Job posting with ID {0} was not found.
```

```java
throw new ResourceNotFoundException(ErrorCode.JOB_POSTING_NOT_FOUND, id);
// -> message: "Job posting with ID 42 was not found."
```

Quy tắc đặt `ErrorCode`:

- Tên enum: `UPPER_SNAKE_CASE`, mô tả đúng lỗi gì (không đặt chung
  chung kiểu `ERROR_1`).
- Key i18n: `error.<snake_case_cua_ten_enum>`, đặt ở cuối
  `messages.properties`, gom nhóm theo domain (đã có comment nhóm
  `// Job posting sample domain`, `// RBAC / user administration`... -
  thêm ErrorCode mới vào đúng nhóm hoặc tạo nhóm mới nếu là domain
  mới).
- **Dùng lại `ErrorCode` chung** (`RESOURCE_NOT_FOUND`, `FORBIDDEN`,
  `VALIDATION_FAILED`...) khi lỗi không cần message riêng cho từng
  domain; chỉ tạo `ErrorCode` riêng khi cần message cụ thể (vd
  `JOB_POSTING_ALREADY_CLOSED` thay vì dùng chung 1
  `BUSINESS_CONFLICT` mơ hồ).

**Lưu ý hiện trạng**: `messages.properties` hiện chỉ có 1 file (default
locale), chưa có `messages_vi.properties`/`messages_en.properties`
riêng - hạ tầng i18n (`LocaleContextHolder`, `MessageSource`) đã sẵn
sàng nhưng **chưa thực sự đa ngôn ngữ**, mọi locale đang nhận cùng 1
message tiếng Anh. Nếu cần hỗ trợ đa ngôn ngữ thật, bổ sung file
`messages_<locale>.properties` cùng key, Spring sẽ tự chọn theo
`Accept-Language`.

### 4.1. Message validation Bean Validation (DTO request) - dung chung co che voi ErrorCode

Tuong tu ErrorCode, message cua Bean Validation annotation tren DTO
(`@NotBlank`, `@Size`, `@Email`, `@NotNull`...) **khong con hardcode
chuoi tieng Anh cung trong code** - dung cu phap `message = "{key}"` tro
ve 1 key trong CHUNG `resources/messages.properties` voi `error.*`, nho
`config/ValidationConfig.java` khai bao `LocalValidatorFactoryBean` rieng
va goi `setValidationMessageSource(messageSource)` (thay vi de Spring Boot
tu tao Validator mac dinh, chi doc bundle rieng `ValidationMessages.properties`
khong lien quan gi `messages.properties` cua app):

```java
@NotBlank(message = "{validation.user.email.required}")
@Email(message = "{validation.user.email.invalid}")
@Size(max = 255, message = "{validation.user.email.size}")
private String email;
```

```properties
validation.user.email.required=Email is required
validation.user.email.invalid=Must be a valid email address
validation.user.email.size=Email should not be longer than {max} characters
```

Quy tac dat key: `validation.<dto_hoac_domain>.<field>.<constraint>`, gom
nhom trong `messages.properties` o section `VALIDATION` rieng, tach voi
section `error.*`.

**Luu y khac biet voi `error.*`**: placeholder trong message validation
(vd `{max}`, `{min}`) la **ten thuoc tinh cua chinh constraint** (Hibernate
Validator tu thay bang gia tri that cua annotation, vd `@Size(max = 255)`
-> `{max}` = `255`), **khong phai** chi so vi tri `{0}`, `{1}`... nhu
`error.*` (`MessageFormat` cua Spring, khop voi `args` truyen vao khi
throw `BaseException`). Hai co che interpolation khac nhau du dung chung
1 file properties va chung co che i18n theo `Accept-Language`/
`LocaleContextHolder`.

Constraint nao khong khai bao `message = "{...}"` (dung message mac dinh
cua Hibernate Validator, vd `must not be blank`) van chay binh thuong -
khong bat buoc phai co key rieng, chi can khi muon message tuy bien/i18n
theo domain.

---

## 5. Format response lỗi (`dto/response/ErrorResponse.java`)

Mọi lỗi (400/401/403/404/409/500/502) đều trả về **cùng 1 shape JSON**:

```java
@Data @Builder
public class ErrorResponse {
    private String timestamp;               // Instant.now().toString()
    private int status;                      // HTTP status code
    private String code;                     // ErrorCode.name(), vd "JOB_POSTING_NOT_FOUND"
    private String message;                  // message đã resolve qua i18n
    private String path;                     // request.getRequestURI()
    private Map<String, String> fieldErrors; // chỉ có khi lỗi validation, field -> message
}
```

Ví dụ lỗi nghiệp vụ thông thường:

```json
{
  "timestamp": "2026-08-18T03:12:45.123Z",
  "status": 404,
  "code": "JOB_POSTING_NOT_FOUND",
  "message": "Job posting with ID 42 was not found.",
  "path": "/api/job-postings/42"
}
```

Ví dụ lỗi validation (`MethodArgumentNotValidException` - có thêm
`fieldErrors`):

```json
{
  "timestamp": "2026-08-18T03:12:45.123Z",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "Request body validation failed. Please correct the fields.",
  "path": "/api/job-postings",
  "fieldErrors": {
    "title": "Title is required",
    "departmentId": "Department is required"
  }
}
```

`code` luôn là `ErrorCode.name()` (không phải HTTP status text) - FE nên
switch theo `code`, không parse `message` (message có thể đổi câu chữ
theo locale sau này).

---

## 6. Cách ném lỗi (throw) - quy tắc thực tế

1. **Không bao giờ `throw new RuntimeException(...)` trần trụi** trong
   service/controller cho lỗi nghiệp vụ - luôn dùng 1 exception con của
   `BaseException` để có `HttpStatus` + `ErrorCode` + format response
   thống nhất.

2. **Not found -> dùng chuẩn `.orElseThrow(...)`** ngay tại điểm query,
   không tách thành `if (x == null)` riêng:

   ```java
   private JobPosting findOrThrow(Long id) {
       return jobPostingRepository.findById(id)
               .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.JOB_POSTING_NOT_FOUND, id));
   }
   ```

3. **Không tự `log.error()`/`log.warn()` rồi mới `throw`** trong
   service - `GlobalExceptionHandler` đã log tập trung (xem
   `LOGGING_CONVENTION.md` mục 1.4). Code service chỉ cần `throw`.

4. **Validate input đơn giản (bắt buộc/độ dài/định dạng field)** dùng
   Bean Validation annotation trên DTO (`@NotBlank`, `@NotNull`,
   `@Size`...) + `@Valid` ở Controller, **không** tự viết `if` kiểm tra
   tay trong service - để `MethodArgumentNotValidException` handler lo,
   tự động có `fieldErrors` chi tiết theo từng field. Message nên đặt qua
   key `message = "{validation.<...>}"` trỏ về `messages.properties`
   thay vì hardcode chuỗi - xem mục 4.1.

   ```java
   @NotBlank(message = "{validation.job_posting.title.required}")
   @Size(max = 255, message = "{validation.job_posting.title.size}")
   private String title;
   ```

5. **Validate mang tính nghiệp vụ** (không thể diễn tả bằng annotation,
   vd "email đã tồn tại", "job đã đóng rồi") thì throw
   `BusinessConflictException`/`BadRequestException` kèm `ErrorCode`
   phù hợp, ngay tại service, càng sớm càng tốt (fail fast trước khi
   thực hiện thao tác ghi DB).

   ```java
   if (userRepository.existsByEmail(request.getEmail())) {
       throw new BusinessConflictException(ErrorCode.USER_ALREADY_EXISTS);
   }
   ```

6. **Lỗi phụ thuộc hệ thống ngoài** (Keycloak, hoặc sau này thêm
   API/service khác) -> tạo/dùng exception riêng kiểu
   `KeycloakSyncException` (502), không dùng chung
   `ResourceNotFoundException`/`BadRequestException` vốn dành cho lỗi
   do chính app/input gây ra - giúp phân biệt rõ "lỗi input/nghiệp vụ
   do app" với "lỗi từ bên thứ 3" khi debug production.

7. **Giữ tính nhất quán dữ liệu khi 1 thao tác đụng tới 2 hệ thống**
   (DB nội bộ + Keycloak): tạo ở hệ thống ngoài trước, nếu bước ghi DB
   sau đó thất bại thì gọi **compensating action** để dọn lại, không để
   Keycloak và DB lệch nhau (không nuốt exception gốc - vẫn `throw` lại
   để `@Transactional` rollback đúng):

   ```java
   String keycloakId = keycloakAdminClient.createUser(request.getEmail(), request.getFullName());
   try {
       userRepository.save(user);
       return UserMapper.toResponseDto(user, Set.of());
   } catch (RuntimeException dbFailure) {
       // Compensate: dọn user "mồ côi" bên Keycloak vì bước ghi DB nội bộ
       // thất bại ngay sau khi Keycloak đã tạo xong.
       keycloakAdminClient.deleteUser(keycloakId);
       throw dbFailure;
   }
   ```

8. **Lỗi RBAC (403)**: mặc định dùng lại 1 trong 4 exception có sẵn
   (mục 3), không tự tạo `ErrorCode`/message riêng cho từng trường hợp
   403 cụ thể - giữ nguyên chủ đích "không lộ lý do chặn" (mục 2).

---

## 7. Log lỗi ở đâu

`GlobalExceptionHandler` là nơi **duy nhất** log lỗi phát sinh từ
Controller/Service/Security filter, mức log tuỳ loại:

| Loại lỗi | Log level | Vì sao |
|---|---|---|
| `BaseException` (mọi lỗi nghiệp vụ 400/401/403/404/409/502) | `WARN` | Đã lường trước, app vẫn hoạt động bình thường |
| Validation lỗi (`MethodArgumentNotValidException`), body sai định dạng, tham số sai kiểu | `DEBUG` | Lỗi phía client, tần suất có thể cao, không cần ồn ào ở mức WARN/ERROR |
| `AccessDeniedException`/`AuthenticationException` (từ Spring Security) | `WARN` | Đáng chú ý về bảo mật/audit nhưng không phải lỗi hệ thống |
| Mọi `Exception` khác không lường trước (`handleAllUncaughtException`) | `ERROR` (kèm full stack trace) | Bug thật/lỗi hạ tầng, cần biết ngay |

Chi tiết đầy đủ về log format, MDC (`correlationId`, `userId`...) xem
`LOGGING_CONVENTION.md`. Quy tắc cốt lõi: **service/usecase không tự
log lỗi nghiệp vụ, tránh log trùng 2 lần cho cùng 1 lỗi.**

---

## 8. Thêm 1 loại lỗi mới - checklist

- [ ] Lỗi có khớp 1 trong 6 nhóm HTTP status hiện có không (400/401/
      403/404/409/502)? Nếu có, dùng lại exception tương ứng, chỉ thêm
      `ErrorCode` mới.
- [ ] Nếu không khớp nhóm nào (vd cần 422/503...), tạo class mới kế
      thừa `BaseException`, constructor gọi `super(errorCode,
      HttpStatus.XXX, args)` - theo đúng mẫu các exception hiện có.
- [ ] Thêm `ErrorCode` mới vào đúng nhóm domain trong `ErrorCode.java`.
- [ ] Thêm key `error.<ten>` tương ứng vào `messages.properties`, dùng
      `{0}`, `{1}`... nếu message cần chèn id/tên bản ghi.
- [ ] Ném lỗi tại đúng nơi phát sinh (service, càng sớm càng tốt), kèm
      `args` đúng thứ tự khớp với placeholder trong message.
- [ ] Không tự `log.warn/error` trước khi `throw` - để
      `GlobalExceptionHandler` log.
- [ ] Nếu là lỗi 403 do RBAC, cân nhắc dùng lại 1 trong 4 exception
      RBAC có sẵn thay vì tạo `ErrorCode` mới lộ chi tiết lý do chặn.
- [ ] Nếu lỗi liên quan hệ thống ngoài mới (không phải Keycloak), cân
      nhắc pattern giống `KeycloakSyncException` (status riêng 502/503,
      tách biệt rõ với lỗi do chính app/input).
