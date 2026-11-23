-- =========================================================
--  WishesSender - MySQL schema (runs automatically on start)
-- =========================================================

-- 1. Users (regular users and admins)
CREATE TABLE IF NOT EXISTS users (
    id                 INT AUTO_INCREMENT PRIMARY KEY,
    name               VARCHAR(100) NOT NULL,
    email              VARCHAR(150) NOT NULL UNIQUE,
    password_hash      VARCHAR(100) NOT NULL,
    role               VARCHAR(10)  NOT NULL DEFAULT 'USER',   -- USER or ADMIN
    ai_enabled         TINYINT(1)   NOT NULL DEFAULT 1,        -- privacy: may we use AI?
    share_details      TINYINT(1)   NOT NULL DEFAULT 1,        -- privacy: send interests to AI?
    remind_days_before INT          NOT NULL DEFAULT 1,        -- 0 = only on the day
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. Connections (the people a user cares about)
CREATE TABLE IF NOT EXISTS connections (
    id             INT AUTO_INCREMENT PRIMARY KEY,
    user_id        INT          NOT NULL,
    name           VARCHAR(100) NOT NULL,
    relation       VARCHAR(20)  NOT NULL,          -- Family, Friend, Partner, Colleague, Client, Customer
    phone          VARCHAR(30),
    email          VARCHAR(150),
    address        VARCHAR(255),
    dob            DATE,
    gender         VARCHAR(10),
    religion       VARCHAR(20),
    favourite_dish VARCHAR(100),
    interests      VARCHAR(255),                   -- comma separated, e.g. "books,music"
    gift_min       INT NOT NULL DEFAULT 0,
    gift_max       INT NOT NULL DEFAULT 5000,
    notes          TEXT,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_conn_user (user_id),
    INDEX idx_conn_name (name)
);

-- 3. Events (one connection has many events)
CREATE TABLE IF NOT EXISTS events (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    connection_id INT          NOT NULL,
    title         VARCHAR(100) NOT NULL,
    event_type    VARCHAR(20)  NOT NULL,           -- BIRTHDAY, ANNIVERSARY, FESTIVAL, CUSTOM
    event_date    DATE         NOT NULL,
    yearly        TINYINT(1)   NOT NULL DEFAULT 1, -- repeats every year?
    description   VARCHAR(255),
    FOREIGN KEY (connection_id) REFERENCES connections(id) ON DELETE CASCADE,
    INDEX idx_event_date (event_date)
);

-- 4. Festival master list (managed by admin)
CREATE TABLE IF NOT EXISTS festivals (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    festival_date DATE         NOT NULL,
    religion      VARCHAR(20)  NOT NULL DEFAULT 'All',
    region        VARCHAR(50)  NOT NULL DEFAULT 'Bangladesh'
);

-- 5. In-app notifications created by the reminder scheduler
CREATE TABLE IF NOT EXISTS notifications (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    user_id     INT          NOT NULL,
    event_id    INT          NOT NULL,
    for_date    DATE         NOT NULL,             -- the date of the event occurrence
    days_before INT          NOT NULL,
    message     VARCHAR(255) NOT NULL,
    is_read     TINYINT(1)   NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id)  REFERENCES users(id)  ON DELETE CASCADE,
    FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE,
    UNIQUE KEY uq_notification (event_id, for_date, days_before)  -- never remind twice
);

-- 6. Wish templates (used when AI is off or fails)
CREATE TABLE IF NOT EXISTS wish_templates (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(20) NOT NULL,
    tone       VARCHAR(20) NOT NULL,                -- Formal, Emotional, Funny, Romantic
    text       TEXT        NOT NULL                 -- {name} is replaced with the person's name
);

-- 7. Wish history
CREATE TABLE IF NOT EXISTS wishes (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    user_id       INT         NOT NULL,
    connection_id INT         NOT NULL,
    event_id      INT,
    tone          VARCHAR(20),
    language      VARCHAR(20),
    text          TEXT        NOT NULL,
    source        VARCHAR(10) NOT NULL,             -- AI or TEMPLATE
    status        VARCHAR(10) NOT NULL DEFAULT 'SAVED', -- SAVED or SENT
    created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id)       REFERENCES users(id)       ON DELETE CASCADE,
    FOREIGN KEY (connection_id) REFERENCES connections(id) ON DELETE CASCADE,
    FOREIGN KEY (event_id)      REFERENCES events(id)      ON DELETE SET NULL
);

-- 8. Gift catalog (managed by admin)
CREATE TABLE IF NOT EXISTS gifts (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    category   VARCHAR(50)  NOT NULL,
    price      INT          NOT NULL,               -- in BDT
    event_type VARCHAR(20)  NOT NULL DEFAULT 'ANY', -- or BIRTHDAY, ANNIVERSARY, ...
    tag        VARCHAR(50)  NOT NULL,               -- matched against connection interests
    reason     VARCHAR(255) NOT NULL                -- "Why this gift?"
);

-- =========================================================
--  VIEWS (used for reminders, dashboard and reports)
-- =========================================================

-- Every event with its NEXT occurrence date and days left.
-- A yearly event (birthday) on 1990-05-10 has next_date = this year's or next year's 05-10.
CREATE OR REPLACE VIEW v_event_next AS
SELECT t.*, DATEDIFF(t.next_date, CURDATE()) AS days_left
FROM (
    SELECT e.id, e.connection_id, e.title, e.event_type, e.event_date, e.yearly, e.description,
           c.user_id, c.name AS connection_name, c.relation,
           CASE WHEN e.yearly = 1 THEN
                DATE_ADD(e.event_date, INTERVAL
                    (YEAR(CURDATE()) - YEAR(e.event_date)
                     + IF(DATE_FORMAT(e.event_date, '%m%d') < DATE_FORMAT(CURDATE(), '%m%d'), 1, 0)) YEAR)
           ELSE e.event_date END AS next_date
    FROM events e
    JOIN connections c ON c.id = e.connection_id
) t;

-- Report: connections per relation type
CREATE OR REPLACE VIEW v_connections_by_relation AS
SELECT user_id, relation, COUNT(*) AS total
FROM connections
GROUP BY user_id, relation;

-- Report: events per month (by the month the event falls in)
CREATE OR REPLACE VIEW v_events_per_month AS
SELECT c.user_id, MONTH(e.event_date) AS month_no, COUNT(*) AS total
FROM events e
JOIN connections c ON c.id = e.connection_id
GROUP BY c.user_id, MONTH(e.event_date);

-- Report: wishes by event type (festival, birthday, ...)
CREATE OR REPLACE VIEW v_wishes_by_event_type AS
SELECT w.user_id, COALESCE(e.event_type, 'GENERAL') AS event_type, COUNT(*) AS total
FROM wishes w
LEFT JOIN events e ON e.id = w.event_id
GROUP BY w.user_id, COALESCE(e.event_type, 'GENERAL');
