package com.property.repair.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * Admin issues parts against an approved request.
 */
@Data
public class IssuePartsRequest {

    @NotNull
    private List<IssueItem> items;

    @Data
    public static class IssueItem {
        @NotNull
        private Long requestItemId;

        /** Actually issued quantity (may be less than requested) */
        @NotNull
        private Integer issuedQty;
    }
}
