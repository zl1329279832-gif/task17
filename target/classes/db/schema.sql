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
    `dispatch_round`    INT          NOT NULL DEFAULT 1 COMMENT 'Incremented on each dispatch/reassignment',
    `repair_round`      INT          NOT NULL DEFAULT 1 COMMENT 'Incremented on each rework',
    `suspended_at`      DATETIME     DEFAULT NULL COMMENT 'When order was suspended',

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
    `timeout_type`    VARCHAR(32)  NOT NULL COMMENT 'ACCEPT_TIMEOUT, COMPLETE_TIMEOUT, VISIT_TIMEOUT',
    `deadline`        DATETIME     NOT NULL COMMENT 'Original deadline',
    `escalated_to`    BIGINT       DEFAULT NULL COMMENT 'Supervisor notified',
    `escalation_level` INT         NOT NULL DEFAULT 1 COMMENT '1=first, 2=second escalation',
    `dispatch_round`  INT          DEFAULT NULL COMMENT 'Dispatch round when escalation was created',
    `repair_round`    INT          DEFAULT NULL COMMENT 'Repair round when escalation was created',
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
