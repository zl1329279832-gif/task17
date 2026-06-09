package com.property.repair.dto;

import lombok.Data;

@Data
public class SparePartRecommendVO {

    private Long partId;
    private String partNo;
    private String name;
    private String specification;
    private String unit;
    private Integer isCritical;

    /** Available stock in the order's community */
    private Integer availableStock;

    /** Whether stock is sufficient (quantity > 0) */
    private Boolean stockSufficient;

    /** Historical consumption count for this part in similar orders */
    private Integer historicalConsumption;

    /** Recommendation reason */
    private String recommendReason;
}
