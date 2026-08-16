-- =======================================================================
--
--                ALREADY RUN, NO NEED TO RUN AGAIN
--
-- =======================================================================

-- Chạy 1 LẦN DUY NHẤT trên Supabase Postgres (SQL Editor trên Supabase
-- Dashboard, hoặc psql) trước khi start Keycloak lần đầu.
-- Mục đích: tạo 1 schema riêng cho Keycloak, tách biệt hoàn toàn khỏi
-- schema "public" mà app Spring Boot (HireWise-BE) đang dùng cho business
-- data, để 2 hệ thống không đụng bảng của nhau trong cùng 1 database.

-- 1. Tạo schema riêng cho Keycloak
CREATE SCHEMA IF NOT EXISTS keycloak;

-- 2. (Khuyến nghị) Tạo 1 role riêng chỉ có quyền trên schema "keycloak",
--    thay vì dùng thẳng user "postgres" (superuser) cho Keycloak.
--    Bỏ qua bước này nếu bạn chấp nhận dùng chung user postgres.
--
CREATE ROLE keycloak_svc LOGIN PASSWORD 'keycloak_service_account';
GRANT USAGE, CREATE ON SCHEMA keycloak TO keycloak_svc;
ALTER DEFAULT PRIVILEGES IN SCHEMA keycloak GRANT ALL ON TABLES TO keycloak_svc;
ALTER ROLE keycloak_svc SET search_path = keycloak;

-- 3. Kiểm tra lại
-- SELECT schema_name FROM information_schema.schemata WHERE schema_name = 'keycloak';

-- Sau bước này, Keycloak container (xem docker-compose.yml) sẽ tự tạo
-- toàn bộ bảng bên trong schema "keycloak" qua Liquibase migration khi
-- khởi động lần đầu. Bạn KHÔNG cần tạo bảng thủ công.
