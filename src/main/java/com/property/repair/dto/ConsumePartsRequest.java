package com.property.repair.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * Worker records actual consumption of parts during repair.
 */
@Data
public class ConsumePartsRequest {

    @NotNull
    private List<ConsumeItem> items;

    @Data
    public static class ConsumeItem {
        @NotNull
        private Long requestItemId;

        @NotNull
        private Integer consumedQty;
    }
}
