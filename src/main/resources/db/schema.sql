-- ============================================
-- 物业报修管理系统 - 数据库建表脚本
-- ============================================

-- 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    real_name VARCHAR(50) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    role VARCHAR(20) NOT NULL,
    community_id BIGINT NULL,
    building_id BIGINT NULL,
    unit_number VARCHAR(20) NULL,
    online_status TINYINT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_role (role),
    INDEX idx_community (community_id),
    INDEX idx_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 小区表
CREATE TABLE IF NOT EXISTS community (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    address VARCHAR(255) NULL,
    supervisor_id BIGINT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_supervisor (supervisor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 楼栋表
CREATE TABLE IF NOT EXISTS building (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    community_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    floor_count INT NULL,
    longitude DECIMAL(10, 7) NULL,
    latitude DECIMAL(10, 7) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_community (community_id),
    CONSTRAINT fk_building_community FOREIGN KEY (community_id) REFERENCES community(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 报修分类表
CREATE TABLE IF NOT EXISTS category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    parent_id BIGINT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_parent (parent_id),
    CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES category(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 维修人员技能表
CREATE TABLE IF NOT EXISTS worker_skill (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    worker_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    community_id BIGINT NOT NULL,
    proficiency INT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_worker_category_community (worker_id, category_id, community_id),
    INDEX idx_worker (worker_id),
    INDEX idx_community_category (community_id, category_id),
    CONSTRAINT fk_skill_worker FOREIGN KEY (worker_id) REFERENCES sys_user(id),
    CONSTRAINT fk_skill_category FOREIGN KEY (category_id) REFERENCES category(id),
    CONSTRAINT fk_skill_community FOREIGN KEY (community_id) REFERENCES community(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 报修工单表
CREATE TABLE IF NOT EXISTS repair_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(32) NOT NULL UNIQUE,
    owner_id BIGINT NOT NULL,
    community_id BIGINT NOT NULL,
    building_id BIGINT NOT NULL,
    unit_number VARCHAR(20) NOT NULL,
    category_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    urgency TINYINT NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    current_worker_id BIGINT NULL,
    expected_time DATETIME NULL,
    assigned_at DATETIME NULL,
    accepted_at DATETIME NULL,
    started_at DATETIME NULL,
    completed_at DATETIME NULL,
    confirmed_at DATETIME NULL,
    duplicate_group VARCHAR(64) NULL,
    rework_count INT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_order_no (order_no),
    INDEX idx_owner (owner_id),
    INDEX idx_status (status),
    INDEX idx_community (community_id),
    INDEX idx_worker (current_worker_id),
    INDEX idx_category (category_id),
    INDEX idx_created_at (created_at),
    INDEX idx_duplicate_group (duplicate_group),
    CONSTRAINT fk_order_owner FOREIGN KEY (owner_id) REFERENCES sys_user(id),
    CONSTRAINT fk_order_community FOREIGN KEY (community_id) REFERENCES community(id),
    CONSTRAINT fk_order_building FOREIGN KEY (building_id) REFERENCES building(id),
    CONSTRAINT fk_order_category FOREIGN KEY (category_id) REFERENCES category(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 派单记录表
CREATE TABLE IF NOT EXISTS dispatch_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    worker_id BIGINT NOT NULL,
    dispatcher_id BIGINT NULL,
    dispatch_type VARCHAR(20) NOT NULL,
    reason VARCHAR(255) NULL,
    result VARCHAR(20) NULL,
    responded_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order (order_id),
    INDEX idx_worker (worker_id),
    CONSTRAINT fk_dispatch_order FOREIGN KEY (order_id) REFERENCES repair_order(id),
    CONSTRAINT fk_dispatch_worker FOREIGN KEY (worker_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 维修进度表
CREATE TABLE IF NOT EXISTS repair_progress (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    operator_id BIGINT NOT NULL,
    from_status VARCHAR(20) NULL,
    to_status VARCHAR(20) NOT NULL,
    remark TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order (order_id),
    CONSTRAINT fk_progress_order FOREIGN KEY (order_id) REFERENCES repair_order(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 附件表
CREATE TABLE IF NOT EXISTS attachment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NULL,
    uploader_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL DEFAULT 0,
    file_type VARCHAR(50) NULL,
    usage_type VARCHAR(20) NULL,
    progress_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order (order_id),
    INDEX idx_progress (progress_id),
    CONSTRAINT fk_attachment_order FOREIGN KEY (order_id) REFERENCES repair_order(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 评价表
CREATE TABLE IF NOT EXISTS evaluation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    worker_id BIGINT NOT NULL,
    score INT NOT NULL,
    attitude_score INT NULL,
    quality_score INT NULL,
    speed_score INT NULL,
    comment TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_order (order_id),
    INDEX idx_worker (worker_id),
    CONSTRAINT fk_eval_order FOREIGN KEY (order_id) REFERENCES repair_order(id),
    CONSTRAINT fk_eval_owner FOREIGN KEY (owner_id) REFERENCES sys_user(id),
    CONSTRAINT fk_eval_worker FOREIGN KEY (worker_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 返工记录表
CREATE TABLE IF NOT EXISTS rework_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    initiator_id BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    from_status VARCHAR(20) NOT NULL,
    previous_worker_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order (order_id),
    CONSTRAINT fk_rework_order FOREIGN KEY (order_id) REFERENCES repair_order(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 超时升级表
CREATE TABLE IF NOT EXISTS timeout_escalation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    worker_id BIGINT NULL,
    supervisor_id BIGINT NULL,
    escalation_type VARCHAR(30) NOT NULL,
    timeout_minutes INT NOT NULL,
    resolution VARCHAR(500) NULL,
    resolved_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order (order_id),
    INDEX idx_supervisor (supervisor_id),
    CONSTRAINT fk_escalation_order FOREIGN KEY (order_id) REFERENCES repair_order(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 审计日志表
CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NULL,
    user_role VARCHAR(20) NULL,
    action VARCHAR(50) NOT NULL,
    target_type VARCHAR(50) NULL,
    target_id BIGINT NULL,
    detail TEXT NULL,
    ip_address VARCHAR(50) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_action (action),
    INDEX idx_target (target_type, target_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================
-- 测试数据
-- ============================================

-- 插入小区
INSERT INTO community (id, name, address, supervisor_id, status) VALUES
(1, '阳光花园小区', '北京市朝阳区阳光路100号', NULL, 1),
(2, '翠湖雅苑小区', '北京市海淀区翠湖路200号', NULL, 1);

-- 插入楼栋
INSERT INTO building (id, community_id, name, floor_count, longitude, latitude) VALUES
(1, 1, '1号楼', 18, 116.4074000, 39.9042000),
(2, 1, '2号楼', 24, 116.4084000, 39.9052000),
(3, 2, '1号楼', 20, 116.3174000, 39.9842000),
(4, 2, '3号楼', 15, 116.3184000, 39.9852000);

-- 插入管理员用户 (密码: admin123, 使用BCrypt加密)
INSERT INTO sys_user (id, username, password, real_name, phone, role, community_id, status) VALUES
(1, 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '系统管理员', '13800000000', 'ADMIN', NULL, 1);

-- 插入业主
INSERT INTO sys_user (id, username, password, real_name, phone, role, community_id, building_id, unit_number, status) VALUES
(2, 'owner1', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '张三', '13800000001', 'OWNER', 1, 1, '1-301', 1),
(3, 'owner2', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '李四', '13800000002', 'OWNER', 2, 3, '2-502', 1);

-- 插入维修人员
INSERT INTO sys_user (id, username, password, real_name, phone, role, community_id, online_status, status) VALUES
(4, 'worker1', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '王维修', '13800000003', 'WORKER', 1, 1, 1),
(5, 'worker2', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '赵维修', '13800000004', 'WORKER', 2, 1, 1);

-- 插入主管
INSERT INTO sys_user (id, username, password, real_name, phone, role, community_id, status) VALUES
(6, 'supervisor1', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '刘主管', '13800000005', 'SUPERVISOR', 1, 1);

-- 更新小区的主管
UPDATE community SET supervisor_id = 6 WHERE id = 1;

-- 插入报修分类
INSERT INTO category (id, name, parent_id, sort_order, status) VALUES
(1, '水电维修', NULL, 1, 1),
(2, '门窗维修', NULL, 2, 1),
(3, '管道维修', NULL, 3, 1);

-- 插入维修人员技能
INSERT INTO worker_skill (worker_id, category_id, community_id, proficiency) VALUES
(4, 1, 1, 3),
(4, 2, 1, 2),
(4, 3, 1, 1),
(5, 1, 2, 2),
(5, 3, 2, 3);
