CREATE TABLE users (
                       id         BIGSERIAL PRIMARY KEY,
                       email      VARCHAR(255) NOT NULL UNIQUE,
                       password   VARCHAR(255) NOT NULL,
                       role       VARCHAR(20)  NOT NULL,
                       enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
                       created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_email ON users (email);

-- Seed admin: admin@shop.com / admin123
INSERT INTO users (email, password, role)
VALUES (
           'admin@shop.com',
           '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVEFDa',
           'ADMIN'
       );