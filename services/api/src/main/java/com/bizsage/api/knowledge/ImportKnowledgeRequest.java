package com.bizsage.api.knowledge;

import jakarta.validation.constraints.NotBlank;

public record ImportKnowledgeRequest(
    @NotBlank String title,
    @NotBlank String content,
    @NotBlank String industryId,
    @NotBlank String regionId,
    @NotBlank String linkId,
    @NotBlank String sourceId) {
}
