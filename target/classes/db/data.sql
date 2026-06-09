-- =====================================================
-- Seed Data for Repair System
-- =====================================================

USE repair_system;

-- Communities
INSERT INTO `sys_community` (`id`, `name`, `address`) VALUES
(1, 'Sunshine Garden', 'No.100 Sunshine Road'),
(2, 'Green Valley Estate', 'No.200 Valley Avenue');

-- Buildings
INSERT INTO `sys_building` (`id`, `community_id`, `name`, `units`, `floors`) VALUES
(1, 1, 'Building A', 4, 18),
(2, 1, 'Building B', 4, 18),
(3, 2, 'Tower 1', 2, 30),
(4, 2, 'Tower 2', 2, 30);

-- Users (password: 123456 for all, BCrypt encoded)
INSERT INTO `sys_user` (`id`, `username`, `password`, `real_name`, `phone`, `role`, `community_id`, `building_id`, `status`, `online_status`) VALUES
-- Admin
(1,  'admin',      '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'Admin Zhang',    '13800000001', 'ADMIN',      NULL, NULL, 1, 1),
-- Supervisors
(2,  'supervisor1','$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'Li Wei',         '13800000002', 'SUPERVISOR', 1,    NULL, 1, 1),
(3,  'supervisor2','$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'Wang Fang',      '13800000003', 'SUPERVISOR', 2,    NULL, 1, 1),
-- Workers
(4,  'worker1',    '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'Zhang San',      '13800000004', 'WORKER',     1,    1,    1, 1),
(5,  'worker2',    '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'Li Si',          '13800000005', 'WORKER',     1,    2,    1, 1),
(6,  'worker3',    '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'Wang Wu',        '13800000006', 'WORKER',     2,    3,    1, 0),
(7,  'worker4',    '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'Zhao Liu',       '13800000007', 'WORKER',     2,    4,    1, 1),
-- Owners
(8,  'owner1',     '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'Chen Jia',       '13800000008', 'OWNER',      1,    1,    1, 0),
(9,  'owner2',     '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'Liu Mei',        '13800000009', 'OWNER',      1,    1,    1, 0),
(10, 'owner3',     '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'Sun Ping',       '13800000010', 'OWNER',      2,    3,    1, 0);

-- Worker Skills
INSERT INTO `sys_worker_skill` (`worker_id`, `problem_type`, `proficiency`) VALUES
(4, 'PLUMBING',   5),
(4, 'CIVIL',      3),
(5, 'ELECTRICAL', 4),
(5, 'FACILITY',   3),
(6, 'PLUMBING',   3),
(6, 'ELECTRICAL', 5),
(7, 'FACILITY',   4),
(7, 'CIVIL',      4),
(7, 'PLUMBING',   2);
