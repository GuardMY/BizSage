package com.bizsage.api.intelligence;

import java.time.LocalDateTime;

public record ReviewTicket(
    long id,
    long intelligenceId,
    String reviewStatus,
    String verdict,
    String reviewer,
    String reason,
    String sourceId,
    double weight,
    String regionId,
    String industryId,
    LocalDateTime createTime,
    LocalDateTime updateTime) {
}
