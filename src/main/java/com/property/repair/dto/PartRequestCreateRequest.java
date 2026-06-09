package com.property.repair.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * Worker creates a part request for a repair order.
 */
@Data
public class PartRequestCreateRequest {

    @NotEmpty(message = "At least one part item is required")
    @Valid
    private List<PartItemRequest> items;

    private String remark;

    @Data
    public static class PartItemRequest {
        @NotNull(message = "Part ID is required")
        private Long partId;

        @NotNull(message = "Quantity is required")
        private Integer quantity;

        /** Whether this part is critical — if unavailable, order enters WAITING_PARTS */
        private boolean critical = true;
    }
}
