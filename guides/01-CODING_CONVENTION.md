# Quy tắc viết code (Coding Convention) - HireWise-BE

Tài liệu này áp dụng cho mọi code viết trong `com.hirewise.be`. Mục tiêu:
code phải **dễ đọc, dễ đoán, dễ maintain** - người khác (hoặc chính bạn
sau này) mở 1 file lên là hiểu ngay nó làm gì mà không cần hỏi lại.

Tài liệu này nói về cách viết code (đặt tên, format, độ phức tạp...).
Về comment trong code xem `COMMENT_CONVENTION.md`, về log runtime xem
`LOGGING_CONVENTION.md` - 3 tài liệu bổ sung cho nhau, không thay thế
nhau.

---

## 1. Format code

1. **Luôn format code trước khi commit** để cả team dùng chung 1 style,
   tránh diff PR bị nhiễu chỉ vì khác cách format. Trong IntelliJ:
   `Code > Reformat Code` (`Ctrl+Alt+L`) trước mỗi lần commit.

2. **1 dòng không nên dài quá 80 ký tự.** IntelliJ có sẵn vạch kẻ
   (right margin) ở cột 80 để canh - bật ở `Settings > Editor > Code
   Style > Java > tab Wrapping and Braces` nếu chưa thấy vạch. Dòng quá
   dài (đặc biệt là chain method, điều kiện if) nên xuống dòng chia nhỏ.

   ```java
   // BAD - quá dài, phải cuộn ngang mới đọc hết
   if (currentUser.hasRole(Role.RECRUITER) && !currentUser.hasRole(Role.ADMIN) && jobPosting.getDepartmentId().equals(currentUser.getDepartmentId())) {

   // GOOD - xuống dòng theo điều kiện, dễ đọc
   boolean isDepartmentRecruiter = currentUser.hasRole(Role.RECRUITER)
           && !currentUser.hasRole(Role.ADMIN)
           && jobPosting.getDepartmentId().equals(currentUser.getDepartmentId());
   if (isDepartmentRecruiter) {
   ```

3. **Cách dòng (blank line) giữa các đoạn logic khác nhau trong cùng 1
   method** - không viết code dính thành 1 cục. Coi mỗi đoạn cách nhau
   bởi dòng trống như 1 "bước" trong flow xử lý.

   ```java
   // BAD - dính thành 1 cục, khó thấy các bước xử lý
   public JobPostingResponseDto create(CreateJobPostingRequestDto request, CurrentUser currentUser) {
       Department department = departmentRepository.findById(request.departmentId())
               .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.DEPARTMENT_NOT_FOUND));
       JobPosting jobPosting = JobPostingMapper.toEntity(request, department);
       jobPosting.setCreatedBy(currentUser.getId());
       jobPostingRepository.save(jobPosting);
       log.info("Created job posting: {} (title={})", jobPosting.getId(), jobPosting.getTitle());
       return JobPostingMapper.toResponseDto(jobPosting);
   }

   // GOOD - mỗi bước 1 nhóm, cách nhau bằng dòng trống
   public JobPostingResponseDto create(CreateJobPostingRequestDto request, CurrentUser currentUser) {
       Department department = departmentRepository.findById(request.departmentId())
               .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.DEPARTMENT_NOT_FOUND));

       JobPosting jobPosting = JobPostingMapper.toEntity(request, department);
       jobPosting.setCreatedBy(currentUser.getId());
       jobPostingRepository.save(jobPosting);

       log.info("Created job posting: {} (title={})", jobPosting.getId(), jobPosting.getTitle());
       return JobPostingMapper.toResponseDto(jobPosting);
   }
   ```

4. Dự án hiện chưa cấu hình formatter tự động (Spotless/Checkstyle) qua
   `pom.xml` - format đang dựa vào formatter mặc định của IntelliJ. Nếu
   team thấy diff PR vẫn bị lệch style nhiều.

---

## 2. Đặt tên

Nguyên tắc chung: **tên phải nói lên được nó LÀ gì / LÀM gì**, đọc tên
là đoán được nội dung, không cần mở code ra xem.

| Đối tượng | Quy tắc | Ví dụ |
|---|---|---|
| Biến (variable) | Danh từ, `camelCase` | `jobPosting`, `currentUser`, `retryCount` |
| Hàm/method | Động từ (hành động) hoặc `is`/`has`/`can` (boolean), `camelCase` | `createJobPosting()`, `resolveScope()`, `isExpired()`, `hasPermission()` |
| Hằng số (constant, `static final`) | `UPPER_SNAKE_CASE`, các từ cách nhau bằng `_` | `MAX_ATTEMPTS`, `DEFAULT_PAGE_SIZE`, `TOKEN_EXPIRY_MINUTES` |
| Class/Interface | Danh từ, `PascalCase` | `JobPostingService`, `AccessScopeService` |
| Package | chữ thường, không gạch dưới | `service`, `authorization`, `logging` |

Vài lưu ý thêm:

- **Boolean nên đặt tên như 1 câu hỏi đúng/sai**: `isActive`,
  `hasExpired`, `canEdit` - tránh đặt tên mập mờ như `flag`, `status`
  (trừ khi `status` thực sự là 1 enum nhiều giá trị, không phải
  boolean).
- **Tránh viết tắt khó đoán**: `usr`, `jp`, `tmpVal` - trừ các viết tắt
  đã quá phổ biến trong domain (`id`, `dto`, `url`).
- **Tên biến loop cũng nên có nghĩa** khi loop không chỉ là index đơn
  thuần: `for (UserRole role : userRoles)` thay vì `for (UserRole r :
  list)`.

---

## 3. Tên phải phản ánh đúng những gì bên trong

Đây là lỗi hay bị bỏ qua nhất: **tên class/method là gì thì nội dung
bên trong phải đúng như vậy**, không "treo đầu dê bán thịt chó".

- Class tên `XxxMapper` (vd `JobPostingMapper`) chỉ nên chứa logic
  convert qua lại giữa entity/DTO, **không** nhét thêm logic query DB,
  gọi service khác, hay validate nghiệp vụ vào đó.
- Class tên `XxxService`/`XxxServiceImpl` mới là nơi chứa logic nghiệp
  vụ, gọi repository, điều phối luồng xử lý.
- Method tên `findById`/`getById` thì không nên có side-effect (không
  tự tạo mới bản ghi nếu không tìm thấy) - nếu có hành vi
  tạo-nếu-chưa-có, đặt tên rõ ràng như `findOrCreateById`.
- Method tên `validateXxx` thì chỉ nên ném exception hoặc trả
  `boolean`/`void`, không nên âm thầm sửa data.

```java
// BAD - tên là Mapper nhưng lại tự query DB, phá vỡ kỳ vọng của người gọi
public class JobPostingMapper {
    public JobPostingResponseDto toResponseDto(JobPosting entity) {
        Department department = departmentRepository.findById(entity.getDepartmentId())
                .orElseThrow(...); // Mapper không nên gọi repository
        ...
    }
}

// GOOD - Mapper chỉ convert dữ liệu đã có sẵn, không tự đi lấy thêm
public class JobPostingMapper {
    public static JobPostingResponseDto toResponseDto(JobPosting entity, Department department) {
        ...
    }
}
```

Khi review PR, nếu thấy tên class/method không khớp với việc nó đang
làm, ưu tiên đổi tên hoặc tách lại code cho đúng vai trò, thay vì để
nguyên và hy vọng người đọc "hiểu ngầm".

---

## 4. DRY - Don't Repeat Yourself

Tránh copy-paste cùng 1 đoạn logic ở nhiều nơi. Khi thấy 1 đoạn code
lặp lại từ 2 lần trở lên (hoặc rõ ràng sẽ còn lặp lại), tách ra:

- **Cùng 1 class**: tách thành private method dùng chung.
- **Nhiều class trong cùng layer** (vd nhiều `*Service`): tách thành
  method `static`/bean dùng chung, hoặc đưa lên 1 class cha/util nếu
  hợp lý.
- **Logic dùng chung toàn dự án** (vd mask dữ liệu nhạy cảm, resolve
  quyền truy cập): tách thành class/module riêng, như
  `LogMaskUtils`, `AccessScopeService`, `OwnershipAspect` hiện có.

```java
// BAD - lặp lại logic check quyền ở nhiều controller
// JobPostingController
if (!currentUser.hasRole(Role.ADMIN) && !currentUser.hasRole(Role.RECRUITER)) {
    throw new PermissionDeniedException();
}
// UserAdminController
if (!currentUser.hasRole(Role.ADMIN) && !currentUser.hasRole(Role.RECRUITER)) {
    throw new PermissionDeniedException();
}

// GOOD - tách thành 1 chỗ dùng chung (vd annotation + AccessControlService)
@RequiresOwnership
public JobPostingResponseDto update(...) { ... }
```

Không áp dụng DRY quá đà cho những đoạn code **giống nhau tình cờ**
nhưng thuộc 2 nghiệp vụ khác nhau, sẽ tiến hoá khác hướng theo thời
gian - gộp chung trong trường hợp này sẽ tạo ra 1 hàm dùng chung nhưng
đầy `if` để phân biệt trường hợp, khó đọc hơn là để lặp lại 1 chút.

---

## 5. Complexity - độ phức tạp của method

- **Method nên ngắn, làm đúng 1 việc** (Single Responsibility ở cấp độ
  method). Nếu 1 method dài hơn khoảng **40-50 dòng** hoặc phải cuộn
  trang mới đọc hết, cân nhắc tách thành các method nhỏ hơn, mỗi method
  đặt tên rõ ràng cho từng bước.
- **Tránh lồng nhiều `if/else`** (nested if). Ưu tiên **early return**
  để giảm độ sâu lồng nhau, dễ đọc hơn `if/else` lồng nhiều tầng.

  ```java
  // BAD - if/else lồng nhau nhiều tầng
  public void closeJobPosting(Long id, CurrentUser currentUser) {
      JobPosting jobPosting = jobPostingRepository.findById(id).orElse(null);
      if (jobPosting != null) {
          if (jobPosting.getStatus() != JobStatus.CLOSED) {
              if (currentUser.hasRole(Role.ADMIN) || jobPosting.getCreatedBy().equals(currentUser.getId())) {
                  jobPosting.setStatus(JobStatus.CLOSED);
                  jobPostingRepository.save(jobPosting);
              } else {
                  throw new PermissionDeniedException();
              }
          } else {
              throw new BusinessConflictException(ErrorCode.JOB_POSTING_ALREADY_CLOSED);
          }
      } else {
          throw new ResourceNotFoundException(ErrorCode.JOB_POSTING_NOT_FOUND);
      }
  }

  // GOOD - early return, dễ đọc theo từng điều kiện chặn
  public void closeJobPosting(Long id, CurrentUser currentUser) {
      JobPosting jobPosting = jobPostingRepository.findById(id)
              .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.JOB_POSTING_NOT_FOUND));

      if (jobPosting.getStatus() == JobStatus.CLOSED) {
          throw new BusinessConflictException(ErrorCode.JOB_POSTING_ALREADY_CLOSED);
      }
      boolean canClose = currentUser.hasRole(Role.ADMIN) || jobPosting.getCreatedBy().equals(currentUser.getId());
      if (!canClose) {
          throw new PermissionDeniedException();
      }

      jobPosting.setStatus(JobStatus.CLOSED);
      jobPostingRepository.save(jobPosting);
  }
  ```

- **Nhiều nhánh `if/else`/`switch` cùng kiểm tra 1 biến** (vd nhiều
  `if` liên tiếp check `jobStatus`) - cân nhắc dùng `switch`
  (switch expression của Java 21) hoặc map/strategy pattern nếu số
  nhánh nhiều và có xu hướng tăng thêm theo thời gian.
- **Tránh quá nhiều tham số** trong 1 method (thường không quá 4-5).
  Nếu nhiều hơn, cân nhắc gom lại thành 1 DTO/record tham số.

---

## 6. Checklist trước khi commit

- [ ] Đã `Reformat Code` (`Ctrl+Alt+L`) trước khi commit.
- [ ] Không có dòng nào vượt quá 80 ký tự (trừ trường hợp bất khả
      kháng, vd URL/String literal dài).
- [ ] Các đoạn logic khác nhau trong cùng 1 method đã được cách dòng rõ
      ràng, không dính thành 1 cục.
- [ ] Biến là danh từ `camelCase`, hàm là động từ `camelCase`, hằng số
      `UPPER_SNAKE_CASE`, class `PascalCase`.
- [ ] Tên class/method phản ánh đúng nội dung bên trong (Mapper không
      lẫn logic query/service, Service không lẫn logic convert DTO).
- [ ] Không có đoạn logic bị copy-paste ở nhiều nơi - đã tách hàm/class
      dùng chung nếu lặp lại.
- [ ] Method không quá dài, không lồng `if/else` quá sâu - đã cân nhắc
      early return hoặc tách method nhỏ hơn.
- [ ] Không quá nhiều tham số trong 1 method.
