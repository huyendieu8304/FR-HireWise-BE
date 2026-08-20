# Quy tắc viết test (JUnit 5) - HireWise-BE

Tài liệu này áp dụng cho mọi test viết trong `com.hirewise.be`. Mục tiêu:
test phải **tự nói lên được nó đang bảo vệ hành vi nào** - đọc tên method
là biết ngay tình huống + kết quả mong đợi, không cần mở phần thân mới
hiểu đang test cái gì.

Liên quan: `CODING_CONVENTION.md` (format/đặt tên áp dụng chung cho cả
test), `GIT_WORKFLOW.md` (test phải pass local trước khi tạo Pull
Request).

Dự án hiện tại **chủ yếu có unit test** cho `authorization/` và
`security/` (chưa có test cho `controller/`/`repository/`) - tài liệu này
mô tả convention **đã áp dụng thực tế** trong các test hiện có, cộng với
đề xuất bổ sung các test level còn thiếu (đánh dấu rõ **[đề xuất]**).

---

## 1. Framework & thư viện

- **JUnit 5 (Jupiter)** - `org.junit.jupiter.api.Test`,
  `@BeforeEach`/`@AfterEach`, `@ExtendWith`.
- **Mockito** qua `MockitoExtension`
  (`@ExtendWith(MockitoExtension.class)`) + `@Mock` field - **không**
  dùng `Mockito.mock(...)` thủ công.
- **AssertJ** cho assertion, KHÔNG dùng `org.junit.jupiter.api.Assertions`
  (`assertEquals`, `assertTrue`...) - toàn bộ test hiện có dùng
  `org.assertj.core.api.Assertions`:
  - `assertThat(actual).isEqualTo(expected)`
  - `assertThatThrownBy(() -> ...).isInstanceOf(XxxException.class)`
  - `assertThatCode(() -> ...).doesNotThrowAnyException()`
- Đã có sẵn trong `pom.xml` (scope `test`) nhưng **[đề xuất] chưa được
  dùng**: `spring-boot-starter-webmvc-test` (MockMvc cho slice test
  Controller), `spring-boot-starter-data-jpa-test` (`@DataJpaTest` cho
  Repository), `spring-boot-starter-security-test`.

---

## 2. Cấu trúc thư mục & class test

- Test nằm ở `src/test/java/com/hirewise/be/...`, **package + tên file
  gương với class được test** (vd `service/JobPostingService.java` ->
  `service/JobPostingServiceTest.java`).
- Tên test class: `<TênClassĐượcTest>Test` (vd `AccessControlServiceImplTest`,
  `OwnershipAspectTest`).
- Class test **không cần** `public` (package-private là đủ, giống các
  file hiện có: `class AccessControlServiceImplTest { ... }`).
- Mỗi test class nên có 1 Javadoc/block comment ngắn ở đầu nói rõ **đang
  test gì, bao phủ trường hợp nào** (giống `AccessControlServiceImplTest`,
  `OwnershipAspectTest` hiện có), đặc biệt quan trọng với test RBAC vì
  logic nhiều lớp.

---

## 3. Quy tắc đặt tên method test

Test hiện tại **không** dùng tiền tố `test` (JUnit 5 không yêu cầu),
KHÔNG theo khuôn cứng `should...When...` - tên method mô tả trực tiếp
**tình huống + kết quả mong đợi**, theo 1 trong 2 dạng đã dùng nhất quán
trong dự án:

1. **Câu mô tả liền mạch, camelCase** (ưu tiên khi tình huống ngắn gọn):

   ```java
   void deniesWhenNoRoleGrantsPermission()
   void allowsWhenPermissionGrantedAndWithinScope()
   void readActionDoesNotRequireCanWrite()
   void unprovisionedAccount_isDenied()
   ```

2. **`<tình huống>_<kết quả mong đợi>`** (ưu tiên khi tình huống dài,
   dùng `_` để tách rõ 2 phần cho dễ đọc):

   ```java
   void ownerMatches_proceeds()
   void ownerMismatch_throwsNotResourceOwner()
   void roleGrantingPermissionWithoutOwnershipRequirement_bypassesOwnershipCheck()
   void blockedAccountWithStillValidJwtAndActiveSession_isDeniedNotProceeded()
   ```

**Quy tắc khi viết test mới**: chọn 1 trong 2 dạng trên tuỳ độ dài tình
huống, LUÔN thể hiện rõ **điều kiện đầu vào** (state, role, dữ liệu biên)
và **kết quả mong đợi** (return giá trị gì / throw exception gì / gọi hay
không gọi 1 dependency nào đó) ngay trong tên method - không đặt tên
chung chung kiểu `test1`, `testCreate`, `testSuccess`.

`@DisplayName` hiện **chưa được dùng** trong dự án - không bắt buộc thêm,
nhưng có thể dùng nếu tên method không đủ diễn đạt bằng tiếng Việt trong
báo cáo test.

---

## 4. Cấu trúc 1 test method - Arrange / Act / Assert

Giống coding convention (cách dòng theo từng bước trong method - xem
`CODING_CONVENTION.md` mục 1.3), mỗi test nên tách rõ 3 khối bằng dòng
trống: chuẩn bị dữ liệu/mock -> gọi hàm cần test -> assert kết quả (+
verify tương tác với mock nếu cần):

```java
@Test
void deniesWhenPermissionGrantedButOutOfScope() {
    // Arrange
    when(rolePermissionCache.permissionsOf("RECRUITER")).thenReturn(Map.of(PermissionCodes.JOB_CREATE, true));
    when(accessScopeService.isWithinScope(any(), any(), eq(true))).thenReturn(false);

    // Act + Assert (dồn chung khi assert chính là bắt exception của chính lời gọi)
    assertThatThrownBy(() -> accessControlService.checkAccess(
            recruiter(1L), PermissionCodes.JOB_CREATE, ResourceContext.department(999L)))
            .isInstanceOf(OutOfScopeException.class);
}
```

Khởi tạo class cần test trong `@BeforeEach` bằng constructor thủ công
(khớp với cách Controller/Service dùng `@AllArgsConstructor` +
`@FieldDefaults` để constructor injection), field dùng chung giữa các
test (constant như id cố định) khai báo `static final` ở đầu class, hàm
dựng dữ liệu lặp lại (vd `recruiter(Long userId)`) tách thành private
helper method trong chính test class.

---

## 5. Test level - dùng level nào cho layer nào

| Level | Công cụ | Dùng cho | Trạng thái trong dự án |
|---|---|---|---|
| **1. Unit test** | JUnit 5 + `MockitoExtension`, mock 100% dependency, KHÔNG load Spring context | `service/*`, `authorization/*`, `mapper/*`, filter/aspect logic thuần (`security/*`) | Đã áp dụng (`authorization`, `security`) |
| **2. Slice test** [đề xuất] | `@WebMvcTest` + `MockMvc` + `@MockitoBean` cho service; `@DataJpaTest` cho Repository | `controller/*` (status code, JSON body, `fieldErrors` khi validation lỗi); `repository/*` có custom `@Query` phức tạp (`JobPostingRepository.search`, đặc biệt CTE đệ quy trong `DepartmentRepository`) | **Chưa có** - nên bổ sung khi thêm Controller/Repository mới có logic đáng test |
| **3. Full integration** [đề xuất] | `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate`/`MockMvc`, DB thật qua **Testcontainers PostgreSQL** (không dùng H2 - dự án dùng cú pháp/dialect đặc thù Postgres, CTE đệ quy) | luồng nghiệp vụ xuyên nhiều layer quan trọng: login (`AuthController` + `AuthService` + rate limit + lockout), toàn bộ pipeline RBAC 4 lớp end-to-end trên 1 API thật | **Chưa có** - ưu tiên bổ sung cho luồng auth và 1-2 API RBAC tiêu biểu trước |

**Quy tắc chọn level khi thêm 1 API nghiệp vụ mới**:

- Luôn viết **unit test cho Service** (bắt buộc, level 1) - bao phủ happy
  path + mọi nhánh lỗi nghiệp vụ (`ResourceNotFoundException`,
  `BusinessConflictException`...) + đúng permission/scope được gọi
  (`verify(accessControlService).checkAccess(...)` với tham số đúng).
- Nếu API có logic authorization mới (permission mới, resource type mới
  cho `@RequiresOwnership`) -> thêm unit test tương ứng trong
  `authorization/` giống `AccessControlServiceImplTest`/
  `OwnershipAspectTest` hiện có.
- Nếu Controller có logic ngoài việc gọi thẳng service (transform param,
  validate thủ công, build header như `Location` ở `create`) -> cân nhắc
  thêm slice test `@WebMvcTest` (level 2).
- Nếu Repository có `@Query`/CTE phức tạp -> thêm `@DataJpaTest` (level
  2) với Testcontainers Postgres.
- Level 3 chỉ dùng cho luồng nghiệp vụ **quan trọng/rủi ro cao**, không
  bắt buộc cho mọi API (chi phí chạy cao, CI hiện tại `mvn -B clean test`
  chạy trên mọi PR vào `dev`/`main` - xem `.github/workflows/ci-test.yml`).

---

## 6. Checklist trước khi commit code có test

- [ ] Test class đặt tên `<ClassĐượcTest>Test`, cùng package với class
      gốc, nằm trong `src/test/java`.
- [ ] Dùng `@ExtendWith(MockitoExtension.class)` + `@Mock`, không tự
      `Mockito.mock(...)`.
- [ ] Assertion dùng AssertJ (`assertThat`/`assertThatThrownBy`/
      `assertThatCode`), không dùng `org.junit.jupiter.api.Assertions`.
- [ ] Tên method test thể hiện rõ tình huống + kết quả mong đợi (mục 3),
      không đặt tên chung chung.
- [ ] Mỗi test tách rõ Arrange/Act/Assert bằng dòng trống.
- [ ] Service mới đã có unit test bao phủ happy path + các nhánh
      exception nghiệp vụ chính + assert đúng permission/scope được gọi
      (nếu có RBAC).
- [ ] Nếu thêm permission/resource type RBAC mới, đã có unit test riêng
      trong `authorization/`.
- [ ] `mvn -B clean test` chạy pass ở local trước khi tạo Pull Request
      (bắt buộc theo `GIT_WORKFLOW.md`).
