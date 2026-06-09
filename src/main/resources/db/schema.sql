-- =====================================================
-- Repair System Database Schema
-- Property Repair Work Order Management System
-- =====================================================

CREATE DATABASE IF NOT EXISTS repair_system
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE repair_system;

-- ---------------------------------------------------
-- 1. Users Table
-- ---------------------------------------------------
CREATE TABLE `sys_user` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `username`        VARCHAR(64)  NOT NULL COMMENT 'Login username',
    `password`        VARCHAR(255) NOT NULL COMMENT 'BCrypt hashed password',
    `real_name`       VARCHAR(64)  NOT NULL COMMENT 'Real name',
    `phone`           VARCHAR(20)  DEFAULT NULL,
    `role`            VARCHAR(32)  NOT NULL COMMENT 'OWNER, WORKER, SUPERVISOR, ADMIN',
    `community_id`    BIGINT       DEFAULT NULL COMMENT 'Associated community',
    `building_id`     BIGINT       DEFAULT NULL COMMENT 'Associated building',
    `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '0=disabled, 1=enabled',
    `online_status`   TINYINT      NOT NULL DEFAULT 0 COMMENT '0=offline, 1=online',
    `last_online_at`  DATETIME     DEFAULT NULL,
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_role` (`role`),
    KEY `idx_community` (`community_id`)
) ENGINE=InnoDB COMMENT='System Users';

-- ---------------------------------------------------
-- 2. Worker Skills Table
-- ---------------------------------------------------
CREATE TABLE `sys_worker_skill` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `worker_id`       BIGINT       NOT NULL,
    `problem_type`    VARCHAR(64)  NOT NULL COMMENT 'Plumbing, Electrical, etc.',
    `proficiency`     TINYINT      NOT NULL DEFAULT 3 COMMENT '1-5, 5=expert',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_worker_type` (`worker_id`, `problem_type`),
    KEY `idx_problem_type` (`problem_type`)
) ENGINE=InnoDB COMMENT='Worker Skill Mapping';

-- ---------------------------------------------------
-- 3. Community Table
-- ---------------------------------------------------
CREATE TABLE `sys_community` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `name`            VARCHAR(128) NOT NULL,
    `address`         VARCHAR(255) DEFAULT NULL,
    `status`          TINYINT      NOT NULL DEFAULT 1,
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB COMMENT='Property Communities';

-- ---------------------------------------------------
-- 4. Building Table
-- ---------------------------------------------------
CREATE TABLE `sys_building` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `community_id`    BIGINT       NOT NULL,
    `name`            VARCHAR(64)  NOT NULL COMMENT 'Building number/name',
    `units`           INT          DEFAULT NULL COMMENT 'Number of units',
    `floors`          INT          DEFAULT NULL,
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_community` (`community_id`)
) ENGINE=InnoDB COMMENT='Buildings within communities';

-- ---------------------------------------------------
-- 5. Repair Order Table (Core)
-- ---------------------------------------------------
CREATE TABLE `repair_order` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `order_no`          VARCHAR(64)  NOT NULL COMMENT 'Unique order number',
    `title`             VARCHAR(255) NOT NULL COMMENT 'Brief description',
    `description`       TEXT         COMMENT 'Detailed problem description',
    `problem_type`      VARCHAR(64)  NOT NULL COMMENT 'Category: Plumbing/Electrical/Civil/Facility/Other',
    `urgency`           TINYINT      NOT NULL DEFAULT 2 COMMENT '1=Low,2=Normal,3=High,4=Urgent',

    -- Location
    `community_id`      BIGINT       NOT NULL,
    `building_id`       BIGINT       DEFAULT NULL,
    `unit_id`           BIGINT       DEFAULT NULL,
    `room_no`           VARCHAR(32)  DEFAULT NULL,
    `address_detail`    VARCHAR(255) DEFAULT NULL,

    -- Parties
    `owner_id`          BIGINT       NOT NULL COMMENT 'Reporter (owner)',
    `assigned_worker_id` BIGINT      DEFAULT NULL COMMENT 'Currently assigned worker',
    `original_worker_id` BIGINT      DEFAULT NULL COMMENT 'First assigned worker',

    -- State machine
    `status`            VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    `previous_status`   VARCHAR(32)  DEFAULT NULL COMMENT 'Before suspend/transfer',
    `suspend_reason`    TEXT         DEFAULT NULL,
    `current_dispatch_id` BIGINT     DEFAULT NULL COMMENT 'Active dispatch record ID for this order',
    `suspended_at`      DATETIME     DEFAULT NULL COMMENT 'When the order was last suspended',
    `total_suspended_seconds` INT    NOT NULL DEFAULT 0 COMMENT 'Cumulative suspension duration in seconds',

    -- Timestamps
    `submitted_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `assigned_at`       DATETIME     DEFAULT NULL,
    `accepted_at`       DATETIME     DEFAULT NULL,
    `visit_at`          DATETIME     DEFAULT NULL,
    `completed_at`      DATETIME     DEFAULT NULL,

    -- Duplicate handling
    `parent_order_id`   BIGINT       DEFAULT NULL COMMENT 'Merged into parent order',
    `is_duplicate`      TINYINT      NOT NULL DEFAULT 0,
    `duplicate_note`    VARCHAR(255) DEFAULT NULL,

    -- Soft delete
    `deleted`           TINYINT      NOT NULL DEFAULT 0,

    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_owner` (`owner_id`),
    KEY `idx_worker` (`assigned_worker_id`),
    KEY `idx_status` (`status`),
    KEY `idx_community_building` (`community_id`, `building_id`),
    KEY `idx_problem_type` (`problem_type`),
    KEY `idx_parent` (`parent_order_id`),
    KEY `idx_submitted` (`submitted_at`)
) ENGINE=InnoDB COMMENT='Repair Work Orders';

-- ---------------------------------------------------
-- 6. Dispatch Record Table
-- ---------------------------------------------------
CREATE TABLE `dispatch_record` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `order_id`        BIGINT       NOT NULL,
    `worker_id`       BIGINT       NOT NULL COMMENT 'Dispatched worker',
    `dispatch_type`   VARCHAR(32)  NOT NULL COMMENT 'AUTO, MANUAL, TRANSFER, ESCALATION',
    `from_worker_id`  BIGINT       DEFAULT NULL COMMENT 'Previous worker (for transfer)',
    `reason`          VARCHAR(500) DEFAULT NULL COMMENT 'Dispatch/transfer reason',
    `load_before`     INT          DEFAULT NULL COMMENT 'Worker load at dispatch time',
    `score`           DECIMAL(5,2) DEFAULT NULL COMMENT 'Auto-dispatch match score',
    `active`          TINYINT      NOT NULL DEFAULT 1 COMMENT '1=active dispatch round, 0=superseded',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_order` (`order_id`),
    KEY `idx_worker` (`worker_id`),
    KEY `idx_created` (`created_at`)
) ENGINE=InnoDB COMMENT='Dispatch History Records';

-- ---------------------------------------------------
-- 7. Repair Progress Table
-- ---------------------------------------------------
CREATE TABLE `repair_progress` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `order_id`        BIGINT       NOT NULL,
    `from_status`     VARCHAR(32)  NOT NULL,
    `to_status`       VARCHAR(32)  NOT NULL,
    `operator_id`     BIGINT       NOT NULL,
    `operator_role`   VARCHAR(32)  NOT NULL,
    `remark`          TEXT         DEFAULT NULL,
    `evidence_urls`   JSON         DEFAULT NULL COMMENT 'Photo/video evidence',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_order` (`order_id`),
    KEY `idx_created` (`created_at`)
) ENGINE=InnoDB COMMENT='Repair Progress Timeline';

-- ---------------------------------------------------
-- 8. Attachment Table
-- ---------------------------------------------------
CREATE TABLE `attachment` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `order_id`        BIGINT       NOT NULL,
    `file_name`       VARCHAR(255) NOT NULL,
    `file_type`       VARCHAR(32)  DEFAULT NULL COMMENT 'jpg/png/mp4/pdf',
    `file_size`       BIGINT       DEFAULT NULL COMMENT 'Bytes',
    `file_url`        VARCHAR(500) NOT NULL,
    `stage`           VARCHAR(32)  NOT NULL COMMENT 'SUBMIT, PROCESS, COMPLETE, REWORK',
    `uploader_id`     BIGINT       NOT NULL,
    `deleted`         TINYINT      NOT NULL DEFAULT 0,
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_order` (`order_id`),
    KEY `idx_stage` (`stage`)
) ENGINE=InnoDB COMMENT='File Attachments';

-- ---------------------------------------------------
-- 9. Review Table
-- ---------------------------------------------------
CREATE TABLE `review` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `order_id`        BIGINT       NOT NULL,
    `reviewer_id`     BIGINT       NOT NULL COMMENT 'Owner who reviews',
    `rating`          TINYINT      NOT NULL COMMENT '1-5 stars',
    `content`         TEXT         DEFAULT NULL,
    `reply`           TEXT         DEFAULT NULL COMMENT 'Worker/admin reply',
    `reply_at`        DATETIME     DEFAULT NULL,
    `deleted`         TINYINT      NOT NULL DEFAULT 0,
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_order` (`order_id`),
    KEY `idx_reviewer` (`reviewer_id`)
) ENGINE=InnoDB COMMENT='Owner Reviews';

-- ---------------------------------------------------
-- 10. Rework Order Table
-- ---------------------------------------------------
CREATE TABLE `rework_order` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `order_id`        BIGINT       NOT NULL COMMENT 'Original repair order',
    `rework_no`       VARCHAR(64)  NOT NULL,
    `reason`          TEXT         NOT NULL COMMENT 'Rework reason',
    `description`     TEXT         DEFAULT NULL,
    `assigned_worker_id` BIGINT    DEFAULT NULL COMMENT 'May differ from original',
    `status`          VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    `requester_id`    BIGINT       NOT NULL,
    `completed_at`    DATETIME     DEFAULT NULL,
    `deleted`         TINYINT      NOT NULL DEFAULT 0,
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rework_no` (`rework_no`),
    KEY `idx_order` (`order_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB COMMENT='Rework Orders';

-- ---------------------------------------------------
-- 11. Timeout Escalation Table
-- ---------------------------------------------------
CREATE TABLE `timeout_escalation` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `order_id`        BIGINT       NOT NULL,
    `dispatch_id`     BIGINT       DEFAULT NULL COMMENT 'Dispatch record ID at escalation creation time',
    `timeout_type`    VARCHAR(32)  NOT NULL COMMENT 'ACCEPT_TIMEOUT, COMPLETE_TIMEOUT, VISIT_TIMEOUT',
    `deadline`        DATETIME     NOT NULL COMMENT 'Original deadline',
    `escalated_to`    BIGINT       DEFAULT NULL COMMENT 'Supervisor notified',
    `escalation_level` INT         NOT NULL DEFAULT 1 COMMENT '1=first, 2=second escalation',
    `handled`         TINYINT      NOT NULL DEFAULT 0,
    `handled_at`      DATETIME     DEFAULT NULL,
    `handled_by`      BIGINT       DEFAULT NULL,
    `handle_remark`   VARCHAR(500) DEFAULT NULL,
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_order` (`order_id`),
    KEY `idx_type_handled` (`timeout_type`, `handled`),
    KEY `idx_deadline` (`deadline`)
) ENGINE=InnoDB COMMENT='Timeout Escalation Records';

-- ---------------------------------------------------
-- 12. Audit Log Table
-- ---------------------------------------------------
CREATE TABLE `audit_log` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `order_id`        BIGINT       DEFAULT NULL,
    `action`          VARCHAR(64)  NOT NULL,
    `user_id`         BIGINT       NOT NULL,
    `user_role`       VARCHAR(32)  NOT NULL,
    `before_data`     JSON         DEFAULT NULL COMMENT 'State before action',
    `after_data`      JSON         DEFAULT NULL COMMENT 'State after action',
    `ip_address`      VARCHAR(64)  DEFAULT NULL,
    `user_agent`      VARCHAR(255) DEFAULT NULL,
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_order` (`order_id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_action` (`action`),
    KEY `idx_created` (`created_at`)
) ENGINE=InnoDB COMMENT='Operation Audit Log';

-- ---------------------------------------------------
-- Indexes for performance
-- ---------------------------------------------------
CREATE INDEX `idx_order_status_worker` ON `repair_order` (`status`, `assigned_worker_id`);
CREATE INDEX `idx_progress_order_time` ON `repair_progress` (`order_id`, `created_at`);
CREATE INDEX `idx_dispatch_active` ON `dispatch_record` (`order_id`, `active`);
CREATE INDEX `idx_escalation_dispatch` ON `timeout_escalation` (`order_id`, `dispatch_id`, `handled`);

-- ---------------------------------------------------
-- 13. Spare Part Table
-- ---------------------------------------------------
CREATE TABLE `spare_part` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `part_no`         VARCHAR(64)  NOT NULL COMMENT 'Unique part number',
    `name`            VARCHAR(128) NOT NULL COMMENT 'Part name',
    `specification`   VARCHAR(255) DEFAULT NULL COMMENT 'Specification/model',
    `unit`            VARCHAR(32)  NOT NULL DEFAULT 'pcs' COMMENT 'Unit of measure',
    `problem_type`    VARCHAR(64)  DEFAULT NULL COMMENT 'Associated repair category',
    `is_critical`     TINYINT      NOT NULL DEFAULT 0 COMMENT '1=critical part',
    `min_stock`       INT          NOT NULL DEFAULT 5 COMMENT 'Minimum safe stock level',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_part_no` (`part_no`),
    KEY `idx_problem_type` (`problem_type`)
) ENGINE=InnoDB COMMENT='Spare Part Definitions';

-- ---------------------------------------------------
-- 14. Spare Part Inventory Table
-- ---------------------------------------------------
CREATE TABLE `spare_part_inventory` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `part_id`         BIGINT       NOT NULL,
    `community_id`    BIGINT       NOT NULL,
    `quantity`        INT          NOT NULL DEFAULT 0 COMMENT 'Available quantity',
    `reserved_quantity` INT        NOT NULL DEFAULT 0 COMMENT 'Reserved for pending requisitions',
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_part_community` (`part_id`, `community_id`),
    KEY `idx_community` (`community_id`)
) ENGINE=InnoDB COMMENT='Spare Part Inventory per Community';

-- ---------------------------------------------------
-- 15. Spare Part Requisition Table
-- ---------------------------------------------------
CREATE TABLE `spare_part_requisition` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `requisition_no`  VARCHAR(64)  NOT NULL,
    `order_id`        BIGINT       NOT NULL COMMENT 'Related repair order',
    `worker_id`       BIGINT       NOT NULL COMMENT 'Requesting worker',
    `rework_order_id` BIGINT       DEFAULT NULL COMMENT 'Related rework order for second requisition',
    `status`          VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/ISSUED/COMPLETED/REJECTED',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_requisition_no` (`requisition_no`),
    KEY `idx_order` (`order_id`),
    KEY `idx_worker` (`worker_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB COMMENT='Spare Part Requisition Orders';

-- ---------------------------------------------------
-- 16. Spare Part Requisition Item Table
-- ---------------------------------------------------
CREATE TABLE `spare_part_requisition_item` (
    `id`                BIGINT     NOT NULL AUTO_INCREMENT,
    `requisition_id`    BIGINT     NOT NULL,
    `part_id`           BIGINT     NOT NULL,
    `requested_quantity` INT       NOT NULL DEFAULT 1,
    `issued_quantity`   INT        NOT NULL DEFAULT 0,
    `consumed_quantity` INT        NOT NULL DEFAULT 0,
    `returned_quantity` INT        NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_requisition` (`requisition_id`),
    KEY `idx_part` (`part_id`)
) ENGINE=InnoDB COMMENT='Requisition Line Items';

-- ---------------------------------------------------
-- 17. Purchase Request Table
-- ---------------------------------------------------
CREATE TABLE `purchase_request` (
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT,
    `request_no`            VARCHAR(64)  NOT NULL,
    `part_id`               BIGINT       NOT NULL,
    `community_id`          BIGINT       NOT NULL,
    `quantity`              INT          NOT NULL,
    `status`                VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/ORDERED/RECEIVED/CANCELLED',
    `trigger_requisition_id` BIGINT     DEFAULT NULL COMMENT 'Requisition that triggered this purchase',
    `trigger_order_id`      BIGINT       DEFAULT NULL COMMENT 'Repair order that triggered this purchase',
    `received_at`           DATETIME     DEFAULT NULL,
    `created_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_request_no` (`request_no`),
    KEY `idx_part` (`part_id`),
    KEY `idx_status` (`status`),
    KEY `idx_community` (`community_id`)
) ENGINE=InnoDB COMMENT='Spare Part Purchase Requests';

-- ---------------------------------------------------
-- 18. Spare Part Audit Log Table
-- ---------------------------------------------------
CREATE TABLE `spare_part_audit_log` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `part_id`         BIGINT       DEFAULT NULL,
    `inventory_id`    BIGINT       DEFAULT NULL,
    `requisition_id`  BIGINT       DEFAULT NULL,
    `order_id`        BIGINT       DEFAULT NULL,
    `action`          VARCHAR(64)  NOT NULL COMMENT 'REQUISITION/ISSUE/CONSUME/RETURN/PURCHASE_IN/STOCK_ADJUST',
    `quantity_change`  INT         NOT NULL DEFAULT 0 COMMENT 'Positive=in, Negative=out',
    `before_quantity` INT          DEFAULT NULL,
    `after_quantity`  INT          DEFAULT NULL,
    `operator_id`     BIGINT       DEFAULT NULL,
    `remark`          VARCHAR(500) DEFAULT NULL,
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_part` (`part_id`),
    KEY `idx_order` (`order_id`),
    KEY `idx_requisition` (`requisition_id`),
    KEY `idx_action` (`action`),
    KEY `idx_created` (`created_at`)
) ENGINE=InnoDB COMMENT='Spare Part Audit Trail';
