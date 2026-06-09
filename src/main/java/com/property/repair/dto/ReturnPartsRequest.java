package com.property.repair.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * Worker returns unused parts after repair.
 */
@Data
public class ReturnPartsRequest {

    @NotNull
    private List<ReturnItem> items;

    @Data
    public static class ReturnItem {
        @NotNull
        private Long requestItemId;

        @NotNull
        private Integer returnedQty;
    }
}
