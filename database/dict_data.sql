-- ========================================================
-- LTPMS 字典初始数据
-- ========================================================

SET NAMES utf8mb4;

-- 实验类别
INSERT INTO `dictionary` (`dict_type`, `dict_label`, `dict_value`, `sort_order`, `status`) VALUES
('category', '基础实验', 'basic', 1, 1),
('category', '综合实验', 'comprehensive', 2, 1),
('category', '设计实验', 'design', 3, 1),
('category', '创新实验', 'innovation', 4, 1),
('category', '虚拟仿真实验', 'virtual', 5, 1);

-- 实验类型
INSERT INTO `dictionary` (`dict_type`, `dict_label`, `dict_value`, `sort_order`, `status`) VALUES
('type', '验证性', 'verification', 1, 1),
('type', '演示性', 'demonstration', 2, 1),
('type', '综合性', 'comprehensive', 3, 1),
('type', '设计性', 'design', 4, 1),
('type', '研究性', 'research', 5, 1);

-- 实验所属学科
INSERT INTO `dictionary` (`dict_type`, `dict_label`, `dict_value`, `sort_order`, `status`) VALUES
('subject', '计算机科学与技术', 'cs', 1, 1),
('subject', '软件工程', 'se', 2, 1),
('subject', '网络工程', 'network', 3, 1),
('subject', '信息安全', 'security', 4, 1),
('subject', '物联网工程', 'iot', 5, 1),
('subject', '数据科学与大数据技术', 'data_science', 6, 1),
('subject', '人工智能', 'ai', 7, 1),
('subject', '电子信息工程', 'electronics', 8, 1);

-- 实验要求
INSERT INTO `dictionary` (`dict_type`, `dict_label`, `dict_value`, `sort_order`, `status`) VALUES
('requirement', '必修', 'required', 1, 1),
('requirement', '选修', 'elective', 2, 1);

-- 实验者类别
INSERT INTO `dictionary` (`dict_type`, `dict_label`, `dict_value`, `sort_order`, `status`) VALUES
('experimenter_type', '本科生', 'undergraduate', 1, 1),
('experimenter_type', '研究生', 'graduate', 2, 1);
