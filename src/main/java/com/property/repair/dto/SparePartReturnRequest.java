package com.property.repair.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class SparePartReturnRequest {

    @NotEmpty
    private List<Item> items;

    @Data
    public static class Item {
        private Long partId;
        private Integer quantity;
    }
}
