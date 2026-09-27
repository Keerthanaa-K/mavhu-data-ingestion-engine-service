package com.mavhu.ingestion.dto;

/**
 * @param managerUserId the reviewing user; must have the MANAGER role and belong to the file's customer
 * @param comment       optional when approving, required when rejecting
 */
public record ReviewRequest(Integer managerUserId, String comment) {
}
