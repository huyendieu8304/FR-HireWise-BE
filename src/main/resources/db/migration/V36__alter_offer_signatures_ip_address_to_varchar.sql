-- UC-39 fix: offer_signatures.ip_address was declared INET in V35, which
-- made every signature fail with
--   column "ip_address" is of type inet but expression is of type character varying
-- because Hibernate binds a String and PostgreSQL will not implicitly cast
-- varchar to inet. (The entity's columnDefinition = "inet" only affects
-- schema generation, which Flyway owns here - it never changed the bind.)
--
-- Switching the column to text rather than teaching Hibernate to cast, for
-- two reasons:
--
-- 1. The value comes from the X-Forwarded-For header, i.e. untrusted client
--    input. INET makes PostgreSQL REJECT anything malformed, so a bogus
--    header would abort the whole signing transaction and leave a candidate
--    unable to accept a job offer. This column is audit evidence; it must
--    never be the reason a signature fails.
-- 2. It is never queried as a network address - no subnet matching, no
--    ordering - so INET buys nothing here. user_sessions.ip_address (V6) is
--    already VARCHAR for the same reason.
--
-- 45 characters is the longest possible textual IP (IPv4-mapped IPv6, e.g.
-- 0000:0000:0000:0000:0000:ffff:255.255.255.255). ClientIpResolver validates
-- the value before it ever reaches here, so it cannot overflow.

ALTER TABLE offer_signatures
    ALTER COLUMN ip_address TYPE VARCHAR(45) USING ip_address::text;
