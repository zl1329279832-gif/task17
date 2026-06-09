package com.property.repair.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repair order detail response.
 */
@Data
public class RepairOrderVO {

    private Long id;
    private String orderNo;
    private String title;
    private String description;
    private String problemType;
    private String problemTypeName;
    private Integer urgency;
    private String urgencyName;

    // Location
    private Long communityId;
    private String communityName;
    private Long buildingId;
    private String buildingName;
    private Long unitId;
    private String roomNo;
    private String addressDetail;

    // Parties
    private Long ownerId;
    private String ownerName;
    private String ownerPhone;
    private Long assignedWorkerId;
    private String assignedWorkerName;
    private String assignedWorkerPhone;

    // Status
    private String status;
    private String statusName;
    private String suspendReason;
    private LocalDateTime suspendedAt;
    private Integer totalSuspendedSeconds;
    private Long currentDispatchId;

    // Timestamps
    private LocalDateTime submittedAt;
    private LocalDateTime assignedAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime visitAt;
    private LocalDateTime completedAt;

    // Duplicate info
    private Long parentOrderId;
    private String parentOrderNo;
    private Integer isDuplicate;

    // Related data
    private List<AttachmentVO> attachments;
    private List<ProgressVO> progressTimeline;
    private ReviewVO review;
    private List<ReworkOrderVO> reworkOrders;

    @Data
    public static class AttachmentVO {
        private Long id;
        private String fileName;
        private String fileType;
        private Long fileSize;
        private String fileUrl;
        private String stage;
        private LocalDateTime createdAt;
    }

    @Data
    public static class ProgressVO {
        private Long id;
        private String fromStatus;
        private String toStatus;
        private Long operatorId;
        private String operatorName;
        private String operatorRole;
        private String remark;
        private String evidenceUrls;
        private LocalDateTime createdAt;
    }

    @Data
    public static class ReviewVO {
        private Long id;
        private Integer rating;
        private String content;
        private String reply;
        private LocalDateTime replyAt;
        private LocalDateTime createdAt;
    }

    @Data
    public static class ReworkOrderVO {
        private Long id;
        private String reworkNo;
        private String reason;
        private String description;
        private String status;
        private Long assignedWorkerId;
        private String assignedWorkerName;
        private LocalDateTime completedAt;
        private LocalDateTime createdAt;
    }
}
