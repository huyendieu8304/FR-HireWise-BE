-- Rewrites every Vietnamese seed string that was originally written without
-- diacritics (V23, V25, V27, V28, V31, V32, V34) into properly accented
-- Vietnamese. The unaccented form was a habit of writing ASCII-safe SQL, not
-- a technical constraint: these files are UTF-8 without BOM, Flyway reads
-- them as UTF-8, and V29/V30 already carry accented Vietnamese comments that
-- survive the build byte-for-byte.
--
-- It matters here because most of this seed data is user-facing: email
-- subjects and bodies sent to candidates, JD text on the public Job Board,
-- rejection labels in the Recruiter UI, and the offer letter itself.
--
-- Done as a new migration rather than an edit to V23/V25/... because those
-- already have a Flyway checksum recorded in flyway_schema_history; editing
-- them in place would fail validation on any database that has run them.
--
-- Every UPDATE is guarded on the row still holding the original unaccented
-- text, so this migration is idempotent and never clobbers a template an HR
-- Admin has already edited through the app (UC-09/UC-10).

-- ---------------------------------------------------------------------------
-- email_templates EM-01..EM-13 (V23), EM-SEC (V27), EM-OTP-OFFER (V34).
-- EM-05 and EM-08 use the newer bodies introduced by V31, not the V23 ones.
-- Guard: every seeded body still starts with the unaccented '<p>Xin chao '.
-- ---------------------------------------------------------------------------

UPDATE email_templates SET
    name = 'Kích hoạt tài khoản nội bộ',
    subject_template = '[HireWise] Kích hoạt tài khoản của bạn',
    body_template =
        '<p>Xin chào {{Full_Name}},</p>' || chr(10) || chr(10) ||
        '<p>Tài khoản HireWise của bạn đã được HR Admin tạo với vai trò {{Role_Name}} tại phòng ban {{Department_Name}}.</p>' || chr(10) || chr(10) ||
        '<p>Vui lòng bấm vào liên kết bên dưới để kích hoạt tài khoản và đặt mật khẩu đăng nhập:<br/>' || chr(10) ||
        '{{Activation_Link}} (hết hạn sau 48 giờ)</p>' || chr(10) || chr(10) ||
        '<p>Trân trọng,<br/>' || chr(10) ||
        'Đội ngũ HireWise</p>',
    updated_at = now()
WHERE code = 'EM-01' AND body_template LIKE '<p>Xin chao %';

UPDATE email_templates SET
    name = 'Thông báo Job Position chờ phê duyệt',
    subject_template = '[HireWise] Yêu cầu tuyển dụng "{{Job_Title}}" đang chờ bạn phê duyệt',
    body_template =
        '<p>Xin chào {{Manager_Name}},</p>' || chr(10) || chr(10) ||
        '<p>Recruiter {{Recruiter_Name}} vừa gửi yêu cầu tuyển dụng vị trí "{{Job_Title}}" (Phòng {{Department_Name}}, {{Openings}} chỉ tiêu) để bạn xem xét phê duyệt.</p>' || chr(10) || chr(10) ||
        '<p>Xem chi tiết và phê duyệt tại: {{Job_Approval_Link}}</p>' || chr(10) || chr(10) ||
        '<p>Trân trọng,<br/>' || chr(10) ||
        'HireWise</p>',
    updated_at = now()
WHERE code = 'EM-02' AND body_template LIKE '<p>Xin chao %';

UPDATE email_templates SET
    name = 'Thông báo kết quả phê duyệt Job Position',
    subject_template = '[HireWise] Yêu cầu tuyển dụng "{{Job_Title}}" đã được {{Decision}}',
    body_template =
        '<p>Xin chào {{Recruiter_Name}},</p>' || chr(10) || chr(10) ||
        '<p>Yêu cầu tuyển dụng "{{Job_Title}}" đã được {{Manager_Name}} {{Decision}}.</p>' || chr(10) || chr(10) ||
        '<p>{{Reject_Reason_Block}}</p>' || chr(10) || chr(10) ||
        '<p>Xem chi tiết tại: {{Job_Link}}</p>' || chr(10) || chr(10) ||
        '<p>Trân trọng,<br/>' || chr(10) ||
        'HireWise</p>',
    updated_at = now()
WHERE code = 'EM-03' AND body_template LIKE '<p>Xin chao %';

UPDATE email_templates SET
    name = 'Xác nhận đã nhận hồ sơ ứng tuyển',
    subject_template = '[{{Company}}] Đã nhận hồ sơ ứng tuyển vị trí {{Job_Title}}',
    body_template =
        '<p>Xin chào {{Candidate_Name}},</p>' || chr(10) || chr(10) ||
        '<p>Cảm ơn bạn đã ứng tuyển vị trí {{Job_Title}} tại {{Company}}. Chúng tôi đã nhận được hồ sơ của bạn vào lúc {{Applied_At}}.</p>' || chr(10) || chr(10) ||
        '<p>Đội ngũ tuyển dụng sẽ xem xét và phản hồi trong thời gian sớm nhất.</p>' || chr(10) || chr(10) ||
        '<p>Trân trọng,<br/>' || chr(10) ||
        '{{Company}}</p>',
    updated_at = now()
WHERE code = 'EM-04' AND body_template LIKE '<p>Xin chao %';

-- EM-05: body follows V31 (meeting location block), not the original V23 body.
UPDATE email_templates SET
    name = 'Thư mời phỏng vấn (lịch cố định)',
    subject_template = '[{{Company}}] Thư mời phỏng vấn vị trí {{Job_Title}}',
    body_template =
        '<p>Xin chào {{Candidate_Name}},</p>' || chr(10) || chr(10) ||
        '<p>Chúng tôi trân trọng mời bạn tham gia phỏng vấn vị trí {{Job_Title}} vào lúc {{Interview_Date}} {{Interview_Time}} ({{Interview_Mode}}).</p>' || chr(10) || chr(10) ||
        '<p><strong>Thông tin cuộc họp:</strong><br/>' || chr(10) ||
        '{{Meeting_Location_Or_Link}}</p>' || chr(10) || chr(10) ||
        '<p>Vui lòng xác nhận tham gia đúng giờ.</p>' || chr(10) || chr(10) ||
        '<p>Trân trọng,<br/>' || chr(10) ||
        '{{Recruiter_Name}} - {{Company}}</p>',
    updated_at = now()
WHERE code = 'EM-05' AND body_template LIKE '<p>Xin chao %';

UPDATE email_templates SET
    name = 'Lời mời tự chọn lịch phỏng vấn (Self-service booking)',
    subject_template = '[{{Company}}] Vui lòng chọn khung giờ phỏng vấn phù hợp',
    body_template =
        '<p>Xin chào {{Candidate_Name}},</p>' || chr(10) || chr(10) ||
        '<p>Bạn đã vượt qua vòng sàng lọc hồ sơ vị trí {{Job_Title}}. Vui lòng chọn khung giờ phỏng vấn phù hợp nhất tại liên kết dưới đây (hết hạn sau {{Expiry_Hours}} giờ):</p>' || chr(10) || chr(10) ||
        '<p>{{Booking_Link}}</p>' || chr(10) || chr(10) ||
        '<p>Trân trọng,<br/>' || chr(10) ||
        '{{Recruiter_Name}} - {{Company}}</p>',
    updated_at = now()
WHERE code = 'EM-06' AND body_template LIKE '<p>Xin chao %';

UPDATE email_templates SET
    name = 'Xác nhận lịch phỏng vấn đã đặt',
    subject_template = '[{{Company}}] Xác nhận lịch phỏng vấn vị trí {{Job_Title}}',
    body_template =
        '<p>Xin chào {{Candidate_Name}},</p>' || chr(10) || chr(10) ||
        '<p>Lịch phỏng vấn của bạn đã được xác nhận vào lúc {{Interview_Date}} {{Interview_Time}}.</p>' || chr(10) || chr(10) ||
        '<p>{{Meeting_Location_Or_Link}}</p>' || chr(10) || chr(10) ||
        '<p>Sự kiện đã được thêm vào lịch cá nhân của bạn (đính kèm .ics).</p>' || chr(10) || chr(10) ||
        '<p>Trân trọng,<br/>' || chr(10) ||
        '{{Company}}</p>',
    updated_at = now()
WHERE code = 'EM-07' AND body_template LIKE '<p>Xin chao %';

-- EM-08: body follows V31 (meeting location block), not the original V23 body.
UPDATE email_templates SET
    name = 'Lời mời họp gửi Interviewer (Calendar invite)',
    subject_template = '[HireWise] Lịch phỏng vấn: {{Candidate_Name}} - {{Job_Title}}',
    body_template =
        '<p>Xin chào {{Interviewer_Name}},</p>' || chr(10) || chr(10) ||
        '<p>Bạn được mời tham gia phỏng vấn ứng viên {{Candidate_Name}} cho vị trí {{Job_Title}} vào lúc {{Interview_Date}} {{Interview_Time}}.</p>' || chr(10) || chr(10) ||
        '<p><strong>Thông tin cuộc họp:</strong><br/>' || chr(10) ||
        '{{Meeting_Location_Or_Link}}</p>' || chr(10) || chr(10) ||
        '<p>Hồ sơ ứng viên: {{Candidate_Profile_Link}}<br/>' || chr(10) ||
        'Bảng chấm điểm: {{Scorecard_Link}}</p>' || chr(10) || chr(10) ||
        '<p>Trân trọng,<br/>' || chr(10) ||
        'HireWise</p>',
    updated_at = now()
WHERE code = 'EM-08' AND body_template LIKE '<p>Xin chao %';

UPDATE email_templates SET
    name = 'Thông báo kết quả - Từ chối ứng viên',
    subject_template = '[{{Company}}] Thông báo kết quả ứng tuyển vị trí {{Job_Title}}',
    body_template =
        '<p>Xin chào {{Candidate_Name}},</p>' || chr(10) || chr(10) ||
        '<p>Cảm ơn bạn đã dành thời gian ứng tuyển và tham gia phỏng vấn vị trí {{Job_Title}} tại {{Company}}.</p>' || chr(10) || chr(10) ||
        '<p>Sau khi cân nhắc kỹ lưỡng, chúng tôi rất tiếc phải thông báo rằng hồ sơ của bạn chưa phù hợp ở thời điểm này{{Custom_Message_Block}}</p>' || chr(10) || chr(10) ||
        '<p>Chúng tôi sẽ lưu hồ sơ của bạn vào Talent Pool và liên hệ khi có cơ hội phù hợp hơn.</p>' || chr(10) || chr(10) ||
        '<p>Chúc bạn thành công,<br/>' || chr(10) ||
        '{{Company}}</p>',
    updated_at = now()
WHERE code = 'EM-09' AND body_template LIKE '<p>Xin chao %';

UPDATE email_templates SET
    name = 'Thông báo kết quả đăng tin đa kênh',
    subject_template = '[HireWise] Kết quả đăng tin "{{Job_Title}}" lên các kênh',
    body_template =
        '<p>Xin chào {{Recruiter_Name}},</p>' || chr(10) || chr(10) ||
        '<p>Tin tuyển dụng "{{Job_Title}}" đã được xử lý trên các kênh sau:</p>' || chr(10) || chr(10) ||
        '<p>{{Channel_Status_List}}</p>' || chr(10) || chr(10) ||
        '<p>Xem chi tiết tại: {{Job_Link}}</p>' || chr(10) || chr(10) ||
        '<p>Trân trọng,<br/>' || chr(10) ||
        'HireWise</p>',
    updated_at = now()
WHERE code = 'EM-10' AND body_template LIKE '<p>Xin chao %';

UPDATE email_templates SET
    name = 'Thư mời làm việc (Offer) và yêu cầu ký điện tử',
    subject_template = '[{{Company}}] Thư mời làm việc - Vị trí {{Job_Title}}',
    body_template =
        '<p>Xin chào {{Candidate_Name}},</p>' || chr(10) || chr(10) ||
        '<p>Chúc mừng bạn đã được lựa chọn cho vị trí {{Job_Title}} tại {{Company}}!</p>' || chr(10) || chr(10) ||
        '<p>Vui lòng xem chi tiết thư mời và hoàn tất ký xác nhận điện tử trước {{Expiry_Date}} tại liên kết bảo mật sau:<br/>' || chr(10) ||
        '{{Offer_Link}} (yêu cầu xác thực OTP)</p>' || chr(10) || chr(10) ||
        '<p>Trân trọng,<br/>' || chr(10) ||
        '{{Recruiter_Name}} - {{Company}}</p>',
    updated_at = now()
WHERE code = 'EM-11' AND body_template LIKE '<p>Xin chao %';

UPDATE email_templates SET
    name = 'Xác nhận đã ký Offer thành công',
    subject_template = '[{{Company}}] Chào mừng bạn gia nhập {{Company}}!',
    body_template =
        '<p>Xin chào {{Candidate_Name}},</p>' || chr(10) || chr(10) ||
        '<p>Bạn đã ký xác nhận thư mời làm việc vị trí {{Job_Title}} thành công vào lúc {{Signed_At}}.</p>' || chr(10) || chr(10) ||
        '<p>Bản hợp đồng đã ký được đính kèm/lưu tại: {{Signed_File_Link}}</p>' || chr(10) || chr(10) ||
        '<p>Ngày bắt đầu làm việc dự kiến: {{Start_Date}}</p>' || chr(10) || chr(10) ||
        '<p>Rất mong được chào đón bạn,<br/>' || chr(10) ||
        '{{Company}}</p>',
    updated_at = now()
WHERE code = 'EM-12' AND body_template LIKE '<p>Xin chao %';

UPDATE email_templates SET
    name = 'Cảnh báo vi phạm SLA',
    subject_template = '[HireWise] Cảnh báo: {{n}} hồ sơ vượt SLA tại {{Job_Title}}',
    body_template =
        '<p>Xin chào {{Manager_Name}},</p>' || chr(10) || chr(10) ||
        '<p>Hệ thống phát hiện {{n}} ứng viên đang vượt ngưỡng SLA tại Stage "{{Stage_Name}}" của vị trí {{Job_Title}}:</p>' || chr(10) || chr(10) ||
        '<p>{{Breach_List}}</p>' || chr(10) || chr(10) ||
        '<p>Vui lòng kiểm tra và xử lý kịp thời tại: {{Dashboard_Link}}</p>' || chr(10) || chr(10) ||
        '<p>Trân trọng,<br/>' || chr(10) ||
        'HireWise</p>',
    updated_at = now()
WHERE code = 'EM-13' AND body_template LIKE '<p>Xin chao %';

UPDATE email_templates SET
    name = 'Cảnh báo đăng nhập thất bại',
    subject_template = '[{{Company}}] Cảnh báo đăng nhập thất bại nhiều lần',
    body_template =
        '<p>Xin chào {{Full_Name}},</p>' || chr(10) || chr(10) ||
        '<p>Hệ thống ghi nhận 5 lần đăng nhập sai liên tiếp vào tài khoản của bạn từ địa chỉ IP {{IP_Address}}. Tài khoản của bạn đã bị tạm khóa trong 15 phút để bảo vệ an toàn.</p>' || chr(10) || chr(10) ||
        '<p>Nếu đây không phải là bạn, vui lòng đổi mật khẩu ngay sau khi tài khoản được mở lại và liên hệ quản trị viên.</p>' || chr(10) || chr(10) ||
        '<p>Trân trọng,<br/>' || chr(10) ||
        '{{Company}}</p>',
    updated_at = now()
WHERE code = 'EM-SEC' AND body_template LIKE '<p>Xin chao %';

UPDATE email_templates SET
    name = 'Mã xác thực xem thư mời làm việc',
    subject_template = '[{{Company}}] Mã xác thực thư mời làm việc: {{Otp_Code}}',
    body_template =
        '<p>Xin chào {{Candidate_Name}},</p>' || chr(10) || chr(10) ||
        '<p>Mã xác thực để xem thư mời làm việc vị trí "{{Job_Title}}" của bạn là:</p>' || chr(10) ||
        '<p style="font-size:24px;font-weight:bold;letter-spacing:4px;">{{Otp_Code}}</p>' || chr(10) || chr(10) ||
        '<p>Mã có hiệu lực trong {{Otp_Ttl_Minutes}} phút. Vui lòng không chia sẻ mã này với bất kỳ ai.</p>' || chr(10) || chr(10) ||
        '<p>Nếu bạn không yêu cầu mã này, hãy bỏ qua email và liên hệ Recruiter phụ trách.</p>' || chr(10) || chr(10) ||
        '<p>Trân trọng,<br/>' || chr(10) ||
        '{{Company}}</p>',
    updated_at = now()
WHERE code = 'EM-OTP-OFFER' AND body_template LIKE '<p>Xin chao %';

-- ---------------------------------------------------------------------------
-- job_positions: the four sample Published jobs from V25. Titles stay English
-- (they are the natural key V25 guards on); description/requirements/benefits
-- and the two Vietnamese city names get their diacritics back. Matched on the
-- fixed seed UUIDs so a real job that happens to share a title is untouched.
-- ---------------------------------------------------------------------------

UPDATE job_positions SET
    description = 'Thiết kế và phát triển hệ thống backend cho nền tảng ATS, xử lý lưu lượng lớn với kiến trúc microservices. Làm việc trực tiếp với team Product để hiện thực hóa các tính năng AI matching, pipeline tuyển dụng và tích hợp bên thứ ba.',
    requirements = E'5+ năm kinh nghiệm Java/Spring Boot hoặc tương đương\n- Kinh nghiệm thiết kế hệ thống phân tán, microservices\n- Hiểu biết về message queue (Kafka/RabbitMQ) là lợi thế',
    benefits = E'Lương tháng 13, thưởng theo KPI\n- Bảo hiểm sức khỏe cao cấp cho nhân viên và gia đình\n- Chế độ làm việc hybrid linh hoạt',
    location = 'Hồ Chí Minh',
    updated_at = now()
WHERE id = '11111111-1111-4111-8111-111111111111'::uuid
  AND description LIKE 'Thiet ke va phat trien he thong backend%';

UPDATE job_positions SET
    description = 'Thiết kế trải nghiệm và giao diện cho các sản phẩm tuyển dụng, phối hợp chặt chẽ với Product và Engineering từ ý tưởng đến bàn giao.',
    requirements = E'3+ năm kinh nghiệm Product/UX Design\n- Thành thạo Figma và hệ thống thiết kế (design system)\n- Có portfolio thể hiện tư duy giải quyết vấn đề',
    benefits = E'Thỏa thuận theo năng lực\n- Được tham gia định hình sản phẩm từ giai đoạn đầu\n- Ngân sách học tập/công cụ thiết kế riêng',
    location = 'Hà Nội',
    updated_at = now()
WHERE id = '22222222-2222-4222-8222-222222222222'::uuid
  AND description LIKE 'Thiet ke trai nghiem va giao dien%';

UPDATE job_positions SET
    description = 'Đồng hành cùng các trưởng phòng ban về chiến lược nhân sự, tuyển dụng và phát triển đội ngũ.',
    requirements = E'3+ năm kinh nghiệm HRBP hoặc Tuyển dụng\n- Kỹ năng giao tiếp, tư vấn tốt\n- Am hiểu luật lao động Việt Nam',
    benefits = E'Lương tháng 13, thưởng theo KPI\n- Bảo hiểm sức khỏe cao cấp cho nhân viên và gia đình\n- Chế độ làm việc hybrid linh hoạt',
    location = 'Hồ Chí Minh',
    updated_at = now()
WHERE id = '33333333-3333-4333-8333-333333333333'::uuid
  AND description LIKE 'Dong hanh cung cac truong phong ban%';

-- location stays 'Remote' - already language-neutral.
UPDATE job_positions SET
    description = 'Hỗ trợ phân tích dữ liệu tuyển dụng, xây dựng báo cáo và dashboard phục vụ ra quyết định.',
    requirements = E'Đang học năm cuối/mới tốt nghiệp ngành CNTT, Toán, Thống kê...\n- Biết SQL cơ bản; biết Python/R là lợi thế\n- Ham học hỏi, cẩn thận, chủ động',
    benefits = E'Được đào tạo trực tiếp từ Data team\n- Cơ hội chuyển đổi nhân viên chính thức\n- Làm việc Remote linh hoạt',
    updated_at = now()
WHERE id = '44444444-4444-4444-8444-444444444444'::uuid
  AND description LIKE 'Ho tro phan tich du lieu tuyen dung%';

-- ---------------------------------------------------------------------------
-- rejection_reasons (V28): only the display label changes; code and category
-- are the stable keys the Recruiter UI and reporting join on (BR-REJ-01).
-- ---------------------------------------------------------------------------

UPDATE rejection_reasons AS rr SET
    label = v.new_label,
    updated_at = now()
FROM (VALUES
    ('TECHNICAL_GAP', 'Ky nang chuyen mon chua phu hop',      'Kỹ năng chuyên môn chưa phù hợp'),
    ('CULTURE_GAP',   'Chua phu hop van hoa cong ty',         'Chưa phù hợp văn hóa công ty'),
    ('SALARY_GAP',    'Muc luong ky vong khong phu hop',      'Mức lương kỳ vọng không phù hợp'),
    ('DUPLICATE',     'Ho so trung lap voi ung vien khac',    'Hồ sơ trùng lặp với ứng viên khác'),
    ('WITHDRAWN',     'Ung vien da rut ho so / khong phan hoi', 'Ứng viên đã rút hồ sơ / không phản hồi'),
    ('OTHER',         'Ly do khac',                           'Lý do khác')
) AS v(code, old_label, new_label)
WHERE rr.code = v.code AND rr.label = v.old_label;

-- ---------------------------------------------------------------------------
-- offer_templates (V32): the company-wide default offer letter. This is the
-- document a candidate reads and e-signs in UC-38, so it is the seed row that
-- most needs correct Vietnamese.
-- ---------------------------------------------------------------------------

UPDATE offer_templates SET
    name = 'Thư mời làm việc chuẩn',
    body_template =
        '<h2>THƯ MỜI LÀM VIỆC</h2>' ||
        '<p>Kính gửi <strong>{{Candidate_Name}}</strong>,</p>' ||
        '<p>{{Company}} trân trọng mời bạn gia nhập đội ngũ ở vị trí ' ||
        '<strong>{{Job_Title}}</strong> với các điều khoản sau:</p>' ||
        '<ul>' ||
        '<li>Mức lương chính thức: <strong>{{Salary}}</strong></li>' ||
        '<li>Tỷ lệ hưởng lương thử việc: <strong>{{Probation_Rate}}</strong></li>' ||
        '<li>Ngày nhận việc dự kiến: <strong>{{Start_Date}}</strong></li>' ||
        '<li>Hạn trả lời thư mời: <strong>{{Expiry_Date}}</strong></li>' ||
        '</ul>' ||
        '<p>Vui lòng xác nhận bằng chữ ký điện tử trước hạn trả lời nói trên. ' ||
        'Sau thời điểm đó thư mời sẽ tự động hết hiệu lực.</p>' ||
        '<p>Trân trọng,<br/>{{Recruiter_Name}} - {{Company}}</p>',
    updated_at = now()
WHERE name = 'Thu moi lam viec chuan' AND version = 1;
