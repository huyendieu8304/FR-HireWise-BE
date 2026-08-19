# Quy tắc viết comment - HireWise-BE

Tài liệu này áp dụng cho mọi code viết trong `com.hirewise.be`. Mục tiêu:
comment phải giúp người đọc (chính bạn 3 tháng sau, hoặc đồng đội mới vào
dự án) hiểu **vì sao** code được viết như vậy, chứ không phải lặp lại
**code đang làm gì** - điều mà chỉ cần đọc code là thấy.

Tài liệu này nói về comment trong code (giải thích ý định, logic nghiệp
vụ). Còn log runtime (ghi lại "ai đã làm gì lúc nào") xem
`LOGGING_CONVENTION.md` - hai tài liệu bổ sung cho nhau, không thay thế
nhau.

---

## 1. Nguyên tắc chung

1. **Luôn viết comment bằng tiếng Anh**, kể cả khi trao đổi trong team
   dùng tiếng Việt. Code và comment là tài sản chung, có thể có người
   ngoài team hoặc đối tác đọc.

2. **Sửa code thì phải sửa comment đi kèm.** Comment sai/lỗi thời còn
   nguy hiểm hơn không có comment, vì nó đánh lừa người đọc. Khi review
   PR, nếu logic thay đổi mà comment cũ vẫn còn nguyên, coi đó là 1 lỗi
   cần sửa trước khi merge.

3. **Không comment những điều hiển nhiên, không diễn dịch lại code
   thành lời.** Nếu chỉ đọc lại tên biến/hàm là hiểu ngay, không cần
   comment.

   ```java
   // BAD - chỉ lặp lại code bằng lời
   int retryCount = 0; // initialize retry count to 0
   user.setActive(true); // set user active to true

   // OK - không cần comment gì cả, tên biến đã đủ rõ
   int retryCount = 0;
   user.setActive(true);
   ```

4. **Bắt buộc comment flow xử lý quan trọng trong các method phức tạp**
   (nhiều bước, nhiều nhánh rẽ, xử lý nghiệp vụ không hiển nhiên nhìn
   code là hiểu ngay). Comment phải giải thích **logic nghiệp vụ - vì
   sao làm vậy**, không phải mô tả lại từng dòng code đang làm gì.

   ```java
   // BAD - mô tả lại code, không giải thích lý do
   // loop through user roles
   for (UserRole role : userRoles) {
       // if role is expired
       if (role.getExpiredAt().isBefore(now)) {
           continue;
       }
       ...
   }

   // GOOD - giải thích lý do nghiệp vụ
   // A role assigned with a past expiredAt is a leftover from a temporary
   // access grant (e.g. project-based access) that should no longer be
   // in effect, even though it hasn't been cleaned up from the DB yet.
   for (UserRole role : userRoles) {
       if (role.getExpiredAt().isBefore(now)) {
           continue;
       }
       ...
   }
   ```

5. **Không để lại code chết (dead code) dưới dạng comment.** Nếu code
   không dùng nữa, xoá hẳn - lịch sử đã có trong git, không cần giữ lại
   bằng cách comment out. Ngoại lệ: có thể tạm comment out kèm tag
   `// FIXME:`/`// TODO:` giải thích rõ lý do và thời hạn dọn dẹp, không
   để tồn tại vĩnh viễn qua nhiều PR.

---

## 2. Ba loại comment

| | Javadoc Comment (`/** ... */`) | Block Comment (`/* ... */`) | Inline Comment (`// ...`) |
|---|---|---|---|
| **Dùng để** | Tạo tài liệu (doc) cho class, method, field dùng chung | Giải thích 1 đoạn code phức tạp, hoặc ghi chú chung cho cả khối | Giải thích ngắn gọn 1 dòng/nhóm dòng code |
| **Vị trí** | Ngay trên khai báo class/method/field | Ngay trên đoạn code cần giải thích | Cuối dòng code, hoặc dòng riêng ngay trên dòng cần giải thích |
| **Thẻ đặc biệt** | `@param`, `@return`, `@throws`, `@author`, `@version` (xem 2.1) | Không dùng thẻ Javadoc | Có thể dùng task tag (`TODO`, `FIXME`..., xem mục 3) |
| **Khi nào bắt buộc** | Class/method **dùng chung** giữa nhiều nơi: mọi `public` class/method trong `service`, `controller`, `repository`, `util`/`logging`, `security`, `authorization`; các `custom exception` | Chỉ khi thực sự cần thiết - 1 thuật toán/luồng xử lý nhiều bước không thể diễn giải gọn bằng vài dòng inline | Xem mục 2.2 - các vị trí bắt buộc |

### 2.1. Javadoc - các thẻ đặc biệt

- `@param` - mô tả từng parameter của method (bỏ qua nếu tên tham số đã
  tự giải thích và method chỉ có 1-2 tham số đơn giản).
- `@return` - mô tả giá trị trả về, đặc biệt khi trả về `null`/`Optional`
  hay list rỗng có ý nghĩa nghiệp vụ riêng (vd "trả về rỗng nếu user
  chưa được gán role nào").
- `@throws` / `@exception` - liệt kê các exception nghiệp vụ mà method
  có thể ném ra và điều kiện gây ra nó. Không cần liệt kê
  `RuntimeException` chung chung, chỉ cần các exception custom của dự án
  (`ResourceNotFoundException`, `PermissionDeniedException`...).
- `@author` - người viết ban đầu (tuỳ chọn; dự án dùng `git blame` để
  tra cứu tác giả, chỉ thêm `@author` cho các class lõi/dùng chung nếu
  team muốn ghi rõ đầu mối liên hệ).
- `@version` - chỉ dùng cho các class có version tài liệu/API rõ ràng
  (vd DTO expose ra ngoài cho bên thứ 3), không bắt buộc cho code nội
  bộ.

```java
/**
 * Resolves the effective access scope (which departments/records a user
 * can act on) by combining the user's roles and any scope explicitly
 * assigned to them.
 * <p>
 * Used by {@code OwnershipAspect} to authorize requests annotated with
 * {@code @RequiresOwnership} before the controller method runs.
 *
 * @param userId id of the user whose scope is being resolved
 * @return the resolved scope; never {@code null}, but may contain an
 *         empty department list if the user has no scoped access
 * @throws ResourceNotFoundException if no user exists with {@code userId}
 */
public UserAccessScope resolveScope(String userId) {
    ...
}
```

### 2.2. Inline comment - các vị trí BẮT BUỘC phải có

- **Trước mỗi regex**: giải thích regex đó check cái gì, không bắt
  người đọc phải tự dịch regex trong đầu.

  ```java
  // Matches a Vietnamese phone number: optional +84/0 prefix, followed
  // by 9-10 digits (e.g. "0912345678", "+84912345678").
  private static final Pattern PHONE_PATTERN =
          Pattern.compile("^(\\+84|0)\\d{9,10}$");
  ```

- **Các vòng điều kiện/vòng lặp có ý nghĩa nghiệp vụ**: trường hợp nào
  thì nhảy vào nhánh này, vòng lặp này đang xử lý gì cho nghiệp vụ nào.

  ```java
  // Recruiters only see job postings within their own department;
  // admins bypass this restriction and see all postings.
  if (currentUser.hasRole(Role.RECRUITER) && !currentUser.hasRole(Role.ADMIN)) {
      spec = spec.and(JobPostingSpecs.inDepartment(currentUser.getDepartmentId()));
  }
  ```

- **Bất kỳ chỗ nào code không tự nói lên được "tại sao"**: một quyết
  định kỹ thuật không hiển nhiên (vd chọn thuật toán, workaround cho
  bug thư viện, đánh đổi hiệu năng...).

Nguyên tắc chốt: **comment sao cho người khác đọc sẽ hiểu được flow xử
lý nghiệp vụ của bạn**, không cần đọc lại cả method mới hiểu logic đang
làm gì.

---

## 3. Task tag / Comment annotation

Dùng để đánh dấu, dễ tìm lại bằng full-text search (`TODO:`, `FIXME:`...)
trong IDE. Format khuyến nghị: `// TAG: mô tả ngắn gọn` (có thể thêm tên
người phụ trách nếu cần theo dõi, vd `// TODO(hai): ...`).

| Tag | Ý nghĩa | Khi nào dùng |
|---|---|---|
| `TODO` | Việc cần làm trong tương lai | Tính năng chưa hoàn thiện, cần bổ sung sau |
| `FIXME` | Đoạn code có bug, chạy sai | Khi phát hiện lỗi cần sửa nhưng chưa sửa ngay được |
| `BUG` | Ghi chú bug đã biết | Khi muốn đánh dấu lại 1 lỗi cụ thể, thường kèm link ticket/issue |
| `HACK` | Cách làm tạm thời, chưa chuẩn | Khi viết workaround để code chạy được (vd né bug của thư viện) |
| `NOTE` / `INFO` | Giải thích, chú thích quan trọng | Khi muốn người đọc hiểu lý do chọn giải pháp này thay vì cách khác |
| `XXX` | Đoạn code quan trọng/nhạy cảm | Khi cần cảnh báo người đọc chú ý kỹ trước khi sửa (vd liên quan tới bảo mật, tiền, dữ liệu nhạy cảm) |
| `REVIEW` | Cần người khác kiểm tra kỹ | Khi không chắc chắn về tính đúng đắn, cần thêm 1 cặp mắt khác trước khi merge |
| `OPTIMIZE` | Cần tối ưu hiệu năng | Khi code chạy được nhưng còn chậm/chưa tối ưu, chưa có thời gian xử lý ngay |

```java
// TODO(hai): support pagination once the recruiter dashboard needs it
// FIXME: this throws NPE when department is null - see BUG-142
// HACK: Keycloak admin client returns 204 with an empty body on update,
// so we re-fetch the user right after instead of trusting the response.
// XXX: role cache is shared across requests - do not mutate the
// returned Set in place, always copy it first.
```

Không lạm dụng task tag để né việc phải sửa code ngay khi có thể sửa
được trong cùng PR - tag chỉ dành cho việc thực sự phải để lại cho sau.

---

## 4. Những điều KHÔNG nên làm

- Không viết comment mô tả lại từng dòng code (`i++; // increase i by 1`).
- Không để comment/Javadoc sai lệch với code hiện tại (xem mục 1.2).
- Không giữ code chết dưới dạng comment vô thời hạn.
- Không viết Javadoc rỗng/copy-paste chỉ để "cho có" (vd `@param userId
  the user id` không thêm thông tin gì so với tên tham số).
- Không dùng comment để giải thích code viết tệ - nếu phải viết cả đoạn
  comment dài để giải thích 1 đoạn code khó hiểu, ưu tiên refactor lại
  code cho rõ ràng hơn trước, comment chỉ nên giải thích phần **logic
  nghiệp vụ** không thể tự hiện rõ qua tên biến/hàm.

---

## 5. Checklist trước khi commit code có thêm comment

- [ ] Comment viết bằng tiếng Anh.
- [ ] Sửa logic thì đã cập nhật lại comment/Javadoc liên quan, không để
      comment cũ mô tả sai code mới.
- [ ] Không có comment chỉ lặp lại code bằng lời.
- [ ] Class/method dùng chung (`service`, `controller`, `repository`,
      `util`, `security`, `authorization`, custom exception) đã có
      Javadoc với `@param`/`@return`/`@throws` khi cần thiết.
- [ ] Method phức tạp (nhiều bước/nhiều nhánh nghiệp vụ) đã có comment
      giải thích flow xử lý, không chỉ mô tả lại code.
- [ ] Mọi regex đều có comment giải thích ngay phía trên.
- [ ] Các nhánh if/else, vòng lặp mang ý nghĩa nghiệp vụ đã được giải
      thích rõ trường hợp nào nhảy vào, xử lý gì.
- [ ] Không còn code chết dưới dạng comment (trừ khi có `TODO`/`FIXME`
      kèm lý do rõ ràng).
- [ ] Task tag (nếu có) dùng đúng loại, có mô tả ngắn gọn kèm theo.
