CREATE TABLE vl_user (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(160) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    user_role VARCHAR(20) NOT NULL,
    phone VARCHAR(30),
    document VARCHAR(20),
    active BOOLEAN NOT NULL,
    failed_logins INT NOT NULL,
    locked_until DATETIME(6),
    last_login_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_user_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE vl_session (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    last_seen_at DATETIME(6) NOT NULL,
    ended_at DATETIME(6),
    end_reason VARCHAR(20),
    ip_address VARCHAR(45),
    user_agent VARCHAR(255),
    CONSTRAINT uk_session_token_hash UNIQUE (token_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE vl_address (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    label VARCHAR(40) NOT NULL,
    recipient VARCHAR(120) NOT NULL,
    zip_code VARCHAR(12) NOT NULL,
    street VARCHAR(160) NOT NULL,
    street_number VARCHAR(20) NOT NULL,
    complement VARCHAR(80),
    district VARCHAR(80) NOT NULL,
    city VARCHAR(80) NOT NULL,
    state VARCHAR(2) NOT NULL,
    is_default BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE vl_category (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    slug VARCHAR(90) NOT NULL,
    description VARCHAR(400),
    sort_order INT NOT NULL,
    active BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_category_slug UNIQUE (slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE vl_product (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    category_id BIGINT,
    sku VARCHAR(40) NOT NULL,
    name VARCHAR(140) NOT NULL,
    slug VARCHAR(160) NOT NULL,
    description VARCHAR(2000),
    price DECIMAL(12,2) NOT NULL,
    compare_at_price DECIMAL(12,2),
    stock INT NOT NULL,
    low_stock_threshold INT NOT NULL,
    image_url VARCHAR(400),
    weight_grams INT NOT NULL,
    active BOOLEAN NOT NULL,
    featured BOOLEAN NOT NULL,
    version BIGINT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_product_sku UNIQUE (sku),
    CONSTRAINT uk_product_slug UNIQUE (slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE vl_cart (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_cart_user_id UNIQUE (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE vl_cart_item (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    cart_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_cart_item_cart_id_product_id UNIQUE (cart_id, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE vl_coupon (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(30) NOT NULL,
    description VARCHAR(200),
    discount_type VARCHAR(20) NOT NULL,
    discount_value DECIMAL(12,2) NOT NULL,
    min_subtotal DECIMAL(12,2),
    max_uses INT,
    used_count INT NOT NULL,
    starts_at DATETIME(6),
    ends_at DATETIME(6),
    active BOOLEAN NOT NULL,
    version BIGINT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_coupon_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE vl_order (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_number VARCHAR(20) NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    subtotal DECIMAL(12,2) NOT NULL,
    discount DECIMAL(12,2) NOT NULL,
    shipping_cost DECIMAL(12,2) NOT NULL,
    total DECIMAL(12,2) NOT NULL,
    coupon_code VARCHAR(30),
    ship_recipient VARCHAR(120) NOT NULL,
    ship_zip_code VARCHAR(12) NOT NULL,
    ship_street VARCHAR(160) NOT NULL,
    ship_number VARCHAR(20) NOT NULL,
    ship_complement VARCHAR(80),
    ship_district VARCHAR(80) NOT NULL,
    ship_city VARCHAR(80) NOT NULL,
    ship_state VARCHAR(2) NOT NULL,
    tracking_code VARCHAR(40),
    notes VARCHAR(500),
    placed_at DATETIME(6) NOT NULL,
    paid_at DATETIME(6),
    shipped_at DATETIME(6),
    delivered_at DATETIME(6),
    canceled_at DATETIME(6),
    version BIGINT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_order_order_number UNIQUE (order_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE vl_order_item (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT,
    sku VARCHAR(40) NOT NULL,
    product_name VARCHAR(140) NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    quantity INT NOT NULL,
    line_total DECIMAL(12,2) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE vl_order_status_history (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    note VARCHAR(300),
    user_id BIGINT,
    created_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE vl_payment (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    provider VARCHAR(20) NOT NULL,
    method VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    installments INT NOT NULL,
    external_id VARCHAR(120),
    checkout_url VARCHAR(500),
    card_brand VARCHAR(20),
    card_last4 VARCHAR(4),
    pix_code VARCHAR(300),
    boleto_line VARCHAR(60),
    failure_reason VARCHAR(200),
    refunded_amount DECIMAL(12,2) NOT NULL,
    paid_at DATETIME(6),
    expires_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE vl_inventory_movement (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    movement_type VARCHAR(20) NOT NULL,
    quantity INT NOT NULL,
    stock_after INT NOT NULL,
    reason VARCHAR(200),
    order_id BIGINT,
    user_id BIGINT,
    created_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_user_user_role ON vl_user (user_role);
CREATE INDEX ix_session_user_id ON vl_session (user_id);
CREATE INDEX ix_address_user_id ON vl_address (user_id);
CREATE INDEX ix_product_category_id ON vl_product (category_id);
CREATE INDEX ix_product_active ON vl_product (active);
CREATE INDEX ix_order_user_id ON vl_order (user_id);
CREATE INDEX ix_order_status ON vl_order (status);
CREATE INDEX ix_order_placed_at ON vl_order (placed_at);
CREATE INDEX ix_order_item_order_id ON vl_order_item (order_id);
CREATE INDEX ix_order_item_product_id ON vl_order_item (product_id);
CREATE INDEX ix_order_status_history_order_id ON vl_order_status_history (order_id);
CREATE INDEX ix_payment_order_id ON vl_payment (order_id);
CREATE INDEX ix_payment_external_id ON vl_payment (external_id);
CREATE INDEX ix_inventory_movement_product_id ON vl_inventory_movement (product_id);
CREATE INDEX ix_inventory_movement_order_id ON vl_inventory_movement (order_id);
ALTER TABLE vl_session ADD CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES vl_user (id);
ALTER TABLE vl_address ADD CONSTRAINT fk_address_user FOREIGN KEY (user_id) REFERENCES vl_user (id);
ALTER TABLE vl_product ADD CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES vl_category (id);
ALTER TABLE vl_cart ADD CONSTRAINT fk_cart_user FOREIGN KEY (user_id) REFERENCES vl_user (id);
ALTER TABLE vl_cart_item ADD CONSTRAINT fk_cart_item_cart FOREIGN KEY (cart_id) REFERENCES vl_cart (id);
ALTER TABLE vl_cart_item ADD CONSTRAINT fk_cart_item_product FOREIGN KEY (product_id) REFERENCES vl_product (id);
ALTER TABLE vl_order ADD CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES vl_user (id);
ALTER TABLE vl_order_item ADD CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES vl_order (id);
ALTER TABLE vl_order_item ADD CONSTRAINT fk_order_item_product FOREIGN KEY (product_id) REFERENCES vl_product (id);
ALTER TABLE vl_order_status_history ADD CONSTRAINT fk_order_status_history_order FOREIGN KEY (order_id) REFERENCES vl_order (id);
ALTER TABLE vl_order_status_history ADD CONSTRAINT fk_order_status_history_user FOREIGN KEY (user_id) REFERENCES vl_user (id);
ALTER TABLE vl_payment ADD CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES vl_order (id);
ALTER TABLE vl_inventory_movement ADD CONSTRAINT fk_inventory_movement_product FOREIGN KEY (product_id) REFERENCES vl_product (id);
ALTER TABLE vl_inventory_movement ADD CONSTRAINT fk_inventory_movement_order FOREIGN KEY (order_id) REFERENCES vl_order (id);
ALTER TABLE vl_inventory_movement ADD CONSTRAINT fk_inventory_movement_user FOREIGN KEY (user_id) REFERENCES vl_user (id);

