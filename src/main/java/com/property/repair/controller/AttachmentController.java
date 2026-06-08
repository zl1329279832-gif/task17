package com.property.repair.controller;

import com.property.repair.common.result.Result;
import com.property.repair.entity.Attachment;
import com.property.repair.service.AttachmentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.List;

@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    @Value("${attachment.upload-dir:uploads}")
    private String uploadDir;

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping("/upload")
    public Result<Attachment> upload(@RequestParam("file") MultipartFile file,
                                     @RequestParam(required = false) Long orderId,
                                     @RequestParam(required = false, defaultValue = "REPORT") String usageType) {
        return attachmentService.upload(file, orderId, usageType);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        Result<List<Attachment>> result = attachmentService.getByOrderId(null);
        // Find attachment by id from DB
        Attachment attachment = null;
        if (result.getData() != null) {
            for (Attachment a : result.getData()) {
                if (a.getId().equals(id)) {
                    attachment = a;
                    break;
                }
            }
        }

        if (attachment == null) {
            return ResponseEntity.notFound().build();
        }

        File file = new File(uploadDir + File.separator + attachment.getFilePath());
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + attachment.getFileName() + "\"")
                .body(resource);
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        return attachmentService.delete(id);
    }
}
