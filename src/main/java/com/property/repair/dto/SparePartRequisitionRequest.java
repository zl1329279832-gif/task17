package com.property.repair.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class SparePartRequisitionRequest {

    @NotNull
    private Long orderId;

    /** Non-null when requesting parts during rework */
    private Long reworkOrderId;

    @NotEmpty
    private List<Item> items;

    @Data
    public static class Item {
        @NotNull
        private Long partId;

        @NotNull
        private Integer quantity;
    }
}
