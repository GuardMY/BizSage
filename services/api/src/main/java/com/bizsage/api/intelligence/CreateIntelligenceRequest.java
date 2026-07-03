package com.bizsage.api.intelligence;

import jakarta.validation.constraints.NotBlank;

public record CreateIntelligenceRequest(
    @NotBlank String title,
    @NotBlank String content,
    String url,
    @NotBlank String industryId,
    @NotBlank String regionId,
    @NotBlank String linkId,
    @NotBlank String sourceId) {
}
