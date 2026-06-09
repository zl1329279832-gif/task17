package com.property.repair.dto;

import lombok.Data;

import java.util.List;

/**
 * Spare part recommendation response for a repair order.
 */
@Data
public class PartRecommendationVO {

    private Long orderId;
    private String problemType;
    private Long buildingId;
    private List<RecommendedPart> recommendedParts;

    @Data
    public static class RecommendedPart {
        private Long partId;
        private String partCode;
        private String partName;
        private String specification;
        private String unit;
        /** Historical usage count for this building + problem type */
        private int historicalUsage;
        /** Current available stock at the location */
        private int availableStock;
        /** Recommended quantity based on history */
        private int recommendedQty;
        /** Whether stock is sufficient */
        private boolean stockSufficient;
    }
}
