-- V30: Update interview invitation email templates (EM-05 and EM-08) to explicitly show meeting location/link
UPDATE email_templates
SET body_template = '<p>Xin chao {{Candidate_Name}},</p>' || chr(10) || chr(10) ||
    '<p>Chung toi tran trong moi ban tham gia phong van vi tri {{Job_Title}} vao luc {{Interview_Date}} {{Interview_Time}} ({{Interview_Mode}}).</p>' || chr(10) || chr(10) ||
    '<p><strong>Thong tin cuoc hop:</strong><br/>' || chr(10) ||
    '{{Meeting_Location_Or_Link}}</p>' || chr(10) || chr(10) ||
    '<p>Vui long xac nhan tham gia dung gio.</p>' || chr(10) || chr(10) ||
    '<p>Tran trong,<br/>' || chr(10) ||
    '{{Recruiter_Name}} - {{Company}}</p>',
    updated_at = NOW()
WHERE code = 'EM-05';

UPDATE email_templates
SET body_template = '<p>Xin chao {{Interviewer_Name}},</p>' || chr(10) || chr(10) ||
    '<p>Ban duoc moi tham gia phong van ung vien {{Candidate_Name}} cho vi tri {{Job_Title}} vao luc {{Interview_Date}} {{Interview_Time}}.</p>' || chr(10) || chr(10) ||
    '<p><strong>Thong tin cuoc hop:</strong><br/>' || chr(10) ||
    '{{Meeting_Location_Or_Link}}</p>' || chr(10) || chr(10) ||
    '<p>Ho so ung vien: {{Candidate_Profile_Link}}<br/>' || chr(10) ||
    'Bang cham diem: {{Scorecard_Link}}</p>' || chr(10) || chr(10) ||
    '<p>Tran trong,<br/>' || chr(10) ||
    'HireWise</p>',
    updated_at = NOW()
WHERE code = 'EM-08';
