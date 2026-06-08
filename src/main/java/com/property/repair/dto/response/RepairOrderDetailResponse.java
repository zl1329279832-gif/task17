package com.property.repair.dto.response;

import com.property.repair.common.enums.OrderStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class RepairOrderDetailResponse {

    private Long id;
    private String orderNo;
    private String title;
    private String description;
    private Integer urgency;
    private OrderStatus status;
    private String statusDescription;
    private Long ownerId;
    private String ownerName;
    private String ownerPhone;
    private Long communityId;
    private String communityName;
    private Long buildingId;
    private String buildingName;
    private String unitNumber;
    private Long categoryId;
    private String categoryName;
    private Long currentWorkerId;
    private String workerName;
    private String workerPhone;
    private LocalDateTime expectedTime;
    private LocalDateTime assignedAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime confirmedAt;
    private Integer reworkCount;
    private String duplicateGroup;
    private List<AttachmentInfo> attachments;
    private List<ProgressInfo> progressList;
    private EvaluationInfo evaluation;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    public static class AttachmentInfo {
        private Long id;
        private String fileName;
        private String filePath;
        private String fileType;
        private String usageType;
        private LocalDateTime createdAt;
    }

    @Data
    public static class ProgressInfo {
        private Long id;
        private String fromStatus;
        private String toStatus;
        private Long operatorId;
        private String operatorName;
        private String remark;
        private LocalDateTime createdAt;
    }

    @Data
    public static class EvaluationInfo {
        private Long id;
        private Integer score;
        private Integer attitudeScore;
        private Integer qualityScore;
        private Integer speedScore;
        private String comment;
        private LocalDateTime createdAt;
    }
}
