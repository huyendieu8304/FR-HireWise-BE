-- UC-09 Other Information: seed 13 default email templates EM-01..EM-13
-- status = ACTIVE, version = 1, pipeline_stage_id = NULL (no stage linked by default).
-- Subjects/bodies use {{Tag}} placeholders per SRS section 5.2.

INSERT INTO email_templates (code, name, subject_template, body_template, version, status)
VALUES
(
    'EM-01',
    'Kich hoat tai khoan noi bo',
    '[HireWise] Kich hoat tai khoan cua ban',
    'Xin chao {{Full_Name}},' || chr(10) || chr(10) ||
    'Tai khoan HireWise cua ban da duoc HR Admin tao voi vai tro {{Role_Name}} tai phong ban {{Department_Name}}.' || chr(10) ||
    'Vui long bam vao lien ket ben duoi de kich hoat tai khoan va dat mat khau dang nhap:' || chr(10) ||
    '{{Activation_Link}} (het han sau 48 gio)' || chr(10) || chr(10) ||
    'Tran trong,' || chr(10) ||
    'Doi ngu HireWise',
    1, 'ACTIVE'
),
(
    'EM-02',
    'Thong bao Job Position cho phe duyet',
    '[HireWise] Yeu cau tuyen dung "{{Job_Title}}" dang cho ban phe duyet',
    'Xin chao {{Manager_Name}},' || chr(10) || chr(10) ||
    'Recruiter {{Recruiter_Name}} vua gui yeu cau tuyen dung vi tri "{{Job_Title}}" (Phong {{Department_Name}}, {{Openings}} chi tieu) de ban xem xet phe duyet.' || chr(10) ||
    'Xem chi tiet va phe duyet tai: {{Job_Approval_Link}}' || chr(10) || chr(10) ||
    'Tran trong,' || chr(10) ||
    'HireWise',
    1, 'ACTIVE'
),
(
    'EM-03',
    'Thong bao ket qua phe duyet Job Position',
    '[HireWise] Yeu cau tuyen dung "{{Job_Title}}" da duoc {{Decision}}',
    'Xin chao {{Recruiter_Name}},' || chr(10) || chr(10) ||
    'Yeu cau tuyen dung "{{Job_Title}}" da duoc {{Manager_Name}} {{Decision}}.' || chr(10) ||
    '{{Reject_Reason_Block}}' || chr(10) ||
    'Xem chi tiet tai: {{Job_Link}}' || chr(10) || chr(10) ||
    'Tran trong,' || chr(10) ||
    'HireWise',
    1, 'ACTIVE'
),
(
    'EM-04',
    'Xac nhan da nhan ho so ung tuyen',
    '[{{Company}}] Da nhan ho so ung tuyen vi tri {{Job_Title}}',
    'Xin chao {{Candidate_Name}},' || chr(10) || chr(10) ||
    'Cam on ban da ung tuyen vi tri {{Job_Title}} tai {{Company}}. Chung toi da nhan duoc ho so cua ban vao luc {{Applied_At}}.' || chr(10) ||
    'Doi ngu tuyen dung se xem xet va phan hoi trong thoi gian som nhat.' || chr(10) || chr(10) ||
    'Tran trong,' || chr(10) ||
    '{{Company}}',
    1, 'ACTIVE'
),
(
    'EM-05',
    'Thu moi phong van (lich co dinh)',
    '[{{Company}}] Thu moi phong van vi tri {{Job_Title}}',
    'Xin chao {{Candidate_Name}},' || chr(10) || chr(10) ||
    'Chung toi tran trong moi ban tham gia phong van vi tri {{Job_Title}} vao luc {{Interview_Date}} {{Interview_Time}} ({{Interview_Mode}}).' || chr(10) ||
    '{{Meeting_Location_Or_Link}}' || chr(10) ||
    'Vui long xac nhan tham du tai: {{Confirm_Link}}' || chr(10) || chr(10) ||
    'Tran trong,' || chr(10) ||
    '{{Recruiter_Name}} - {{Company}}',
    1, 'ACTIVE'
),
(
    'EM-06',
    'Loi moi tu chon lich phong van (Self-service booking)',
    '[{{Company}}] Vui long chon khung gio phong van phu hop',
    'Xin chao {{Candidate_Name}},' || chr(10) || chr(10) ||
    'Ban da vuot qua vong sang loc ho so vi tri {{Job_Title}}. Vui long chon khung gio phong van phu hop nhat tai lien ket duoi day (het han sau {{Expiry_Hours}} gio):' || chr(10) ||
    '{{Booking_Link}}' || chr(10) || chr(10) ||
    'Tran trong,' || chr(10) ||
    '{{Recruiter_Name}} - {{Company}}',
    1, 'ACTIVE'
),
(
    'EM-07',
    'Xac nhan lich phong van da dat',
    '[{{Company}}] Xac nhan lich phong van vi tri {{Job_Title}}',
    'Xin chao {{Candidate_Name}},' || chr(10) || chr(10) ||
    'Lich phong van cua ban da duoc xac nhan vao luc {{Interview_Date}} {{Interview_Time}}.' || chr(10) ||
    '{{Meeting_Location_Or_Link}}' || chr(10) ||
    'Su kien da duoc them vao lich ca nhan cua ban (dinh kem .ics).' || chr(10) || chr(10) ||
    'Tran trong,' || chr(10) ||
    '{{Company}}',
    1, 'ACTIVE'
),
(
    'EM-08',
    'Loi moi hop gui Interviewer (Calendar invite)',
    '[HireWise] Lich phong van: {{Candidate_Name}} - {{Job_Title}}',
    'Xin chao {{Interviewer_Name}},' || chr(10) || chr(10) ||
    'Ban duoc moi tham gia phong van ung vien {{Candidate_Name}} cho vi tri {{Job_Title}} vao luc {{Interview_Date}} {{Interview_Time}}.' || chr(10) ||
    'Ho so ung vien: {{Candidate_Profile_Link}}' || chr(10) ||
    'Bang cham diem: {{Scorecard_Link}}' || chr(10) ||
    '{{Meeting_Location_Or_Link}}' || chr(10) || chr(10) ||
    'Tran trong,' || chr(10) ||
    'HireWise',
    1, 'ACTIVE'
),
(
    'EM-09',
    'Thong bao ket qua - Tu choi ung vien',
    '[{{Company}}] Thong bao ket qua ung tuyen vi tri {{Job_Title}}',
    'Xin chao {{Candidate_Name}},' || chr(10) || chr(10) ||
    'Cam on ban da danh thoi gian ung tuyen va tham gia phong van vi tri {{Job_Title}} tai {{Company}}.' || chr(10) ||
    'Sau khi can nhac ky luong, chung toi rat tiec phai thong bao rang ho so cua ban chua phu hop o thoi diem nay{{Custom_Message_Block}}' || chr(10) ||
    'Chung toi se luu ho so cua ban vao Talent Pool va lien he khi co co hoi phu hop hon.' || chr(10) || chr(10) ||
    'Chuc ban thanh cong,' || chr(10) ||
    '{{Company}}',
    1, 'ACTIVE'
),
(
    'EM-10',
    'Thong bao ket qua dang tin da kenh',
    '[HireWise] Ket qua dang tin "{{Job_Title}}" len cac kenh',
    'Xin chao {{Recruiter_Name}},' || chr(10) || chr(10) ||
    'Tin tuyen dung "{{Job_Title}}" da duoc xu ly tren cac kenh sau:' || chr(10) ||
    '{{Channel_Status_List}}' || chr(10) ||
    'Xem chi tiet tai: {{Job_Link}}' || chr(10) || chr(10) ||
    'Tran trong,' || chr(10) ||
    'HireWise',
    1, 'ACTIVE'
),
(
    'EM-11',
    'Thu moi lam viec (Offer) va yeu cau ky dien tu',
    '[{{Company}}] Thu moi lam viec - Vi tri {{Job_Title}}',
    'Xin chao {{Candidate_Name}},' || chr(10) || chr(10) ||
    'Chuc mung ban da duoc lua chon cho vi tri {{Job_Title}} tai {{Company}}!' || chr(10) ||
    'Vui long xem chi tiet thu moi va hoan tat ky xac nhan dien tu truoc {{Expiry_Date}} tai lien ket bao mat sau:' || chr(10) ||
    '{{Offer_Link}} (yeu cau xac thuc OTP)' || chr(10) || chr(10) ||
    'Tran trong,' || chr(10) ||
    '{{Recruiter_Name}} - {{Company}}',
    1, 'ACTIVE'
),
(
    'EM-12',
    'Xac nhan da ky Offer thanh cong',
    '[{{Company}}] Chao mung ban gia nhap {{Company}}!',
    'Xin chao {{Candidate_Name}},' || chr(10) || chr(10) ||
    'Ban da ky xac nhan thu moi lam viec vi tri {{Job_Title}} thanh cong vao luc {{Signed_At}}.' || chr(10) ||
    'Ban hop dong da ky duoc dinh kem/luu tai: {{Signed_File_Link}}' || chr(10) ||
    'Ngay bat dau lam viec du kien: {{Start_Date}}' || chr(10) || chr(10) ||
    'Rat mong duoc chao don ban,' || chr(10) ||
    '{{Company}}',
    1, 'ACTIVE'
),
(
    'EM-13',
    'Canh bao vi pham SLA',
    '[HireWise] Canh bao: {{n}} ho so vuot SLA tai {{Job_Title}}',
    'Xin chao {{Manager_Name}},' || chr(10) || chr(10) ||
    'He thong phat hien {{n}} ung vien dang vuot nguong SLA tai Stage "{{Stage_Name}}" cua vi tri {{Job_Title}}:' || chr(10) ||
    '{{Breach_List}}' || chr(10) ||
    'Vui long kiem tra va xu ly kip thoi tai: {{Dashboard_Link}}' || chr(10) || chr(10) ||
    'Tran trong,' || chr(10) ||
    'HireWise',
    1, 'ACTIVE'
)
ON CONFLICT (code) DO NOTHING;
