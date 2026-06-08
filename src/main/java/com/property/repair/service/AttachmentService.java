package com.property.repair.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.property.repair.entity.Attachment;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface AttachmentService extends IService<Attachment> {

    /**
     * Upload a file and create attachment record.
     */
    Attachment upload(Long orderId, MultipartFile file, String stage, Long uploaderId);

    /**
     * Get all attachments for an order.
     */
    List<Attachment> getByOrderId(Long orderId);

    /**
     * Get attachments for a specific stage.
     */
    List<Attachment> getByOrderIdAndStage(Long orderId, String stage);

    /**
     * Batch link pre-uploaded attachments to an order.
     */
    void linkAttachments(List<Long> attachmentIds, Long orderId);

    /**
     * Soft delete an attachment.
     */
    void deleteAttachment(Long attachmentId, Long userId);
}
