package com.property.repair.common.constants;

public class RepairConstants {

    private RepairConstants() {
    }

    /**
     * Order number prefix
     */
    public static final String ORDER_NO_PREFIX = "RO";

    /**
     * Maximum rework count
     */
    public static final int MAX_REWORK_COUNT = 3;

    /**
     * Maximum attachment count per order
     */
    public static final int MAX_ATTACHMENT_COUNT = 10;

    /**
     * Maximum description length
     */
    public static final int MAX_DESCRIPTION_LENGTH = 2000;

    /**
     * Default page size
     */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /**
     * Maximum page size
     */
    public static final int MAX_PAGE_SIZE = 100;

    /**
     * Minimum evaluation score
     */
    public static final int MIN_EVALUATION_SCORE = 1;

    /**
     * Maximum evaluation score
     */
    public static final int MAX_EVALUATION_SCORE = 5;
}
