-- Follow-up to V50: that migration only matched the HTML seed wording, but
-- some databases hold EM-12 as plain text (no <p> tags), so the line with
-- {{Signed_File_Link}} survived and rendered blank. Replace the whole line
-- carrying the old placeholder, whatever its wording or markup.
UPDATE email_templates SET
    body_template = regexp_replace(
        regexp_replace(body_template,
            '<p>[^<]*\{\{Signed_File_Link\}\}[^<]*</p>',
            '<p>{{Signed_File_Note}}</p>', 'g'),
        '[^\n<>]*\{\{Signed_File_Link\}\}[^\n<>]*',
        '{{Signed_File_Note}}', 'g'),
    updated_at = now()
WHERE code = 'EM-12'
  AND body_template LIKE '%{{Signed_File_Link}}%';
