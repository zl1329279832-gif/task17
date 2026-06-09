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

-- Spare Parts
INSERT INTO `spare_part` (`id`, `part_no`, `name`, `specification`, `unit`, `problem_type`, `is_critical`, `min_stock`) VALUES
(1,  'SP-PLB-001', 'Faucet Cartridge',      'Universal 35mm',        'pcs', 'PLUMBING',   1, 10),
(2,  'SP-PLB-002', 'PVC Pipe 50mm',         '50mm x 1m',             'm',   'PLUMBING',   1, 20),
(3,  'SP-PLB-003', 'Pipe Sealant Tape',     'PTFE 12mm x 10m',       'roll','PLUMBING',   0, 30),
(4,  'SP-PLB-004', 'Drain Strainer',        'Stainless Steel 80mm',  'pcs', 'PLUMBING',   0, 15),
(5,  'SP-ELE-001', 'Circuit Breaker 20A',   'DZ47 1P 20A',           'pcs', 'ELECTRICAL', 1, 8),
(6,  'SP-ELE-002', 'LED Light Panel',       '600x600mm 48W',         'pcs', 'ELECTRICAL', 0, 10),
(7,  'SP-ELE-003', 'Electrical Wire 2.5mm', 'BV 2.5mm² red',         'm',   'ELECTRICAL', 1, 50),
(8,  'SP-CIV-001', 'Wall Patch Compound',   '5kg bucket',            'bucket','CIVIL',    0, 10),
(9,  'SP-CIV-002', 'Waterproof Membrane',   '1.5mm x 1m roll',       'm',   'CIVIL',      1, 15),
(10, 'SP-FAC-001', 'Door Lock Cylinder',    'Universal Euro Profile', 'pcs', 'FACILITY',   1, 6),
(11, 'SP-FAC-002', 'Window Handle',         'Universal Espag',       'pcs', 'FACILITY',   0, 10),
(12, 'SP-FAC-003', 'Elevator Button Panel', 'Standard 16-floor',     'pcs', 'FACILITY',   1, 2);

-- Spare Part Inventory (per community)
INSERT INTO `spare_part_inventory` (`part_id`, `community_id`, `quantity`, `reserved_quantity`) VALUES
-- Sunshine Garden (community 1)
(1,  1, 15, 0), (2,  1, 30, 0), (3,  1, 50, 0), (4,  1, 20, 0),
(5,  1, 10, 0), (6,  1, 12, 0), (7,  1, 80, 0), (8,  1, 8,  0),
(9,  1, 20, 0), (10, 1, 5,  0), (11, 1, 8,  0), (12, 1, 1,  0),
-- Green Valley Estate (community 2)
(1,  2, 10, 0), (2,  2, 25, 0), (3,  2, 40, 0), (4,  2, 15, 0),
(5,  2, 8,  0), (6,  2, 10, 0), (7,  2, 60, 0), (8,  2, 6,  0),
(9,  2, 15, 0), (10, 2, 4,  0), (11, 2, 6,  0), (12, 2, 0,  0);
