CREATE TABLE vl_user (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    full_name NVARCHAR(120) NOT NULL,
    email NVARCHAR(160) NOT NULL,
    password_hash NVARCHAR(100) NOT NULL,
    user_role NVARCHAR(20) NOT NULL,
    phone NVARCHAR(30),
    document NVARCHAR(20),
    active BIT NOT NULL,
    failed_logins INT NOT NULL,
    locked_until DATETIME2(6),
    last_login_at DATETIME2(6),
    created_at DATETIME2(6) NOT NULL,
    updated_at DATETIME2(6) NOT NULL,
    CONSTRAINT uk_user_email UNIQUE (email)
);

CREATE TABLE vl_session (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash NVARCHAR(64) NOT NULL,
    created_at DATETIME2(6) NOT NULL,
    last_seen_at DATETIME2(6) NOT NULL,
    ended_at DATETIME2(6),
    end_reason NVARCHAR(20),
    ip_address NVARCHAR(45),
    user_agent NVARCHAR(255),
    CONSTRAINT uk_session_token_hash UNIQUE (token_hash)
);

CREATE TABLE vl_address (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    label NVARCHAR(40) NOT NULL,
    recipient NVARCHAR(120) NOT NULL,
    zip_code NVARCHAR(12) NOT NULL,
    street NVARCHAR(160) NOT NULL,
    street_number NVARCHAR(20) NOT NULL,
    complement NVARCHAR(80),
    district NVARCHAR(80) NOT NULL,
    city NVARCHAR(80) NOT NULL,
    state NVARCHAR(2) NOT NULL,
    is_default BIT NOT NULL,
    created_at DATETIME2(6) NOT NULL,
    updated_at DATETIME2(6) NOT NULL
);

CREATE TABLE vl_category (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    name NVARCHAR(80) NOT NULL,
    slug NVARCHAR(90) NOT NULL,
    description NVARCHAR(400),
    sort_order INT NOT NULL,
    active BIT NOT NULL,
    created_at DATETIME2(6) NOT NULL,
    updated_at DATETIME2(6) NOT NULL,
    CONSTRAINT uk_category_slug UNIQUE (slug)
);

CREATE TABLE vl_product (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    category_id BIGINT,
    sku NVARCHAR(40) NOT NULL,
    name NVARCHAR(140) NOT NULL,
    slug NVARCHAR(160) NOT NULL,
    description NVARCHAR(2000),
    price DECIMAL(12,2) NOT NULL,
    compare_at_price DECIMAL(12,2),
    stock INT NOT NULL,
    low_stock_threshold INT NOT NULL,
    image_url NVARCHAR(400),
    weight_grams INT NOT NULL,
    active BIT NOT NULL,
    featured BIT NOT NULL,
    version BIGINT,
    created_at DATETIME2(6) NOT NULL,
    updated_at DATETIME2(6) NOT NULL,
    CONSTRAINT uk_product_sku UNIQUE (sku),
    CONSTRAINT uk_product_slug UNIQUE (slug)
);

CREATE TABLE vl_cart (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    created_at DATETIME2(6) NOT NULL,
    updated_at DATETIME2(6) NOT NULL,
    CONSTRAINT uk_cart_user_id UNIQUE (user_id)
);

CREATE TABLE vl_cart_item (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    cart_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    created_at DATETIME2(6) NOT NULL,
    updated_at DATETIME2(6) NOT NULL,
    CONSTRAINT uk_cart_item_cart_id_product_id UNIQUE (cart_id, product_id)
);

CREATE TABLE vl_coupon (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    code NVARCHAR(30) NOT NULL,
    description NVARCHAR(200),
    discount_type NVARCHAR(20) NOT NULL,
    discount_value DECIMAL(12,2) NOT NULL,
    min_subtotal DECIMAL(12,2),
    max_uses INT,
    used_count INT NOT NULL,
    starts_at DATETIME2(6),
    ends_at DATETIME2(6),
    active BIT NOT NULL,
    version BIGINT,
    created_at DATETIME2(6) NOT NULL,
    updated_at DATETIME2(6) NOT NULL,
    CONSTRAINT uk_coupon_code UNIQUE (code)
);

CREATE TABLE vl_order (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    order_number NVARCHAR(20) NOT NULL,
    user_id BIGINT NOT NULL,
    status NVARCHAR(20) NOT NULL,
    subtotal DECIMAL(12,2) NOT NULL,
    discount DECIMAL(12,2) NOT NULL,
    shipping_cost DECIMAL(12,2) NOT NULL,
    total DECIMAL(12,2) NOT NULL,
    coupon_code NVARCHAR(30),
    ship_recipient NVARCHAR(120) NOT NULL,
    ship_zip_code NVARCHAR(12) NOT NULL,
    ship_street NVARCHAR(160) NOT NULL,
    ship_number NVARCHAR(20) NOT NULL,
    ship_complement NVARCHAR(80),
    ship_district NVARCHAR(80) NOT NULL,
    ship_city NVARCHAR(80) NOT NULL,
    ship_state NVARCHAR(2) NOT NULL,
    tracking_code NVARCHAR(40),
    notes NVARCHAR(500),
    placed_at DATETIME2(6) NOT NULL,
    paid_at DATETIME2(6),
    shipped_at DATETIME2(6),
    delivered_at DATETIME2(6),
    canceled_at DATETIME2(6),
    version BIGINT,
    created_at DATETIME2(6) NOT NULL,
    updated_at DATETIME2(6) NOT NULL,
    CONSTRAINT uk_order_order_number UNIQUE (order_number)
);

CREATE TABLE vl_order_item (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT,
    sku NVARCHAR(40) NOT NULL,
    product_name NVARCHAR(140) NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    quantity INT NOT NULL,
    line_total DECIMAL(12,2) NOT NULL
);

CREATE TABLE vl_order_status_history (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    from_status NVARCHAR(20),
    to_status NVARCHAR(20) NOT NULL,
    note NVARCHAR(300),
    user_id BIGINT,
    created_at DATETIME2(6) NOT NULL
);

CREATE TABLE vl_payment (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    provider NVARCHAR(20) NOT NULL,
    method NVARCHAR(20) NOT NULL,
    status NVARCHAR(20) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    currency NVARCHAR(3) NOT NULL,
    installments INT NOT NULL,
    external_id NVARCHAR(120),
    checkout_url NVARCHAR(500),
    card_brand NVARCHAR(20),
    card_last4 NVARCHAR(4),
    pix_code NVARCHAR(300),
    boleto_line NVARCHAR(60),
    failure_reason NVARCHAR(200),
    refunded_amount DECIMAL(12,2) NOT NULL,
    paid_at DATETIME2(6),
    expires_at DATETIME2(6),
    created_at DATETIME2(6) NOT NULL,
    updated_at DATETIME2(6) NOT NULL
);

CREATE TABLE vl_inventory_movement (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    movement_type NVARCHAR(20) NOT NULL,
    quantity INT NOT NULL,
    stock_after INT NOT NULL,
    reason NVARCHAR(200),
    order_id BIGINT,
    user_id BIGINT,
    created_at DATETIME2(6) NOT NULL
);

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

