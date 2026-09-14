package com.hirewise.be.service;

/**
 * One file attached to an outgoing email, e.g. the signed Offer PDF on EM-12.
 *
 * @param fileName name shown to the recipient
 * @param mimeType content type of {@code content}
 * @param content  the raw bytes
 */
public record EmailAttachment(String fileName, String mimeType, byte[] content) {
}
