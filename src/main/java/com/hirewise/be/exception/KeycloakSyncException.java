package com.hirewise.be.exception;

import org.springframework.http.HttpStatus;

/**
 * Loi khi dong bo du lieu sang Keycloak that bai (vd gan role that qua
 * Admin API - xem KeycloakAdminClient#assignRealmRole). Khac voi cac loi
 * nghiep vu thong thuong (404/409/403 - do CHINH app quyet dinh), day la
 * loi phu thuoc vao he thong ben ngoai (Keycloak khong phan hoi, chua cau
 * hinh admin client, hoac role chua ton tai ben Keycloak) - dung 502 Bad
 * Gateway de phan biet ro "loi cua chinh app" voi "loi tu upstream Identity
 * Provider", giup FE/QA khong nham day la bug nghiep vu.
 */
public class KeycloakSyncException extends BaseException {
    public KeycloakSyncException(ErrorCode errorCode, Object... args) {
        super(errorCode, HttpStatus.BAD_GATEWAY, args);
    }
}
