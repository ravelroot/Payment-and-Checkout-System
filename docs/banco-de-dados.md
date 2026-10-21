# Banco de dados da Velo

A Velo funciona com o banco que você preferir. As tabelas são criadas sozinhas na primeira vez que a aplicação sobe, com o [Flyway](https://flywaydb.org), seguindo o mesmo modelo em todos os bancos.

| Banco | Versão mínima | Perfil | Script do esquema |
|---|---|---|---|
| H2 (arquivo local) | 2.2 | nenhum (padrão) | [`h2/V1__schema.sql`](../backend/src/main/resources/db/migration/h2/V1__schema.sql) |
| PostgreSQL | 13 | `postgresql` | [`postgresql/V1__schema.sql`](../backend/src/main/resources/db/migration/postgresql/V1__schema.sql) |
| MySQL | 8.0 | `mysql` | [`mysql/V1__schema.sql`](../backend/src/main/resources/db/migration/mysql/V1__schema.sql) |
| MariaDB | 10.6 | `mariadb` | [`mariadb/V1__schema.sql`](../backend/src/main/resources/db/migration/mariadb/V1__schema.sql) |
| SQL Server | 2019 | `sqlserver` | [`sqlserver/V1__schema.sql`](../backend/src/main/resources/db/migration/sqlserver/V1__schema.sql) |
| Oracle | 19c | `oracle` | [`oracle/V1__schema.sql`](../backend/src/main/resources/db/migration/oracle/V1__schema.sql) |

## Como parear o banco

### Sem configurar nada (H2)

Rode a aplicação. O banco é um arquivo em `backend/data/velo.mv.db`, criado na primeira execução com os dados de demonstração. Para começar do zero, pare a aplicação e apague a pasta `backend/data`.

### Com outro banco, em 3 passos

**1. Suba o banco.** Se você tem Docker, o arquivo [`docker-compose.yml`](../docker-compose.yml) já traz cada banco pronto, com usuário `velo` e banco `velo`:

```bash
docker compose --profile postgresql up -d
```

Troque `postgresql` por `mysql`, `mariadb`, `sqlserver` ou `oracle`. Sem Docker, instale o banco normalmente e rode o SQL de preparação do passo 2.

| Perfil do Docker | Porta | Primeira subida |
|---|---|---|
| `postgresql` | 5432 | poucos segundos |
| `mysql` | 3306 | cerca de 20 segundos |
| `mariadb` | 3307 | cerca de 15 segundos |
| `sqlserver` | 1433 | cerca de 30 segundos; o serviço `sqlserver-init` cria o banco e o usuário e encerra sozinho |
| `oracle` | 1521 | de 1 a 2 minutos; acompanhe com `docker compose logs -f oracle` até aparecer `DATABASE IS READY TO USE` |

Para desligar, use `docker compose --profile postgresql down`. Acrescente `-v` para apagar também os dados.

**2. Crie o banco e o usuário** (pule se usou o Docker). Troque `velo` pela senha que quiser.

<details>
<summary>PostgreSQL</summary>

```sql
CREATE USER velo WITH PASSWORD 'velo';
CREATE DATABASE velo OWNER velo ENCODING 'UTF8';
```
</details>

<details>
<summary>MySQL / MariaDB</summary>

```sql
CREATE DATABASE velo CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'velo'@'%' IDENTIFIED BY 'velo';
GRANT ALL PRIVILEGES ON velo.* TO 'velo'@'%';
FLUSH PRIVILEGES;
```
</details>

<details>
<summary>SQL Server</summary>

```sql
CREATE DATABASE velo COLLATE Latin1_General_100_CI_AS_SC_UTF8;
GO
CREATE LOGIN velo WITH PASSWORD = 'Velo@Senha2026';
GO
USE velo;
CREATE USER velo FOR LOGIN velo;
ALTER ROLE db_owner ADD MEMBER velo;
GO
```
</details>

<details>
<summary>Oracle</summary>

```sql
ALTER SESSION SET CONTAINER = FREEPDB1;
CREATE USER velo IDENTIFIED BY velo QUOTA UNLIMITED ON USERS;
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE VIEW TO velo;
```
</details>

**3. Aponte a Velo para o banco** escolhendo o perfil e, se precisar, o endereço e a senha:

```bash
# Linux / macOS
export SPRING_PROFILES_ACTIVE=postgresql
export VELO_DB_URL=jdbc:postgresql://localhost:5432/velo
export VELO_DB_USER=velo
export VELO_DB_PASSWORD=velo
```

```powershell
# Windows (PowerShell)
$env:SPRING_PROFILES_ACTIVE = "postgresql"
$env:VELO_DB_URL = "jdbc:postgresql://localhost:5432/velo"
$env:VELO_DB_USER = "velo"
$env:VELO_DB_PASSWORD = "velo"
```

Depois rode a aplicação como no [README](../README.md#como-rodar). Os perfis já trazem o endereço padrão de cada banco (`localhost`), então `VELO_DB_URL` só é necessária se o banco estiver em outra máquina ou porta.

| Perfil | URL padrão | Usuário / senha padrão |
|---|---|---|
| `postgresql` | `jdbc:postgresql://localhost:5432/velo` | `velo` / `velo` |
| `mysql` | `jdbc:mysql://localhost:3306/velo?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC` | `velo` / `velo` |
| `mariadb` | `jdbc:mariadb://localhost:3307/velo` | `velo` / `velo` |
| `sqlserver` | `jdbc:sqlserver://localhost:1433;databaseName=velo;encrypt=true;trustServerCertificate=true` | `velo` / `Velo@Senha2026` |
| `oracle` | `jdbc:oracle:thin:@//localhost:1521/FREEPDB1` | `velo` / `velo` |

Na primeira conexão o Flyway cria as 13 tabelas e registra a versão em `flyway_schema_history`. Com o banco vazio, a Velo também cadastra os dados de demonstração (usuários, produtos, cupons e pedidos de exemplo). Para não criar esses dados, defina `VELO_SEED=false`.

### Problemas comuns

| Sintoma | O que fazer |
|---|---|
| `Connection refused` | O banco não está rodando ou a porta está diferente. Confira com `docker compose ps` ou ajuste `VELO_DB_URL`. |
| `password authentication failed` / `Login failed` | Usuário ou senha diferentes dos informados em `VELO_DB_USER` e `VELO_DB_PASSWORD`. |
| `Validate failed: Migrations have failed validation` | O banco já tem outro esquema. Use um banco vazio só para a Velo. |
| SQL Server: `The server selected protocol version TLS10` | Atualize o SQL Server ou mantenha `trustServerCertificate=true` na URL. |
| Oracle: `ORA-01950: no privileges on tablespace` | Dê cota ao usuário: `ALTER USER velo QUOTA UNLIMITED ON USERS;` |

## Convenções do modelo

- Todas as tabelas começam com `vl_` e as chaves primárias se chamam `id` (inteiro de 64 bits, autoincremento).
- Datas e horas ficam em **UTC**. A tela converte para o horário de Brasília.
- Valores em dinheiro usam `decimal(12,2)` e a moeda é sempre BRL.
- Situações e tipos são gravados como texto em maiúsculas (por exemplo `PENDING_PAYMENT`), para continuarem legíveis em qualquer banco.
- Pedidos copiam o endereço e o nome/preço de cada item no momento da compra. Alterar um produto depois não muda pedidos antigos.
- Senhas são guardadas só como hash BCrypt. Sessões guardam apenas o SHA-256 do token. Números de cartão nunca são gravados, só a bandeira e os 4 últimos dígitos.
- `version` nas tabelas de produto, cupom e pedido evita que duas pessoas sobrescrevam a mesma alteração.

### Tipos em cada banco

| Tipo no modelo | H2 / PostgreSQL | MySQL / MariaDB | SQL Server | Oracle |
|---|---|---|---|---|
| bigint, autoincremento | `BIGINT GENERATED BY DEFAULT AS IDENTITY` | `BIGINT AUTO_INCREMENT` | `BIGINT IDENTITY(1,1)` | `NUMBER(19) GENERATED BY DEFAULT ON NULL AS IDENTITY` |
| bigint | `BIGINT` | `BIGINT` | `BIGINT` | `NUMBER(19)` |
| inteiro | `INTEGER` | `INT` | `INT` | `NUMBER(10)` |
| texto(n) | `VARCHAR(n)` | `VARCHAR(n)` utf8mb4 | `NVARCHAR(n)` | `VARCHAR2(n CHAR)` |
| decimal(12,2) | `NUMERIC(12,2)` | `DECIMAL(12,2)` | `DECIMAL(12,2)` | `NUMBER(12,2)` |
| booleano | `BOOLEAN` | `BOOLEAN` | `BIT` | `NUMBER(1)` |
| data e hora | `TIMESTAMP(6)` | `DATETIME(6)` | `DATETIME2(6)` | `TIMESTAMP(6)` |

## Diagrama de relacionamentos

PK = chave primária, FK = chave estrangeira, UK = valor único.

```mermaid
erDiagram
    vl_user ||--o{ vl_session : "abre"
    vl_user ||--o{ vl_address : "cadastra"
    vl_user ||--o| vl_cart : "tem"
    vl_cart ||--o{ vl_cart_item : "contém"
    vl_product ||--o{ vl_cart_item : "está em"
    vl_category |o--o{ vl_product : "agrupa"
    vl_user ||--o{ vl_order : "faz"
    vl_order ||--|{ vl_order_item : "contém"
    vl_product |o--o{ vl_order_item : "vendido em"
    vl_order ||--o{ vl_order_status_history : "registra"
    vl_order ||--o{ vl_payment : "é pago por"
    vl_product ||--o{ vl_inventory_movement : "movimenta"
    vl_order |o--o{ vl_inventory_movement : "gera"
    vl_user |o--o{ vl_inventory_movement : "faz"
    vl_user |o--o{ vl_order_status_history : "altera"
    vl_user {
        bigint id PK
        varchar full_name
        varchar email UK
        varchar password_hash
        varchar user_role
        varchar phone
        varchar document
        boolean active
        int failed_logins
        timestamp locked_until
        timestamp last_login_at
        timestamp created_at
        timestamp updated_at
    }
    vl_session {
        bigint id PK
        bigint user_id FK
        varchar token_hash UK
        timestamp created_at
        timestamp last_seen_at
        timestamp ended_at
        varchar end_reason
        varchar ip_address
        varchar user_agent
    }
    vl_address {
        bigint id PK
        bigint user_id FK
        varchar label
        varchar recipient
        varchar zip_code
        varchar street
        varchar street_number
        varchar complement
        varchar district
        varchar city
        varchar state
        boolean is_default
        timestamp created_at
        timestamp updated_at
    }
    vl_category {
        bigint id PK
        varchar name
        varchar slug UK
        varchar description
        int sort_order
        boolean active
        timestamp created_at
        timestamp updated_at
    }
    vl_product {
        bigint id PK
        bigint category_id FK
        varchar sku UK
        varchar name
        varchar slug UK
        varchar description
        decimal price
        decimal compare_at_price
        int stock
        int low_stock_threshold
        varchar image_url
        int weight_grams
        boolean active
        boolean featured
        bigint version
        timestamp created_at
        timestamp updated_at
    }
    vl_cart {
        bigint id PK
        bigint user_id FK,UK
        timestamp created_at
        timestamp updated_at
    }
    vl_cart_item {
        bigint id PK
        bigint cart_id FK
        bigint product_id FK
        int quantity
        timestamp created_at
        timestamp updated_at
    }
    vl_coupon {
        bigint id PK
        varchar code UK
        varchar description
        varchar discount_type
        decimal discount_value
        decimal min_subtotal
        int max_uses
        int used_count
        timestamp starts_at
        timestamp ends_at
        boolean active
        bigint version
        timestamp created_at
        timestamp updated_at
    }
    vl_order {
        bigint id PK
        varchar order_number UK
        bigint user_id FK
        varchar status
        decimal subtotal
        decimal discount
        decimal shipping_cost
        decimal total
        varchar coupon_code
        varchar ship_recipient
        varchar ship_zip_code
        varchar ship_street
        varchar ship_number
        varchar ship_complement
        varchar ship_district
        varchar ship_city
        varchar ship_state
        varchar tracking_code
        varchar notes
        timestamp placed_at
        timestamp paid_at
        timestamp shipped_at
        timestamp delivered_at
        timestamp canceled_at
        bigint version
        timestamp created_at
        timestamp updated_at
    }
    vl_order_item {
        bigint id PK
        bigint order_id FK
        bigint product_id FK
        varchar sku
        varchar product_name
        decimal unit_price
        int quantity
        decimal line_total
    }
    vl_order_status_history {
        bigint id PK
        bigint order_id FK
        varchar from_status
        varchar to_status
        varchar note
        bigint user_id FK
        timestamp created_at
    }
    vl_payment {
        bigint id PK
        bigint order_id FK
        varchar provider
        varchar method
        varchar status
        decimal amount
        varchar currency
        int installments
        varchar external_id
        varchar checkout_url
        varchar card_brand
        varchar card_last4
        varchar pix_code
        varchar boleto_line
        varchar failure_reason
        decimal refunded_amount
        timestamp paid_at
        timestamp expires_at
        timestamp created_at
        timestamp updated_at
    }
    vl_inventory_movement {
        bigint id PK
        bigint product_id FK
        varchar movement_type
        int quantity
        int stock_after
        varchar reason
        bigint order_id FK
        bigint user_id FK
        timestamp created_at
    }
```

### Como ler as ligações

- Um **usuário** tem várias **sessões**, vários **endereços**, um **carrinho** e vários **pedidos**.
- Um **carrinho** tem vários **itens**, cada um apontando para um **produto** (um mesmo produto aparece uma vez por carrinho).
- Um **produto** pertence a no máximo uma **categoria** e tem um histórico de **movimentos de estoque**.
- Um **pedido** tem um ou mais **itens**, uma **linha do tempo** de situações e um ou mais **pagamentos** (uma nova tentativa cria um novo pagamento).
- **Cupons** são ligados ao pedido pelo código (`vl_order.coupon_code`), sem chave estrangeira, para o pedido continuar íntegro mesmo se o cupom for excluído.

### Fluxo de situações do pedido

```mermaid
stateDiagram-v2
    [*] --> PENDING_PAYMENT: pedido realizado
    PENDING_PAYMENT --> PAID: pagamento confirmado
    PENDING_PAYMENT --> CANCELED: cancelado ou prazo de pagamento vencido
    PAID --> PROCESSING: separação
    PAID --> CANCELED: cancelado com estorno
    PROCESSING --> SHIPPED: enviado com código de rastreio
    PROCESSING --> CANCELED: cancelado com estorno
    SHIPPED --> DELIVERED: entregue
    DELIVERED --> REFUNDED: devolução com estorno
    CANCELED --> [*]
    REFUNDED --> [*]
```

O estoque sai no momento da compra (movimento `SALE`) e volta quando o pedido é cancelado ou estornado (movimento `RETURN`). Pedidos não pagos em 60 minutos são cancelados automaticamente.

## Tabelas

### `vl_user`

Pessoas que acessam a Velo: clientes, equipe e administradores.

| Coluna | Tipo | Obrigatório | Chave | Descrição |
|---|---|---|---|---|
| `id` | bigint, autoincremento | sim | PK |  |
| `full_name` | texto(120) | sim |  | Nome completo |
| `email` | texto(160) | sim | Único | E-mail de login, único |
| `password_hash` | texto(100) | sim |  | Hash BCrypt da senha |
| `user_role` | texto(20) | sim |  | Perfil de acesso: `ADMIN`, `STAFF`, `CUSTOMER` |
| `phone` | texto(30) | não |  | Telefone |
| `document` | texto(20) | não |  | CPF ou CNPJ |
| `active` | booleano | sim |  | Conta ativa |
| `failed_logins` | inteiro | sim |  | Tentativas de login erradas seguidas |
| `locked_until` | data e hora | não |  | Bloqueio temporário após várias tentativas erradas |
| `last_login_at` | data e hora | não |  | Último login |
| `created_at` | data e hora | sim |  | Data de criação (UTC) |
| `updated_at` | data e hora | sim |  | Última alteração (UTC) |

Índices: `user_role`.

### `vl_session`

Sessões de login. O token só existe na memória do navegador; aqui fica apenas o hash.

| Coluna | Tipo | Obrigatório | Chave | Descrição |
|---|---|---|---|---|
| `id` | bigint, autoincremento | sim | PK |  |
| `user_id` | bigint | sim | FK → `vl_user.id` | Dono da sessão |
| `token_hash` | texto(64) | sim | Único | SHA-256 do token entregue ao navegador |
| `created_at` | data e hora | sim |  | Início da sessão; limite de 30 minutos a partir daqui |
| `last_seen_at` | data e hora | sim |  | Última atividade; limite de 20 minutos parado |
| `ended_at` | data e hora | não |  | Quando a sessão foi encerrada |
| `end_reason` | texto(20) | não |  | Motivo do encerramento: `LOGOUT`, `IDLE`, `EXPIRED`, `REVOKED` |
| `ip_address` | texto(45) | não |  | IP de origem |
| `user_agent` | texto(255) | não |  | Navegador |

Índices: `user_id`.

### `vl_address`

Endereços de entrega dos clientes.

| Coluna | Tipo | Obrigatório | Chave | Descrição |
|---|---|---|---|---|
| `id` | bigint, autoincremento | sim | PK |  |
| `user_id` | bigint | sim | FK → `vl_user.id` | Cliente |
| `label` | texto(40) | sim |  | Apelido (Casa, Trabalho) |
| `recipient` | texto(120) | sim |  | Quem recebe |
| `zip_code` | texto(12) | sim |  | CEP |
| `street` | texto(160) | sim |  | Logradouro |
| `street_number` | texto(20) | sim |  | Número |
| `complement` | texto(80) | não |  | Complemento |
| `district` | texto(80) | sim |  | Bairro |
| `city` | texto(80) | sim |  | Cidade |
| `state` | texto(2) | sim |  | UF |
| `is_default` | booleano | sim |  | Endereço principal |
| `created_at` | data e hora | sim |  | Data de criação (UTC) |
| `updated_at` | data e hora | sim |  | Última alteração (UTC) |

Índices: `user_id`.

### `vl_category`

Categorias do catálogo.

| Coluna | Tipo | Obrigatório | Chave | Descrição |
|---|---|---|---|---|
| `id` | bigint, autoincremento | sim | PK |  |
| `name` | texto(80) | sim |  | Nome |
| `slug` | texto(90) | sim | Único | Identificador na URL, único |
| `description` | texto(400) | não |  | Descrição |
| `sort_order` | inteiro | sim |  | Ordem de exibição |
| `active` | booleano | sim |  | Visível na loja |
| `created_at` | data e hora | sim |  | Data de criação (UTC) |
| `updated_at` | data e hora | sim |  | Última alteração (UTC) |

### `vl_product`

Produtos à venda, com preço e estoque.

| Coluna | Tipo | Obrigatório | Chave | Descrição |
|---|---|---|---|---|
| `id` | bigint, autoincremento | sim | PK |  |
| `category_id` | bigint | não | FK → `vl_category.id` | Categoria |
| `sku` | texto(40) | sim | Único | Código do produto, único |
| `name` | texto(140) | sim |  | Nome |
| `slug` | texto(160) | sim | Único | Identificador na URL, único |
| `description` | texto(2000) | não |  | Descrição |
| `price` | decimal(12,2) | sim |  | Preço de venda |
| `compare_at_price` | decimal(12,2) | não |  | Preço antigo, para mostrar desconto |
| `stock` | inteiro | sim |  | Quantidade disponível |
| `low_stock_threshold` | inteiro | sim |  | Alerta de estoque baixo |
| `image_url` | texto(400) | não |  | Imagem principal |
| `weight_grams` | inteiro | sim |  | Peso para frete |
| `active` | booleano | sim |  | Visível na loja |
| `featured` | booleano | sim |  | Destaque na vitrine |
| `version` | bigint | não |  | Controle de concorrência |
| `created_at` | data e hora | sim |  | Data de criação (UTC) |
| `updated_at` | data e hora | sim |  | Última alteração (UTC) |

Índices: `category_id`, `active`.

### `vl_cart`

Carrinho de compras, um por cliente.

| Coluna | Tipo | Obrigatório | Chave | Descrição |
|---|---|---|---|---|
| `id` | bigint, autoincremento | sim | PK |  |
| `user_id` | bigint | sim | FK → `vl_user.id` · Único | Cliente |
| `created_at` | data e hora | sim |  | Data de criação (UTC) |
| `updated_at` | data e hora | sim |  | Última alteração (UTC) |

### `vl_cart_item`

Itens do carrinho.

| Coluna | Tipo | Obrigatório | Chave | Descrição |
|---|---|---|---|---|
| `id` | bigint, autoincremento | sim | PK |  |
| `cart_id` | bigint | sim | FK → `vl_cart.id` | Carrinho |
| `product_id` | bigint | sim | FK → `vl_product.id` | Produto |
| `quantity` | inteiro | sim |  | Quantidade |
| `created_at` | data e hora | sim |  | Data de criação (UTC) |
| `updated_at` | data e hora | sim |  | Última alteração (UTC) |

Restrição de unicidade: (`cart_id`, `product_id`).

### `vl_coupon`

Cupons de desconto.

| Coluna | Tipo | Obrigatório | Chave | Descrição |
|---|---|---|---|---|
| `id` | bigint, autoincremento | sim | PK |  |
| `code` | texto(30) | sim | Único | Código digitado pelo cliente, único |
| `description` | texto(200) | não |  | Descrição |
| `discount_type` | texto(20) | sim |  | Tipo de desconto: `PERCENT`, `FIXED` |
| `discount_value` | decimal(12,2) | sim |  | Percentual ou valor |
| `min_subtotal` | decimal(12,2) | não |  | Subtotal mínimo |
| `max_uses` | inteiro | não |  | Limite de usos |
| `used_count` | inteiro | sim |  | Usos até agora |
| `starts_at` | data e hora | não |  | Início da validade |
| `ends_at` | data e hora | não |  | Fim da validade |
| `active` | booleano | sim |  | Ativo |
| `version` | bigint | não |  | Controle de concorrência |
| `created_at` | data e hora | sim |  | Data de criação (UTC) |
| `updated_at` | data e hora | sim |  | Última alteração (UTC) |

### `vl_order`

Pedidos. O endereço é copiado no momento da compra.

| Coluna | Tipo | Obrigatório | Chave | Descrição |
|---|---|---|---|---|
| `id` | bigint, autoincremento | sim | PK |  |
| `order_number` | texto(20) | sim | Único | Número exibido ao cliente, único |
| `user_id` | bigint | sim | FK → `vl_user.id` | Cliente |
| `status` | texto(20) | sim |  | Situação: `PENDING_PAYMENT`, `PAID`, `PROCESSING`, `SHIPPED`, `DELIVERED`, `CANCELED`, `REFUNDED` |
| `subtotal` | decimal(12,2) | sim |  | Soma dos itens |
| `discount` | decimal(12,2) | sim |  | Desconto do cupom |
| `shipping_cost` | decimal(12,2) | sim |  | Frete |
| `total` | decimal(12,2) | sim |  | Total cobrado |
| `coupon_code` | texto(30) | não |  | Cupom usado |
| `ship_recipient` | texto(120) | sim |  | Destinatário |
| `ship_zip_code` | texto(12) | sim |  | CEP |
| `ship_street` | texto(160) | sim |  | Logradouro |
| `ship_number` | texto(20) | sim |  | Número |
| `ship_complement` | texto(80) | não |  | Complemento |
| `ship_district` | texto(80) | sim |  | Bairro |
| `ship_city` | texto(80) | sim |  | Cidade |
| `ship_state` | texto(2) | sim |  | UF |
| `tracking_code` | texto(40) | não |  | Código de rastreio |
| `notes` | texto(500) | não |  | Observações |
| `placed_at` | data e hora | sim |  | Data da compra |
| `paid_at` | data e hora | não |  | Pagamento confirmado |
| `shipped_at` | data e hora | não |  | Enviado |
| `delivered_at` | data e hora | não |  | Entregue |
| `canceled_at` | data e hora | não |  | Cancelado |
| `version` | bigint | não |  | Controle de concorrência |
| `created_at` | data e hora | sim |  | Data de criação (UTC) |
| `updated_at` | data e hora | sim |  | Última alteração (UTC) |

Índices: `user_id`, `status`, `placed_at`.

### `vl_order_item`

Itens do pedido, com nome e preço copiados na hora da compra.

| Coluna | Tipo | Obrigatório | Chave | Descrição |
|---|---|---|---|---|
| `id` | bigint, autoincremento | sim | PK |  |
| `order_id` | bigint | sim | FK → `vl_order.id` | Pedido |
| `product_id` | bigint | não | FK → `vl_product.id` | Produto (pode ficar vazio se o produto for excluído) |
| `sku` | texto(40) | sim |  | Código na hora da compra |
| `product_name` | texto(140) | sim |  | Nome na hora da compra |
| `unit_price` | decimal(12,2) | sim |  | Preço unitário |
| `quantity` | inteiro | sim |  | Quantidade |
| `line_total` | decimal(12,2) | sim |  | Preço x quantidade |

Índices: `order_id`, `product_id`.

### `vl_order_status_history`

Linha do tempo de cada pedido.

| Coluna | Tipo | Obrigatório | Chave | Descrição |
|---|---|---|---|---|
| `id` | bigint, autoincremento | sim | PK |  |
| `order_id` | bigint | sim | FK → `vl_order.id` | Pedido |
| `from_status` | texto(20) | não |  | Situação anterior |
| `to_status` | texto(20) | sim |  | Nova situação |
| `note` | texto(300) | não |  | Observação |
| `user_id` | bigint | não | FK → `vl_user.id` | Quem alterou (vazio = sistema) |
| `created_at` | data e hora | sim |  | Quando |

Índices: `order_id`.

### `vl_payment`

Pagamentos dos pedidos. Número de cartão nunca é gravado.

| Coluna | Tipo | Obrigatório | Chave | Descrição |
|---|---|---|---|---|
| `id` | bigint, autoincremento | sim | PK |  |
| `order_id` | bigint | sim | FK → `vl_order.id` | Pedido |
| `provider` | texto(20) | sim |  | Provedor: `SIMULATED`, `STRIPE` |
| `method` | texto(20) | sim |  | Forma de pagamento: `CARD`, `PIX`, `BOLETO`, `CHECKOUT` |
| `status` | texto(20) | sim |  | Situação: `PENDING`, `PAID`, `FAILED`, `CANCELED`, `REFUNDED` |
| `amount` | decimal(12,2) | sim |  | Valor |
| `currency` | texto(3) | sim |  | Moeda (BRL) |
| `installments` | inteiro | sim |  | Parcelas |
| `external_id` | texto(120) | não |  | Identificador no provedor |
| `checkout_url` | texto(500) | não |  | Página de pagamento do provedor |
| `card_brand` | texto(20) | não |  | Bandeira |
| `card_last4` | texto(4) | não |  | Últimos 4 dígitos |
| `pix_code` | texto(300) | não |  | Código PIX copia e cola |
| `boleto_line` | texto(60) | não |  | Linha digitável do boleto |
| `failure_reason` | texto(200) | não |  | Motivo da recusa |
| `refunded_amount` | decimal(12,2) | sim |  | Valor estornado |
| `paid_at` | data e hora | não |  | Confirmação |
| `expires_at` | data e hora | não |  | Validade do PIX ou boleto |
| `created_at` | data e hora | sim |  | Data de criação (UTC) |
| `updated_at` | data e hora | sim |  | Última alteração (UTC) |

Índices: `order_id`, `external_id`.

### `vl_inventory_movement`

Histórico de entradas e saídas de estoque.

| Coluna | Tipo | Obrigatório | Chave | Descrição |
|---|---|---|---|---|
| `id` | bigint, autoincremento | sim | PK |  |
| `product_id` | bigint | sim | FK → `vl_product.id` | Produto |
| `movement_type` | texto(20) | sim |  | Tipo: `IN`, `OUT`, `SALE`, `RETURN`, `ADJUST` |
| `quantity` | inteiro | sim |  | Quantidade (positiva entra, negativa sai) |
| `stock_after` | inteiro | sim |  | Estoque depois do movimento |
| `reason` | texto(200) | não |  | Motivo |
| `order_id` | bigint | não | FK → `vl_order.id` | Pedido relacionado |
| `user_id` | bigint | não | FK → `vl_user.id` | Quem fez |
| `created_at` | data e hora | sim |  | Quando |

Índices: `product_id`, `order_id`.

## Evolução do esquema

O arquivo `V1__schema.sql` de cada banco é gerado por [`scripts/schema/gerar_esquema.py`](../scripts/schema/gerar_esquema.py), a fonte única do modelo. Para mudar o esquema depois da primeira versão publicada:

1. Altere a entidade Java e o modelo em `gerar_esquema.py`.
2. Crie um `V2__descricao.sql` em cada pasta de `db/migration` com o `ALTER TABLE` correspondente. Nunca edite um `V1` já aplicado, pois o Flyway recusa scripts alterados.
3. Rode `python scripts/schema/gerar_esquema.py --somente-doc` para atualizar este documento sem tocar no `V1`.
