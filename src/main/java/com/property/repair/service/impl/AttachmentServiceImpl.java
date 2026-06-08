package com.property.repair.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.property.repair.entity.Attachment;
import com.property.repair.exception.BusinessException;
import com.property.repair.mapper.AttachmentMapper;
import com.property.repair.service.AttachmentService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentServiceImpl extends ServiceImpl<AttachmentMapper, Attachment>
        implements AttachmentService {

    @Value("${repair.upload.base-path:./uploads}")
    private String uploadBasePath;

    @Value("${repair.upload.allowed-types:jpg,jpeg,png,gif,mp4,avi,pdf}")
    private String allowedTypes;

    @Override
    public Attachment upload(Long orderId, MultipartFile file, String stage, Long uploaderId) {
        // Validate file
        if (file == null || file.isEmpty()) {
            throw new BusinessException("File is empty");
        }

        String originalName = file.getOriginalFilename();
        String extension = originalName != null && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf(".") + 1).toLowerCase()
                : "";

        if (!Arrays.asList(allowedTypes.split(",")).contains(extension)) {
            throw new BusinessException("File type not allowed: " + extension);
        }

        // Generate storage path: uploads/yyyy/MM/dd/uuid.ext
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String storedName = UUID.randomUUID() + "." + extension;
        Path dirPath = Paths.get(uploadBasePath, datePath);
        Path filePath = dirPath.resolve(storedName);

        try {
            Files.createDirectories(dirPath);
            file.transferTo(filePath.toFile());
        } catch (IOException e) {
            log.error("Failed to upload file: {}", originalName, e);
            throw new BusinessException("File upload failed");
        }

        // Create attachment record
        Attachment attachment = new Attachment();
        attachment.setOrderId(orderId);
        attachment.setFileName(originalName);
        attachment.setFileType(extension);
        attachment.setFileSize(file.getSize());
        attachment.setFileUrl("/uploads/" + datePath + "/" + storedName);
        attachment.setStage(stage);
        attachment.setUploaderId(uploaderId);
        save(attachment);

        return attachment;
    }

    @Override
    public List<Attachment> getByOrderId(Long orderId) {
        return list(new LambdaQueryWrapper<Attachment>()
                .eq(Attachment::getOrderId, orderId)
                .orderByDesc(Attachment::getCreatedAt));
    }

    @Override
    public List<Attachment> getByOrderIdAndStage(Long orderId, String stage) {
        return list(new LambdaQueryWrapper<Attachment>()
                .eq(Attachment::getOrderId, orderId)
                .eq(Attachment::getStage, stage)
                .orderByDesc(Attachment::getCreatedAt));
    }

    @Override
    public void linkAttachments(List<Long> attachmentIds, Long orderId) {
        if (attachmentIds == null || attachmentIds.isEmpty()) return;
        for (Long id : attachmentIds) {
            Attachment attachment = getById(id);
            if (attachment != null) {
                attachment.setOrderId(orderId);
                updateById(attachment);
            }
        }
    }

    @Override
    public void deleteAttachment(Long attachmentId, Long userId) {
        Attachment attachment = getById(attachmentId);
        if (attachment == null) {
            throw new BusinessException("Attachment not found");
        }
        // Only uploader or admin can delete
        if (!attachment.getUploaderId().equals(userId)) {
            throw new BusinessException(403, "Not authorized to delete this attachment");
        }
        removeById(attachmentId);
    }
}
