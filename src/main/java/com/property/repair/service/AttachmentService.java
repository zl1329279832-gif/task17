package com.property.repair.service;

import com.property.repair.common.result.Result;
import com.property.repair.entity.Attachment;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface AttachmentService {

    Result<Attachment> upload(MultipartFile file, Long orderId, String usageType);

    Result<List<Attachment>> getByOrderId(Long orderId);

    Result<?> delete(Long id);
}
