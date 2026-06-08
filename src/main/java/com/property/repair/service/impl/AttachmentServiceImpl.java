package com.property.repair.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.property.repair.common.exception.BusinessException;
import com.property.repair.common.result.Result;
import com.property.repair.entity.Attachment;
import com.property.repair.mapper.AttachmentMapper;
import com.property.repair.security.LoginUser;
import com.property.repair.security.SecurityUtils;
import com.property.repair.service.AttachmentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class AttachmentServiceImpl implements AttachmentService {

    @Value("${attachment.upload-dir:uploads}")
    private String uploadDir;

    private final AttachmentMapper attachmentMapper;

    public AttachmentServiceImpl(AttachmentMapper attachmentMapper) {
        this.attachmentMapper = attachmentMapper;
    }

    @Override
    public Result<Attachment> upload(MultipartFile file, Long orderId, String usageType) {
        LoginUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new BusinessException(401, "用户未登录");
        }

        if (file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String uniqueName = UUID.randomUUID().toString().replace("-", "") + extension;
        String relativePath = dateDir + File.separator + uniqueName;

        File destDir = new File(uploadDir + File.separator + dateDir);
        if (!destDir.exists()) {
            destDir.mkdirs();
        }

        File destFile = new File(uploadDir + File.separator + relativePath);
        try {
            file.transferTo(destFile);
        } catch (IOException e) {
            throw new BusinessException("文件上传失败: " + e.getMessage());
        }

        Attachment attachment = new Attachment();
        attachment.setOrderId(orderId);
        attachment.setUploaderId(currentUser.getUserId());
        attachment.setFileName(originalFilename);
        attachment.setFilePath(relativePath);
        attachment.setFileSize(file.getSize());
        attachment.setFileType(file.getContentType());
        attachment.setUsageType(usageType);
        attachmentMapper.insert(attachment);

        return Result.success(attachment);
    }

    @Override
    public Result<List<Attachment>> getByOrderId(Long orderId) {
        LambdaQueryWrapper<Attachment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Attachment::getOrderId, orderId)
                .orderByAsc(Attachment::getCreatedAt);
        List<Attachment> attachments = attachmentMapper.selectList(wrapper);
        return Result.success(attachments);
    }

    @Override
    public Result<?> delete(Long id) {
        LoginUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new BusinessException(401, "用户未登录");
        }

        Attachment attachment = attachmentMapper.selectById(id);
        if (attachment == null) {
            throw new BusinessException("附件不存在");
        }

        if (!attachment.getUploaderId().equals(currentUser.getUserId())) {
            throw new BusinessException(403, "只能删除自己上传的附件");
        }

        File file = new File(uploadDir + File.separator + attachment.getFilePath());
        if (file.exists()) {
            file.delete();
        }

        attachmentMapper.deleteById(id);

        return Result.success("附件删除成功");
    }
}
