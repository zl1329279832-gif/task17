package com.property.repair.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class DuplicateCheckResponse {

    private boolean duplicate;

    private List<DuplicateOrderInfo> duplicateOrders;

    @Data
    public static class DuplicateOrderInfo {
        private Long id;
        private String orderNo;
        private String title;
        private String status;
        private String createdAt;
    }
}
