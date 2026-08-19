# Quy tắc ghi log - HireWise-BE

Tài liệu này áp dụng cho mọi code viết trong `com.hirewise.be`. Mục tiêu:
log phải giúp người đọc (chính bạn 3 tháng sau, hoặc đồng đội trực oncall)
**truy vết lại được** "ai đã làm gì, lúc nào, với bản ghi nào, kết quả ra
sao" mà không cần phải đọc lại toàn bộ code.

---

## 1. Nguyên tắc chung

1. **Luôn dùng SLF4J qua Lombok**: thêm `@Slf4j` lên đầu class, KHÔNG tự
   khai báo `Logger log = LoggerFactory.getLogger(...)` thủ công.

   ```java
   @Slf4j
   @Service
   public class JobPostingService { ... }
   ```

2. **Luôn dùng placeholder `{}`, không nối chuỗi**. Nối chuỗi (`"a" + b`)
   luôn tốn chi phí build string dù log level đó có đang bật hay không;
   placeholder chỉ build string khi appender thực sự ghi ra, và tránh
   `NullPointerException` khi tham số null.

   ```java
   // SAI
   log.info("User " + username + " logged in successfully");

   // ĐÚNG
   log.info("User {} logged in successfully", username);
   ```

3. **correlationId, userId, userRoles, traceId, spanId đã được tự động
   gắn vào MỌI dòng log của 1 request** - bạn KHÔNG cần tự truyền các
   giá trị này vào message. Xem mục 4.

4. **Không tự log lại khi throw exception nghiệp vụ trong
   service/usecase.** `GlobalExceptionHandler` (`exception/GlobalExceptionHandler.java`)
   đã đóng vai trò "advice" log tập trung cho mọi exception văng ra khỏi
   controller (WARN cho lỗi nghiệp vụ/403/401 đã lường trước, ERROR cho
   lỗi hệ thống không lường trước). Code trong service chỉ cần `throw`,
   không cần `log.error(...)` rồi mới `throw` ngay sau đó - tránh bị log
   trùng 2 lần cho cùng 1 lỗi.

---

## 2. Ghi log khi nào, ở đâu, mức nào

| Khi nào | Cụ thể | Level | Vì sao |
|---|---|---|---|
| **State nghiệp vụ quan trọng** | Tạo / sửa / xoá 1 bản ghi; đổi trạng thái object (vd `OPEN` → `CLOSED`) | `INFO` | Ảnh hưởng trực tiếp tới dữ liệu/quyền lợi user, cần truy vết lại sau này |
| **Exception / lỗi** | Log ngay trước khi (hoặc tại nơi) throw | `ERROR` (lỗi hệ thống không lường trước) hoặc `WARN` (lỗi nghiệp vụ đã lường trước: 404/409/403/401) | Đã có `GlobalExceptionHandler` lo phần này, code usecase thường **không cần** tự log |
| **Giao tiếp với dịch vụ khác** | Gọi API ngoài (bên thứ 3, service khác) | `INFO` trước khi gọi + sau khi nhận kết quả (thành công/thất bại) | Đây thường là điểm dễ lỗi nhất khi debug production (timeout, 5xx từ đối tác...) |
| Gửi email | (nếu có module gửi mail) | log riêng trong module gửi mail | Code nghiệp vụ gọi module đó không cần log lại |
| Việc đọc dữ liệu thông thường (GET) | vd `getById`, `search` | thường **không log** | Tần suất cao, ít giá trị điều tra, gây nhiễu log |
| Debug logic nội bộ | giá trị trung gian, nhánh rẽ if/else | `DEBUG` | Chỉ dev cần khi tìm lỗi, không bật mặc định ở dev/prod |
| Chi tiết rất sâu (request/response body đầy đủ, vòng lặp...) | | `TRACE` | Gần như không dùng trong sản phẩm thật |

### Ví dụ áp dụng thực tế (`service/JobPostingService.java`)

```java
@Transactional
public JobPostingResponseDto create(CreateJobPostingRequestDto request, CurrentUser currentUser) {
    ...
    jobPostingRepository.save(jobPosting);
    log.info("Created job posting: {} (title={})", jobPosting.getId(), jobPosting.getTitle());
    return JobPostingMapper.toResponseDto(jobPosting);
}
```

Không cần viết `log.info("User {} created job posting", userId)` - vì
`userId`/`userRoles` của người đang gọi request đã tự động có trong MDC
(xem mục 4), bạn chỉ cần mô tả **hành động + đối tượng bị tác động**.

### Ví dụ "gọi API ngoài" (khi bạn thêm 1 tích hợp mới)

Hiện tại HireWise-BE chưa có lời gọi ra service ngoài nào trong code
nghiệp vụ (việc verify JWT với Keycloak do Spring Security tự xử lý
ngầm). Khi bạn thêm 1 client gọi API ngoài (`RestClient`/`WebClient`),
áp dụng mẫu sau:

```java
@Slf4j
@Component
public class PayrollApiClient {

    public PayrollResponse syncEmployee(String employeeId) {
        log.info("Calling Payroll API: syncEmployee employeeId={}", employeeId);
        try {
            PayrollResponse response = restClient.post()
                    .uri("/employees/{id}/sync", employeeId)
                    .retrieve()
                    .body(PayrollResponse.class);
            log.info("Payroll API syncEmployee succeeded: employeeId={}, status={}", employeeId, response.status());
            return response;
        } catch (RestClientException ex) {
            log.error("Payroll API syncEmployee failed: employeeId={}", employeeId, ex);
            throw ex;
        }
    }
}
```

---

## 3. Log level - ý nghĩa và khi nào dùng

| Level | Ý nghĩa | Ví dụ trong dự án |
|---|---|---|
| `TRACE` | Chi tiết cực kỳ sâu, gần như không dùng trong sản phẩm thật | Nội dung đầy đủ của request/response khi debug 1 bug rất khó |
| `DEBUG` | Thông tin hữu ích cho dev khi gỡ lỗi | Giá trị biến trung gian, nhánh rẽ logic, số field lỗi validation |
| `INFO` | Thông tin có ích để theo dõi hoạt động bình thường của app | Tạo/sửa/xoá bản ghi, đổi trạng thái, gọi API ngoài |
| `WARN` | Tình huống bất thường nhưng app vẫn chạy bình thường | Exception nghiệp vụ (404/409), truy cập bị từ chối (403), token không hợp lệ (401) |
| `ERROR` | Lỗi nghiêm trọng, app không xử lý được request bình thường | Exception không lường trước (bug, mất kết nối DB, NPE...) - xem `GlobalExceptionHandler#handleAllUncaughtException` |

Spring Boot mặc định chỉ hiển thị từ `INFO` trở lên (`INFO`, `WARN`,
`ERROR`) - `DEBUG`/`TRACE` bị ẩn trừ khi bật riêng. Trong dự án, mức log
được cấu hình qua biến môi trường (`.env.local`/`.env.dev`/`.env.prod`,
xem `README.md`), KHÔNG hardcode trong `application.properties`:

```properties
# application.properties (đã cấu hình sẵn)
logging.level.root=${LOG_LEVEL_ROOT:INFO}
logging.level.com.hirewise.be=${LOG_LEVEL_APP:DEBUG}
```

- `logging.level.root`: áp dụng cho log của framework/thư viện bên thứ 3
  (Spring, Hibernate...) - thường để `INFO` hoặc `WARN`.
- `logging.level.com.hirewise.be`: áp dụng riêng cho code của chính dự
  án - local để `DEBUG` cho dễ debug, dev/prod nên để `INFO` để tránh
  log quá nhiều.

Muốn debug tạm 1 package cụ thể mà không đổi cấu hình chung, có thể set
thêm dòng riêng, ví dụ:

```properties
logging.level.com.hirewise.be.service.JobPostingService=TRACE
```

---

## 4. Log cần chứa những thông tin gì

### 4.1. Tự động có sẵn (không cần code tay)

| Field | Gắn vào MDC bởi | Ý nghĩa |
|---|---|---|
| `correlationId` | `logging/CorrelationIdFilter.java` | Id duy nhất cho 1 request (lấy từ header `X-Correlation-ID` hoặc tự sinh), dùng để lọc TẤT CẢ log của cùng 1 request, kể cả log ở nhiều class khác nhau |
| `userId` | `security/UserContextMdcFilter.java` | `sub` (id Keycloak) của user đang gọi request, rỗng nếu request không có JWT hợp lệ |
| `userRoles` | `security/UserContextMdcFilter.java` | Danh sách role của user đang gọi request |
| `traceId`, `spanId` | Micrometer Tracing / OpenTelemetry (tự động khi bật, xem README mục Observability) | Liên kết log với distributed trace trên Grafana Tempo/Jaeger |

Ở local, các field này hiển thị ngay trên console (xem
`logback-spring.xml`, profile `local`):

```
2026-08-15 10:12:03.123 INFO  [http-nio-8080-exec-1] c.h.b.s.JobPostingService [cid=3f2a... uid=b91c... role=ROLE_RECRUITER] : Created job posting: 42 (title=Backend Developer)
```

Ở dev/prod, log in ra dạng JSON (đọc bằng Grafana/Loki thay vì mắt
người), cùng nội dung field nhưng dạng có cấu trúc:

```json
{"@timestamp":"...","level":"INFO","logger_name":"com.hirewise.be.service.JobPostingService","message":"Created job posting: 42 (title=Backend Developer)","correlationId":"3f2a...","userId":"b91c...","userRoles":"ROLE_RECRUITER","service":"HireWise-BE","environment":"dev"}
```

### 4.2. Bạn phải tự viết trong message

Khi hành động của user tạo/sửa/xoá 1 bản ghi cụ thể, **luôn ghi lại id
của bản ghi đó + mô tả ngắn gọn đã làm gì** - để sau này đọc lại hiểu
ngay "à, user này đã làm cái đó":

```java
log.info("Update user information: {}", userId);
log.info("Created job posting: {} (title={})", jobPosting.getId(), jobPosting.getTitle());
log.info("Closed job posting: {}", id);
```

Không cần lặp lại `userId`/`role` của người thực hiện hành động trong
message (đã có trong MDC) - chỉ cần id/mô tả của **đối tượng bị tác
động**.

---

## 5. Không ghi log dữ liệu nhạy cảm

**Tuyệt đối không log** (dù đã mask hay chưa):

- Mật khẩu, mã OTP, access token/refresh token đầy đủ, client secret.
- Số thẻ ngân hàng, CVV.
- Số CCCD/CMND, hộ chiếu đầy đủ.

**Có thể log ở mức DEBUG nếu đã che (mask)**, dùng
`com.hirewise.be.logging.LogMaskUtils`:

```java
log.debug("Recruiter email: {}", LogMaskUtils.maskEmail(email));
// -> "Recruiter email: ng****a@gmail.com"

log.debug("Candidate phone: {}", LogMaskUtils.maskPhone(phone));
// -> "Candidate phone: 098****321"
```

Nếu không chắc 1 field có nhạy cảm hay không, mặc định **không log** -
hỏi lại team lead/PO thay vì tự quyết định log ra.

---

## 6. Checklist trước khi commit code có thêm log

- [ ] Dùng `@Slf4j`, không tự tạo `Logger` thủ công.
- [ ] Dùng placeholder `{}`, không nối chuỗi bằng `+`.
- [ ] Log state nghiệp vụ quan trọng (tạo/sửa/xoá/đổi trạng thái) ở
      `INFO`, có kèm id bản ghi bị tác động.
- [ ] Không tự `log.error()` rồi `throw` trong service - để
      `GlobalExceptionHandler` lo (trừ khi bạn có lý do rõ ràng cần log
      thêm context đặc thù mà advice không có).
- [ ] Không log password/token/số thẻ/CCCD dù có mask hay không.
- [ ] Nếu bắt buộc log field nhạy cảm khác (email, sđt...) để debug, đã
      dùng `LogMaskUtils`.
- [ ] Log gọi API ngoài có cả log lúc gọi và lúc nhận kết quả (thành
      công lẫn thất bại).
- [ ] Không log ở `INFO` cho các API đọc dữ liệu tần suất cao (GET
      thông thường) - dùng `DEBUG` nếu thực sự cần.
