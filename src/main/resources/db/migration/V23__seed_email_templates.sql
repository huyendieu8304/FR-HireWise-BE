-- UC-09 Other Information: seed 13 default email templates EM-01..EM-13
-- status = ACTIVE, version = 1, pipeline_stage_id = NULL (no stage linked by default).
-- Subjects/bodies use {{Tag}} placeholders per SRS section 5.2 and are formatted as clean HTML.

INSERT INTO email_templates (code, name, subject_template, body_template, version, status)
VALUES
(
    'EM-01',
    'Kich hoat tai khoan noi bo',
    '[HireWise] Kich hoat tai khoan cua ban',
    '<p>Xin chao {{Full_Name}},</p>' || chr(10) || chr(10) ||
    '<p>Tai khoan HireWise cua ban da duoc HR Admin tao voi vai tro {{Role_Name}} tai phong ban {{Department_Name}}.</p>' || chr(10) || chr(10) ||
    '<p>Vui long bam vao lien ket ben duoi de kich hoat tai khoan va dat mat khau dang nhap:<br/>' || chr(10) ||
    '{{Activation_Link}} (het han sau 48 gio)</p>' || chr(10) || chr(10) ||
    '<p>Tran trong,<br/>' || chr(10) ||
    'Doi ngu HireWise</p>',
    1, 'ACTIVE'
),
(
    'EM-02',
    'Thong bao Job Position cho phe duyet',
    '[HireWise] Yeu cau tuyen dung "{{Job_Title}}" dang cho ban phe duyet',
    '<p>Xin chao {{Manager_Name}},</p>' || chr(10) || chr(10) ||
    '<p>Recruiter {{Recruiter_Name}} vua gui yeu cau tuyen dung vi tri "{{Job_Title}}" (Phong {{Department_Name}}, {{Openings}} chi tieu) de ban xem xet phe duyet.</p>' || chr(10) || chr(10) ||
    '<p>Xem chi tiet va phe duyet tai: {{Job_Approval_Link}}</p>' || chr(10) || chr(10) ||
    '<p>Tran trong,<br/>' || chr(10) ||
    'HireWise</p>',
    1, 'ACTIVE'
),
(
    'EM-03',
    'Thong bao ket qua phe duyet Job Position',
    '[HireWise] Yeu cau tuyen dung "{{Job_Title}}" da duoc {{Decision}}',
    '<p>Xin chao {{Recruiter_Name}},</p>' || chr(10) || chr(10) ||
    '<p>Yeu cau tuyen dung "{{Job_Title}}" da duoc {{Manager_Name}} {{Decision}}.</p>' || chr(10) || chr(10) ||
    '<p>{{Reject_Reason_Block}}</p>' || chr(10) || chr(10) ||
    '<p>Xem chi tiet tai: {{Job_Link}}</p>' || chr(10) || chr(10) ||
    '<p>Tran trong,<br/>' || chr(10) ||
    'HireWise</p>',
    1, 'ACTIVE'
),
(
    'EM-04',
    'Xac nhan da nhan ho so ung tuyen',
    '[{{Company}}] Da nhan ho so ung tuyen vi tri {{Job_Title}}',
    '<p>Xin chao {{Candidate_Name}},</p>' || chr(10) || chr(10) ||
    '<p>Cam on ban da ung tuyen vi tri {{Job_Title}} tai {{Company}}. Chung toi da nhan duoc ho so cua ban vao luc {{Applied_At}}.</p>' || chr(10) || chr(10) ||
    '<p>Doi ngu tuyen dung se xem xet va phan hoi trong thoi gian som nhat.</p>' || chr(10) || chr(10) ||
    '<p>Tran trong,<br/>' || chr(10) ||
    '{{Company}}</p>',
    1, 'ACTIVE'
),
(
    'EM-05',
    'Thu moi phong van (lich co dinh)',
    '[{{Company}}] Thu moi phong van vi tri {{Job_Title}}',
    '<p>Xin chao {{Candidate_Name}},</p>' || chr(10) || chr(10) ||
    '<p>Chung toi tran trong moi ban tham gia phong van vi tri {{Job_Title}} vao luc {{Interview_Date}} {{Interview_Time}} ({{Interview_Mode}}).</p>' || chr(10) || chr(10) ||
    '<p>{{Meeting_Location_Or_Link}}</p>' || chr(10) || chr(10) ||
    '<p>Vui long xac nhan tham du tai: {{Confirm_Link}}</p>' || chr(10) || chr(10) ||
    '<p>Tran trong,<br/>' || chr(10) ||
    '{{Recruiter_Name}} - {{Company}}</p>',
    1, 'ACTIVE'
),
(
    'EM-06',
    'Loi moi tu chon lich phong van (Self-service booking)',
    '[{{Company}}] Vui long chon khung gio phong van phu hop',
    '<p>Xin chao {{Candidate_Name}},</p>' || chr(10) || chr(10) ||
    '<p>Ban da vuot qua vong sang loc ho so vi tri {{Job_Title}}. Vui long chon khung gio phong van phu hop nhat tai lien ket duoi day (het han sau {{Expiry_Hours}} gio):</p>' || chr(10) || chr(10) ||
    '<p>{{Booking_Link}}</p>' || chr(10) || chr(10) ||
    '<p>Tran trong,<br/>' || chr(10) ||
    '{{Recruiter_Name}} - {{Company}}</p>',
    1, 'ACTIVE'
),
(
    'EM-07',
    'Xac nhan lich phong van da dat',
    '[{{Company}}] Xac nhan lich phong van vi tri {{Job_Title}}',
    '<p>Xin chao {{Candidate_Name}},</p>' || chr(10) || chr(10) ||
    '<p>Lich phong van cua ban da duoc xac nhan vao luc {{Interview_Date}} {{Interview_Time}}.</p>' || chr(10) || chr(10) ||
    '<p>{{Meeting_Location_Or_Link}}</p>' || chr(10) || chr(10) ||
    '<p>Su kien da duoc them vao lich ca nhan cua ban (dinh kem .ics).</p>' || chr(10) || chr(10) ||
    '<p>Tran trong,<br/>' || chr(10) ||
    '{{Company}}</p>',
    1, 'ACTIVE'
),
(
    'EM-08',
    'Loi moi hop gui Interviewer (Calendar invite)',
    '[HireWise] Lich phong van: {{Candidate_Name}} - {{Job_Title}}',
    '<p>Xin chao {{Interviewer_Name}},</p>' || chr(10) || chr(10) ||
    '<p>Ban duoc moi tham gia phong van ung vien {{Candidate_Name}} cho vi tri {{Job_Title}} vao luc {{Interview_Date}} {{Interview_Time}}.</p>' || chr(10) || chr(10) ||
    '<p>Ho so ung vien: {{Candidate_Profile_Link}}<br/>' || chr(10) ||
    'Bang cham diem: {{Scorecard_Link}}</p>' || chr(10) || chr(10) ||
    '<p>{{Meeting_Location_Or_Link}}</p>' || chr(10) || chr(10) ||
    '<p>Tran trong,<br/>' || chr(10) ||
    'HireWise</p>',
    1, 'ACTIVE'
),
(
    'EM-09',
    'Thong bao ket qua - Tu choi ung vien',
    '[{{Company}}] Thong bao ket qua ung tuyen vi tri {{Job_Title}}',
    '<p>Xin chao {{Candidate_Name}},</p>' || chr(10) || chr(10) ||
    '<p>Cam on ban da danh thoi gian ung tuyen va tham gia phong van vi tri {{Job_Title}} tai {{Company}}.</p>' || chr(10) || chr(10) ||
    '<p>Sau khi can nhac ky luong, chung toi rat tiec phai thong bao rang ho so cua ban chua phu hop o thoi diem nay{{Custom_Message_Block}}</p>' || chr(10) || chr(10) ||
    '<p>Chung toi se luu ho so cua ban vao Talent Pool va lien he khi co co hoi phu hop hon.</p>' || chr(10) || chr(10) ||
    '<p>Chuc ban thanh cong,<br/>' || chr(10) ||
    '{{Company}}</p>',
    1, 'ACTIVE'
),
(
    'EM-10',
    'Thong bao ket qua dang tin da kenh',
    '[HireWise] Ket qua dang tin "{{Job_Title}}" len cac kenh',
    '<p>Xin chao {{Recruiter_Name}},</p>' || chr(10) || chr(10) ||
    '<p>Tin tuyen dung "{{Job_Title}}" da duoc xu ly tren cac kenh sau:</p>' || chr(10) || chr(10) ||
    '<p>{{Channel_Status_List}}</p>' || chr(10) || chr(10) ||
    '<p>Xem chi tiet tai: {{Job_Link}}</p>' || chr(10) || chr(10) ||
    '<p>Tran trong,<br/>' || chr(10) ||
    'HireWise</p>',
    1, 'ACTIVE'
),
(
    'EM-11',
    'Thu moi lam viec (Offer) va yeu cau ky dien tu',
    '[{{Company}}] Thu moi lam viec - Vi tri {{Job_Title}}',
    '<p>Xin chao {{Candidate_Name}},</p>' || chr(10) || chr(10) ||
    '<p>Chuc mung ban da duoc lua chon cho vi tri {{Job_Title}} tai {{Company}}!</p>' || chr(10) || chr(10) ||
    '<p>Vui long xem chi tiet thu moi va hoan tat ky xac nhan dien tu truoc {{Expiry_Date}} tai lien ket bao mat sau:<br/>' || chr(10) ||
    '{{Offer_Link}} (yeu cau xac thuc OTP)</p>' || chr(10) || chr(10) ||
    '<p>Tran trong,<br/>' || chr(10) ||
    '{{Recruiter_Name}} - {{Company}}</p>',
    1, 'ACTIVE'
),
(
    'EM-12',
    'Xac nhan da ky Offer thanh cong',
    '[{{Company}}] Chao mung ban gia nhap {{Company}}!',
    '<p>Xin chao {{Candidate_Name}},</p>' || chr(10) || chr(10) ||
    '<p>Ban da ky xac nhan thu moi lam viec vi tri {{Job_Title}} thanh cong vao luc {{Signed_At}}.</p>' || chr(10) || chr(10) ||
    '<p>Ban hop dong da ky duoc dinh kem/luu tai: {{Signed_File_Link}}</p>' || chr(10) || chr(10) ||
    '<p>Ngay bat dau lam viec du kien: {{Start_Date}}</p>' || chr(10) || chr(10) ||
    '<p>Rat mong duoc chao don ban,<br/>' || chr(10) ||
    '{{Company}}</p>',
    1, 'ACTIVE'
),
(
    'EM-13',
    'Canh bao vi pham SLA',
    '[HireWise] Canh bao: {{n}} ho so vuot SLA tai {{Job_Title}}',
    '<p>Xin chao {{Manager_Name}},</p>' || chr(10) || chr(10) ||
    '<p>He thong phat hien {{n}} ung vien dang vuot nguong SLA tai Stage "{{Stage_Name}}" cua vi tri {{Job_Title}}:</p>' || chr(10) || chr(10) ||
    '<p>{{Breach_List}}</p>' || chr(10) || chr(10) ||
    '<p>Vui long kiem tra va xu ly kip thoi tai: {{Dashboard_Link}}</p>' || chr(10) || chr(10) ||
    '<p>Tran trong,<br/>' || chr(10) ||
    'HireWise</p>',
    1, 'ACTIVE'
)
ON CONFLICT (code) DO NOTHING;