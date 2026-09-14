-- EM-12 now attaches the signed Offer PDF to the email instead of linking to
-- it on Cloud Storage: the Drive link exposed where contracts are kept and
-- was not openable by the candidate anyway. The line that carried
-- {{Signed_File_Link}} becomes {{Signed_File_Note}}, which EmailServiceImpl
-- fills with either "attached" or "will follow" wording.
UPDATE email_templates SET
    body_template = replace(
        replace(body_template,
            '<p>Bản hợp đồng đã ký được đính kèm/lưu tại: {{Signed_File_Link}}</p>',
            '<p>{{Signed_File_Note}}</p>'),
        '<p>Ban hop dong da ky duoc dinh kem/luu tai: {{Signed_File_Link}}</p>',
        '<p>{{Signed_File_Note}}</p>'),
    updated_at = now()
WHERE code = 'EM-12'
  AND body_template LIKE '%{{Signed_File_Link}}%';
