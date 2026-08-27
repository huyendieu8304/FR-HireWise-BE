-- Seed EM-SEC for security lockout notifications
INSERT INTO email_templates (code, name, subject_template, body_template, version, status)
VALUES
(
    'EM-SEC',
    'Canh bao dang nhap that bai',
    '[{{Company}}] Canh bao dang nhap that bai nhieu lan',
    '<p>Xin chao {{Full_Name}},</p>' || chr(10) || chr(10) ||
    '<p>He thong ghi nhan 5 lan dang nhap sai lien tiep vao tai khoan cua ban tu dia chi IP {{IP_Address}}. Tai khoan cua ban da bi tam khoa trong 15 phut de bao ve an toan.</p>' || chr(10) || chr(10) ||
    '<p>Neu day khong phai la ban, vui long doi mat khau ngay sau khi tai khoan duoc mo lai va lien he quan tri vien.</p>' || chr(10) || chr(10) ||
    '<p>Tran trong,<br/>' || chr(10) ||
    '{{Company}}</p>',
    1, 'ACTIVE'
)
ON CONFLICT (code) DO NOTHING;
