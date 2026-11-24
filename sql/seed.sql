-- DeskWeave Demo Seed Dataset (PostgreSQL 16)
-- Idempotent reset so running multiple times produces clean deterministic IDs.
TRUNCATE TABLE booking_audit_log, bookings, capacity_recommendations, workspaces, users RESTART IDENTITY CASCADE;

-- 1. Users (admin@deskweave.io / admin123, employees / pass123)
INSERT INTO users (full_name, email, password_hash, role, department) VALUES
    ('Priya Sharma (Facilities Admin)', 'admin@deskweave.io', 'dwadmin2026salt0:84e4fcdc235b2cfe874e72e7d62546c2688615d99d398c99577324378b4a6a3b', 'ADMIN', 'Facilities & Workplace Ops'),
    ('Alice Verma', 'alice@deskweave.io', 'dwemp2026salt000:eae5416047fd96c984422a985076cc773d828067e27d97d04ed46b30c5d2c2df', 'EMPLOYEE', 'Platform Engineering'),
    ('Bob Nair', 'bob@deskweave.io', 'dwemp2026salt000:eae5416047fd96c984422a985076cc773d828067e27d97d04ed46b30c5d2c2df', 'EMPLOYEE', 'Product Design'),
    ('Carol Patel', 'carol@deskweave.io', 'dwemp2026salt000:eae5416047fd96c984422a985076cc773d828067e27d97d04ed46b30c5d2c2df', 'EMPLOYEE', 'Data Science'),
    ('Dave Kulkarni', 'dave@deskweave.io', 'dwemp2026salt000:eae5416047fd96c984422a985076cc773d828067e27d97d04ed46b30c5d2c2df', 'EMPLOYEE', 'Quality Assurance');

-- 2. Workspaces across Floors 1-3 (IDs 1..10)
INSERT INTO workspaces (code, name, workspace_type, floor, capacity, has_monitor, is_standing_desk, has_projector, has_whiteboard, is_active) VALUES
    ('D-101', 'Window Focus Desk 1', 'DESK', 1, 1, TRUE, FALSE, FALSE, FALSE, TRUE),
    ('D-102', 'Window Focus Desk 2', 'DESK', 1, 1, TRUE, TRUE, FALSE, FALSE, TRUE),
    ('D-103', 'Ergonomic Bay 3', 'DESK', 1, 1, FALSE, TRUE, FALSE, FALSE, TRUE),
    ('D-104', 'Open Pod Desk 4', 'DESK', 1, 1, TRUE, FALSE, FALSE, FALSE, TRUE),
    ('D-105', 'Open Pod Desk 5', 'DESK', 1, 1, FALSE, FALSE, FALSE, FALSE, TRUE),
    ('R-201', 'Boardroom Alpha', 'ROOM', 2, 10, FALSE, FALSE, TRUE, TRUE, TRUE),
    ('R-202', 'Huddle Beta', 'ROOM', 2, 6, FALSE, FALSE, TRUE, FALSE, TRUE),
    ('R-203', 'Focus Gamma', 'ROOM', 2, 4, FALSE, FALSE, FALSE, TRUE, TRUE),
    ('D-201', 'Mezzanine Monitor Desk', 'DESK', 2, 1, TRUE, FALSE, FALSE, FALSE, TRUE),
    ('D-304', 'Quiet Corner Desk', 'DESK', 3, 1, FALSE, FALSE, FALSE, FALSE, TRUE);

-- 3. Historical & Live Bookings (relative to CURRENT_DATE)
-- Signal A: Floor 2 Rooms full capacity (10:00-12:00 across multiple recent days) + Signal D: R-201 booked by 1-2 people
INSERT INTO bookings (user_id, workspace_id, booking_date, start_time, end_time, attendee_count, status, checked_in_at, notes) VALUES
    (2, 6, CURRENT_DATE - 2, '10:00', '12:00', 2, 'COMPLETED', CURRENT_DATE - 2 + TIME '10:03', '1:1 architecture review'),
    (3, 7, CURRENT_DATE - 2, '10:00', '12:00', 5, 'COMPLETED', CURRENT_DATE - 2 + TIME '10:04', 'Sprint design critique'),
    (4, 8, CURRENT_DATE - 2, '10:00', '12:00', 3, 'COMPLETED', CURRENT_DATE - 2 + TIME '10:02', 'Model sync'),
    (5, 6, CURRENT_DATE - 3, '10:00', '12:00', 1, 'COMPLETED', CURRENT_DATE - 3 + TIME '10:01', 'Quiet solo prep'),
    (2, 7, CURRENT_DATE - 3, '10:00', '12:00', 4, 'COMPLETED', CURRENT_DATE - 3 + TIME '10:05', 'Backend triage'),
    (3, 8, CURRENT_DATE - 3, '10:00', '12:00', 4, 'COMPLETED', CURRENT_DATE - 3 + TIME '10:02', 'UX workshop'),
    (4, 6, CURRENT_DATE - 4, '10:00', '12:00', 2, 'COMPLETED', CURRENT_DATE - 4 + TIME '10:06', 'Data pipeline pairing'),
    (5, 7, CURRENT_DATE - 4, '10:00', '12:00', 5, 'COMPLETED', CURRENT_DATE - 4 + TIME '10:04', 'Release QA signoff'),
    (2, 8, CURRENT_DATE - 4, '10:00', '12:00', 3, 'COMPLETED', CURRENT_DATE - 4 + TIME '10:01', 'API contract check'),
    (3, 6, CURRENT_DATE - 5, '10:00', '12:00', 2, 'COMPLETED', CURRENT_DATE - 5 + TIME '10:02', 'Figma handoff'),
    (4, 7, CURRENT_DATE - 5, '10:00', '12:00', 6, 'COMPLETED', CURRENT_DATE - 5 + TIME '10:07', 'Analytics retro'),
    (5, 8, CURRENT_DATE - 5, '10:00', '12:00', 4, 'COMPLETED', CURRENT_DATE - 5 + TIME '10:03', 'Test automation plan'),
    (2, 6, CURRENT_DATE - 6, '14:00', '16:00', 8, 'COMPLETED', CURRENT_DATE - 6 + TIME '14:02', 'All-hands engineering'),
    (3, 6, CURRENT_DATE - 7, '14:00', '16:00', 7, 'COMPLETED', CURRENT_DATE - 7 + TIME '14:01', 'Quarterly roadmap'),
    (4, 7, CURRENT_DATE - 6, '13:00', '15:00', 4, 'COMPLETED', CURRENT_DATE - 6 + TIME '13:04', 'SQL tuning session'),
    (5, 8, CURRENT_DATE - 7, '11:00', '13:00', 3, 'COMPLETED', CURRENT_DATE - 7 + TIME '11:03', 'Bug bash'),
    -- Floor 2 Mezzanine Desk (D-201) healthy usage
    (2, 9, CURRENT_DATE - 2, '09:00', '17:00', 1, 'COMPLETED', CURRENT_DATE - 2 + TIME '09:05', 'Deep work day'),
    (3, 9, CURRENT_DATE - 4, '09:00', '16:00', 1, 'COMPLETED', CURRENT_DATE - 4 + TIME '09:04', 'Design sprint'),
    (4, 9, CURRENT_DATE - 6, '10:00', '17:00', 1, 'COMPLETED', CURRENT_DATE - 6 + TIME '10:02', 'Feature build'),

    -- Signal C: Floor 1 Desks — mix of COMPLETED (checked-in) and EXPIRED (no-show > 25%)
    (2, 1, CURRENT_DATE - 2, '09:00', '17:00', 1, 'COMPLETED', CURRENT_DATE - 2 + TIME '09:06', 'Office day'),
    (3, 2, CURRENT_DATE - 2, '09:00', '17:00', 1, 'COMPLETED', CURRENT_DATE - 2 + TIME '09:03', 'Office day'),
    (4, 3, CURRENT_DATE - 2, '09:00', '13:00', 1, 'EXPIRED', NULL, 'Missed morning slot'),
    (5, 4, CURRENT_DATE - 2, '09:00', '17:00', 1, 'COMPLETED', CURRENT_DATE - 2 + TIME '09:08', 'QA regression'),
    (2, 5, CURRENT_DATE - 3, '09:00', '13:00', 1, 'EXPIRED', NULL, 'WFH last minute'),
    (3, 1, CURRENT_DATE - 3, '09:00', '17:00', 1, 'COMPLETED', CURRENT_DATE - 3 + TIME '09:02', 'Office day'),
    (4, 2, CURRENT_DATE - 3, '10:00', '14:00', 1, 'EXPIRED', NULL, 'Train delayed'),
    (5, 3, CURRENT_DATE - 4, '09:00', '17:00', 1, 'COMPLETED', CURRENT_DATE - 4 + TIME '09:04', 'Office day'),
    (2, 4, CURRENT_DATE - 4, '09:00', '13:00', 1, 'EXPIRED', NULL, 'Unclaimed booking'),
    (3, 5, CURRENT_DATE - 5, '09:00', '17:00', 1, 'COMPLETED', CURRENT_DATE - 5 + TIME '09:05', 'Office day'),
    (4, 1, CURRENT_DATE - 5, '09:00', '12:00', 1, 'EXPIRED', NULL, 'Schedule conflict'),
    (5, 2, CURRENT_DATE - 6, '09:00', '17:00', 1, 'COMPLETED', CURRENT_DATE - 6 + TIME '09:01', 'Office day'),
    (2, 3, CURRENT_DATE - 8, '09:00', '17:00', 1, 'COMPLETED', CURRENT_DATE - 8 + TIME '09:07', 'Office day'),
    (3, 4, CURRENT_DATE - 9, '09:00', '13:00', 1, 'EXPIRED', NULL, 'Unclaimed desk'),
    (4, 5, CURRENT_DATE - 10, '09:00', '17:00', 1, 'COMPLETED', CURRENT_DATE - 10 + TIME '09:04', 'Office day'),

    -- Signal B: Floor 3 Desk D-304 (ws 10) deliberately underutilized (only 1 hour in 30 days)
    (5, 10, CURRENT_DATE - 12, '15:00', '16:00', 1, 'COMPLETED', CURRENT_DATE - 12 + TIME '15:02', 'Quick call'),

    -- Live Demo Rows: 1 overdue CONFIRMED booking (swept by Demo Expiry) + future CONFIRMED bookings
    (3, 4, CURRENT_DATE - 1, '09:00', '11:00', 1, 'CONFIRMED', NULL, 'Demo unclaimed booking awaiting expiry sweep'),
    (2, 1, CURRENT_DATE + 1, '10:00', '12:00', 1, 'CONFIRMED', NULL, 'Tomorrow morning focus work'),
    (2, 7, CURRENT_DATE + 1, '11:00', '12:30', 4, 'CONFIRMED', NULL, 'Project architecture check-in');

-- 4. Populate matching audit log entries
INSERT INTO booking_audit_log (booking_id, actor_user_id, action, details)
SELECT booking_id, user_id, 'CREATED', 'Seeded booking (' || notes || ')'
FROM bookings;

INSERT INTO booking_audit_log (booking_id, actor_user_id, action, details)
SELECT booking_id, user_id, 'CHECKED_IN', 'Employee checked in on site'
FROM bookings
WHERE checked_in_at IS NOT NULL;

INSERT INTO booking_audit_log (booking_id, actor_user_id, action, details)
SELECT booking_id, 1, 'EXPIRED', 'Auto-expired after check-in grace window elapsed'
FROM bookings
WHERE status = 'EXPIRED';
