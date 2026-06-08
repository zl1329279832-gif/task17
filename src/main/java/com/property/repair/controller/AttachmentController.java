package com.property.repair.controller;

import com.property.repair.common.Result;
import com.property.repair.entity.Attachment;
import com.property.repair.security.SecurityUtils;
import com.property.repair.service.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    /**
     * Upload a file (can be done before order creation).
     */
    @PostMapping("/upload")
    public Result<Attachment> upload(
            @RequestParam(required = false) Long orderId,
            @RequestParam String stage,
            @RequestParam("file") MultipartFile file) {
        Long uploaderId = SecurityUtils.getCurrentUserId();
        Attachment attachment = attachmentService.upload(
                orderId != null ? orderId : 0L, file, stage, uploaderId);
        return Result.ok(attachment);
    }

    /**
     * Get all attachments for an order.
     */
    @GetMapping("/order/{orderId}")
    public Result<List<Attachment>> getByOrder(@PathVariable Long orderId) {
        return Result.ok(attachmentService.getByOrderId(orderId));
    }

    /**
     * Get attachments for a specific stage.
     */
    @GetMapping("/order/{orderId}/stage/{stage}")
    public Result<List<Attachment>> getByStage(@PathVariable Long orderId,
                                                @PathVariable String stage) {
        return Result.ok(attachmentService.getByOrderIdAndStage(orderId, stage));
    }

    /**
     * Delete an attachment.
     */
    @DeleteMapping("/{attachmentId}")
    public Result<Void> deleteAttachment(@PathVariable Long attachmentId) {
        Long userId = SecurityUtils.getCurrentUserId();
        attachmentService.deleteAttachment(attachmentId, userId);
        return Result.ok();
    }
}
