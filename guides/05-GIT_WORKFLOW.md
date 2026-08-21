# Git Workflow

Tài liệu quy ước sử dụng Git cho project **HireWise ATS**. Áp dụng cho toàn bộ thành viên team (Back-end & Front-end).

> Tài liệu gốc: `gitworkflow.pdf`. Bản này giữ nguyên nội dung gốc và bổ sung thêm phần cheat-sheet lệnh, checklist PR, giải quyết conflict để tiện tra cứu khi code. Có thể tiếp tục chỉnh sửa/bổ sung khi team thống nhất quy tắc mới.

## Mục lục

- [1. Quy tắc chung](#1-quy-tắc-chung)
- [2. Branching](#2-branching)
- [3. Workflow](#3-workflow)
- [4. Commit](#4-commit)
- [5. Pull Request Checklist](#5-pull-request-checklist)
- [6. Xử lý conflict](#6-xử-lý-conflict)
- [7. Git command cheat-sheet](#7-git-command-cheat-sheet)

## 1. Quy tắc chung

- **KHÔNG** commit thông tin nhạy cảm:
  - secret key
  - environment variables
  - cấu hình local của từng người (`.env`, IDE config, ...)
- **KHÔNG** commit 1 cục (1 commit chứa quá nhiều thay đổi không liên quan tới nhau).
- **PHẢI** commit trước khi:
  - `checkout`
  - `merge`
  - `rebase`
  - `pull`

  > (không commit là dễ bay màu code đấy)

- **PHẢI** check xem đang ở nhánh nào trước khi:
  - `merge`
  - `rebase`
- **PHẢI** `fetch` và `pull` remote repo của nhánh hiện tại về trước khi push code trong nhánh đó ở local lên remote, để tránh conflict.

## 2. Branching

Tất cả các nhánh thông thường sẽ được checkout ra từ `dev`. Checkout nhánh theo đúng chức năng đang làm.

| type | ý nghĩa |
|---|---|
| `main` | production-ready code |
| `dev` | integration branch |
| `feature/*` | new features (tương ứng với use case của mình) |
| `refactor/*` | refactor code |
| `bugfix/*` | fix bug |
| `hotfix/*` | urgent production fixes, sửa lỗi khẩn cấp trên production |

**Quy ước đặt tên nhánh:**

```
{type}/{tên-cụ-thể-từng-nhánh-lowercase}
```

Ví dụ:

```
feature/uc001-register-volunteer-account
feature/uc002-login-as-volunteer
bugfix/uc010-wrong-role-permission
hotfix/fix-payment-crash
```

### Sơ đồ minh họa luồng nhánh

```
main      ●───────────────────────────────────●(v1.0)──●(hotfix)──●
           \                                  /                  ↑
dev         ●───●───────●───●───●───────●────●──────────────────●
             \        ↑ /   ↑ \   ↑          ↑
feature/uc001 ●──●──● (PR)  |  \  |          |
feature/uc002 ●─────●───────┘   \ |          |
bugfix/abc                       ●──●──●(PR)─┘
```

> Nhìn hình chi tiết hơn ở slide gốc — ý chính: mọi nhánh feature/bugfix/refactor tách ra từ `dev`, chạy xong tạo PR merge ngược lại `dev`; `dev` ổn định thì tạo PR merge lên `main` để release; `hotfix` tách trực tiếp từ `main` khi cần sửa khẩn cấp trên production, sau đó merge lại cả `main` và `dev`.

## 3. Workflow

- **Không** commit trực tiếp vào `main` hoặc `dev` từ nhánh khác.
- Nhánh `main` và `dev` đã được bảo vệ (protected branch). Muốn merge code vào 2 nhánh này **phải tạo Pull Request**, assign reviewer
- Mọi thay đổi đều phải đi qua Pull Request 
- Code merge vào `main` chỉ được đến từ nhánh `dev` (không merge thẳng feature → main).
- Code review và CI checks là bắt buộc trước khi merge.
- Nhánh nào đã merge vào `dev` thì phải **dừng lại** (không được commit tiếp trên nhánh đó nữa). Về nguyên tắc nên xoá nhánh, nhưng để phòng khi cần trình bày lại với giảng viên, project này giữ lại các nhánh đã merge.
- Người tạo Pull Request **phải**:
  1. Tự resolve conflict trước, bằng cách merge ngược nhánh đích (`dev`/`main`) về nhánh mình đang code.
  2. Chạy test pass ở local hết các test case liên quan trước khi tạo Pull Request.

## 4. Commit

- Small, focused commits per change — mỗi commit chỉ nên chứa một thay đổi rõ ràng, dễ review, dễ revert.
- Viết commit message rõ ràng, ngắn gọn:
  - Độ dài: dưới 70 ký tự là đẹp.
  - Nội dung nên trả lời **what and why?**, ví dụ:
    - Nên viết: `fix: login page error caused by wrong api call`
    - Không nên viết: `fixed login page error`
  - Tóm lại: viết sao để người đọc hiểu được cái gì đã thay đổi trong commit này mà không cần mở diff.
- Bắt đầu commit message bằng 1 trong các prefix sau để thể hiện loại thay đổi (commit type):

| Prefix | Ý nghĩa                                                                                             | Ví dụ |
|---|-----------------------------------------------------------------------------------------------------|---|
| `feat` | Thêm 1 tính năng mới vào source thường là chốt lại sau khi xong khung của 1 feature                 | `feat: register volunteer account` |
| `add` | Thêm mới thành phần (class, method, component, integration...) có ảnh hưởng đáng kể đến source code | `add: mail service`<br>`add: add Redis cache integration` |
| `wip` | (work in progress) Thay đổi chưa xong, chưa thuộc loại nào khác, hoặc muốn lưu tạm làm checkpoint   | `wip: set up handle unauthenticated exception`<br>`wip: initial implementation of search feature`<br>`wip: refactor auth flow (not finished)` |
| `fix` | Sửa 1 bug/issue                                                                                     | `fix: authorized wrong role api/v1/events/apply`<br>`fix: resolve null pointer in order processing`<br>`fix: correct date calculation for subscriptions` |
| `docs` | Cập nhật tài liệu trong code                                                                        | `docs: update README with setup instructions`<br>`docs: add API usage examples`<br>`docs: improve Swagger annotations` |
| `style` | Cập nhật format/style code, không đổi chức năng (spacing, indentation...)                           | `style: format code in VolunteerService.class`<br>`style: fix naming conventions and spacing` |
| `refactor` | Thay đổi cấu trúc code, không fix bug và không thêm tính năng mới                                   | `refactor: extract user validation logic into service`<br>`refactor: rename variables and split large method` |
| `perf` | Cải thiện hiệu năng / hiệu quả code                                                                 | `perf: optimize SQL query by adding index`<br>`perf: reduce API response time by caching result` |
| `test` | Thêm test mới hoặc cải thiện test case hiện có                                                      | `test: add unit tests for order service`<br>`test: improve edge case coverage for payment flow` |
| `chore` | Cập nhật build script, tool, hoặc công việc phụ trợ khác                                            | `chore: update dependencies to latest versions`<br>`chore: adjust CI pipeline and build scripts` |


## 5. Pull Request Checklist

- [ ] Đã pull/rebase code mới nhất từ nhánh đích (`dev` hoặc `main`) về nhánh của mình.
- [ ] Đã tự resolve hết conflict.
- [ ] Đã chạy test local, tất cả test case pass.
- [ ] Không có secret/API key/`.env` bị commit nhầm.
- [ ] Commit message tuân theo quy ước ở mục [4. Commit](#4-commit).
- [ ] Đã gán đúng reviewer (Diệu cho BE, Nguyệt Anh cho FE) và đủ ít nhất 2 approve.
- [ ] CI check (build/test) pass trước khi merge.
- [ ] Sau khi merge vào `dev`, dừng commit tiếp trên nhánh feature/bugfix đó.

## 6. Xử lý conflict

Trước khi tạo Pull Request, người tạo PR tự merge ngược nhánh đích về nhánh của mình để resolve conflict trước:

```bash
# đang ở nhánh feature/uc001-xxx, muốn merge dev mới nhất về nhánh mình
git checkout feature/uc001-xxx
git fetch origin
git merge origin/dev
# resolve conflict thủ công nếu có, sau đó
git add .
git commit -m "fix: resolve merge conflict with dev"
git push origin feature/uc001-xxx
```

Sau khi không còn conflict và test pass local, mới tạo Pull Request lên `dev`.

## 7. Git command cheat-sheet

Một số lệnh hay dùng theo đúng workflow ở trên (bổ sung tham khảo):

```bash
# tạo nhánh mới từ dev
git checkout dev
git pull origin dev
git checkout -b feature/uc003-apply-job

# commit nhỏ, đúng chuẩn message
git add <file-đã-thay-đổi>
git commit -m "feat: add apply job button"

# luôn fetch + pull trước khi push để tránh conflict
git fetch origin
git pull origin feature/uc003-apply-job
git push origin feature/uc003-apply-job

# kiểm tra đang ở nhánh nào trước khi merge/rebase
git branch --show-current

# hotfix khẩn cấp từ main
git checkout main
git pull origin main
git checkout -b hotfix/fix-payment-crash
# ... fix xong ...
git push origin hotfix/fix-payment-crash
# tạo PR merge vào main, sau đó merge/cherry-pick ngược lại dev
```