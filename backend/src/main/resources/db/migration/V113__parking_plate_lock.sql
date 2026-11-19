CREATE TABLE acc_parking_plate_lock (
    plate_no VARCHAR(32) NOT NULL PRIMARY KEY
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='在场登记按车牌互斥';
