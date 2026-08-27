package com.hirewise.be.domain;

/** Lifecycle status of a {@link StoredFile} on its Cloud Storage provider (LV-24). */
public enum FileStatus {
    ACTIVE,
    ARCHIVED,
    DELETED
}
