import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MIGRATIONS = ROOT / "backend" / "src" / "main" / "resources" / "db" / "migration"
DOC = ROOT / "docs" / "banco-de-dados.md"

VENDORS = ["h2", "postgresql", "mysql", "mariadb", "sqlserver", "oracle"]


def col(name, kind, size=None, null=True, desc="", values=None, default=None):
    return {"name": name, "kind": kind, "size": size, "null": null, "desc": desc, "values": values, "default": default}


def audit():
    return [
        col("created_at", "timestamp", null=False, desc="Data de criação (UTC)"),
        col("updated_at", "timestamp", null=False, desc="Última alteração (UTC)"),
    ]


TABLES = [
    {
        "name": "vl_user",
        "desc": "Pessoas que acessam a Velo: clientes, equipe e administradores.",
        "cols": [
            col("id", "id"),
            col("full_name", "varchar", 120, False, "Nome completo"),
            col("email", "varchar", 160, False, "E-mail de login, único"),
            col("password_hash", "varchar", 100, False, "Hash BCrypt da senha"),
            col("user_role", "varchar", 20, False, "Perfil de acesso", values=["ADMIN", "STAFF", "CUSTOMER"]),
            col("phone", "varchar", 30, desc="Telefone"),
            col("document", "varchar", 20, desc="CPF ou CNPJ"),
            col("active", "bool", null=False, desc="Conta ativa"),
            col("failed_logins", "int", null=False, desc="Tentativas de login erradas seguidas"),
            col("locked_until", "timestamp", desc="Bloqueio temporário após várias tentativas erradas"),
            col("last_login_at", "timestamp", desc="Último login"),
        ] + audit(),
        "unique": [["email"]],
        "index": [["user_role"]],
        "fk": [],
    },
    {
        "name": "vl_session",
        "desc": "Sessões de login. O token só existe na memória do navegador; aqui fica apenas o hash.",
        "cols": [
            col("id", "id"),
            col("user_id", "bigint", null=False, desc="Dono da sessão"),
            col("token_hash", "varchar", 64, False, "SHA-256 do token entregue ao navegador"),
            col("created_at", "timestamp", null=False, desc="Início da sessão; limite de 30 minutos a partir daqui"),
            col("last_seen_at", "timestamp", null=False, desc="Última atividade; limite de 20 minutos parado"),
            col("ended_at", "timestamp", desc="Quando a sessão foi encerrada"),
            col("end_reason", "varchar", 20, desc="Motivo do encerramento", values=["LOGOUT", "IDLE", "EXPIRED", "REVOKED"]),
            col("ip_address", "varchar", 45, desc="IP de origem"),
            col("user_agent", "varchar", 255, desc="Navegador"),
        ],
        "unique": [["token_hash"]],
        "index": [["user_id"]],
        "fk": [("user_id", "vl_user")],
    },
    {
        "name": "vl_address",
        "desc": "Endereços de entrega dos clientes.",
        "cols": [
            col("id", "id"),
            col("user_id", "bigint", null=False, desc="Cliente"),
            col("label", "varchar", 40, False, "Apelido (Casa, Trabalho)"),
            col("recipient", "varchar", 120, False, "Quem recebe"),
            col("zip_code", "varchar", 12, False, "CEP"),
            col("street", "varchar", 160, False, "Logradouro"),
            col("street_number", "varchar", 20, False, "Número"),
            col("complement", "varchar", 80, desc="Complemento"),
            col("district", "varchar", 80, False, "Bairro"),
            col("city", "varchar", 80, False, "Cidade"),
            col("state", "varchar", 2, False, "UF"),
            col("is_default", "bool", null=False, desc="Endereço principal"),
        ] + audit(),
        "unique": [],
        "index": [["user_id"]],
        "fk": [("user_id", "vl_user")],
    },
    {
        "name": "vl_category",
        "desc": "Categorias do catálogo.",
        "cols": [
            col("id", "id"),
            col("name", "varchar", 80, False, "Nome"),
            col("slug", "varchar", 90, False, "Identificador na URL, único"),
            col("description", "varchar", 400, desc="Descrição"),
            col("sort_order", "int", null=False, desc="Ordem de exibição"),
            col("active", "bool", null=False, desc="Visível na loja"),
        ] + audit(),
        "unique": [["slug"]],
        "index": [],
        "fk": [],
    },
    {
        "name": "vl_product",
        "desc": "Produtos à venda, com preço e estoque.",
        "cols": [
            col("id", "id"),
            col("category_id", "bigint", desc="Categoria"),
            col("sku", "varchar", 40, False, "Código do produto, único"),
            col("name", "varchar", 140, False, "Nome"),
            col("slug", "varchar", 160, False, "Identificador na URL, único"),
            col("description", "varchar", 2000, desc="Descrição"),
            col("price", "decimal", (12, 2), False, "Preço de venda"),
            col("compare_at_price", "decimal", (12, 2), desc="Preço antigo, para mostrar desconto"),
            col("stock", "int", null=False, desc="Quantidade disponível"),
            col("low_stock_threshold", "int", null=False, desc="Alerta de estoque baixo"),
            col("image_url", "varchar", 400, desc="Imagem principal"),
            col("weight_grams", "int", null=False, desc="Peso para frete"),
            col("active", "bool", null=False, desc="Visível na loja"),
            col("featured", "bool", null=False, desc="Destaque na vitrine"),
            col("version", "bigint", desc="Controle de concorrência"),
        ] + audit(),
        "unique": [["sku"], ["slug"]],
        "index": [["category_id"], ["active"]],
        "fk": [("category_id", "vl_category")],
    },
    {
        "name": "vl_inventory_movement",
        "desc": "Histórico de entradas e saídas de estoque.",
        "cols": [
            col("id", "id"),
            col("product_id", "bigint", null=False, desc="Produto"),
            col("movement_type", "varchar", 20, False, "Tipo", values=["IN", "OUT", "SALE", "RETURN", "ADJUST"]),
            col("quantity", "int", null=False, desc="Quantidade (positiva entra, negativa sai)"),
            col("stock_after", "int", null=False, desc="Estoque depois do movimento"),
            col("reason", "varchar", 200, desc="Motivo"),
            col("order_id", "bigint", desc="Pedido relacionado"),
            col("user_id", "bigint", desc="Quem fez"),
            col("created_at", "timestamp", null=False, desc="Quando"),
        ],
        "unique": [],
        "index": [["product_id"], ["order_id"]],
        "fk": [("product_id", "vl_product"), ("order_id", "vl_order"), ("user_id", "vl_user")],
    },
    {
        "name": "vl_cart",
        "desc": "Carrinho de compras, um por cliente.",
        "cols": [col("id", "id"), col("user_id", "bigint", null=False, desc="Cliente")] + audit(),
        "unique": [["user_id"]],
        "index": [],
        "fk": [("user_id", "vl_user")],
    },
    {
        "name": "vl_cart_item",
        "desc": "Itens do carrinho.",
        "cols": [
            col("id", "id"),
            col("cart_id", "bigint", null=False, desc="Carrinho"),
            col("product_id", "bigint", null=False, desc="Produto"),
            col("quantity", "int", null=False, desc="Quantidade"),
        ] + audit(),
        "unique": [["cart_id", "product_id"]],
        "index": [],
        "fk": [("cart_id", "vl_cart"), ("product_id", "vl_product")],
    },
    {
        "name": "vl_coupon",
        "desc": "Cupons de desconto.",
        "cols": [
            col("id", "id"),
            col("code", "varchar", 30, False, "Código digitado pelo cliente, único"),
            col("description", "varchar", 200, desc="Descrição"),
            col("discount_type", "varchar", 20, False, "Tipo de desconto", values=["PERCENT", "FIXED"]),
            col("discount_value", "decimal", (12, 2), False, "Percentual ou valor"),
            col("min_subtotal", "decimal", (12, 2), desc="Subtotal mínimo"),
            col("max_uses", "int", desc="Limite de usos"),
            col("used_count", "int", null=False, desc="Usos até agora"),
            col("starts_at", "timestamp", desc="Início da validade"),
            col("ends_at", "timestamp", desc="Fim da validade"),
            col("active", "bool", null=False, desc="Ativo"),
            col("version", "bigint", desc="Controle de concorrência"),
        ] + audit(),
        "unique": [["code"]],
        "index": [],
        "fk": [],
    },
    {
        "name": "vl_order",
        "desc": "Pedidos. O endereço é copiado no momento da compra.",
        "cols": [
            col("id", "id"),
            col("order_number", "varchar", 20, False, "Número exibido ao cliente, único"),
            col("user_id", "bigint", null=False, desc="Cliente"),
            col("status", "varchar", 20, False, "Situação",
                values=["PENDING_PAYMENT", "PAID", "PROCESSING", "SHIPPED", "DELIVERED", "CANCELED", "REFUNDED"]),
            col("subtotal", "decimal", (12, 2), False, "Soma dos itens"),
            col("discount", "decimal", (12, 2), False, "Desconto do cupom"),
            col("shipping_cost", "decimal", (12, 2), False, "Frete"),
            col("total", "decimal", (12, 2), False, "Total cobrado"),
            col("coupon_code", "varchar", 30, desc="Cupom usado"),
            col("ship_recipient", "varchar", 120, False, "Destinatário"),
            col("ship_zip_code", "varchar", 12, False, "CEP"),
            col("ship_street", "varchar", 160, False, "Logradouro"),
            col("ship_number", "varchar", 20, False, "Número"),
            col("ship_complement", "varchar", 80, desc="Complemento"),
            col("ship_district", "varchar", 80, False, "Bairro"),
            col("ship_city", "varchar", 80, False, "Cidade"),
            col("ship_state", "varchar", 2, False, "UF"),
            col("tracking_code", "varchar", 40, desc="Código de rastreio"),
            col("notes", "varchar", 500, desc="Observações"),
            col("placed_at", "timestamp", null=False, desc="Data da compra"),
            col("paid_at", "timestamp", desc="Pagamento confirmado"),
            col("shipped_at", "timestamp", desc="Enviado"),
            col("delivered_at", "timestamp", desc="Entregue"),
            col("canceled_at", "timestamp", desc="Cancelado"),
            col("version", "bigint", desc="Controle de concorrência"),
        ] + audit(),
        "unique": [["order_number"]],
        "index": [["user_id"], ["status"], ["placed_at"]],
        "fk": [("user_id", "vl_user")],
    },
    {
        "name": "vl_order_item",
        "desc": "Itens do pedido, com nome e preço copiados na hora da compra.",
        "cols": [
            col("id", "id"),
            col("order_id", "bigint", null=False, desc="Pedido"),
            col("product_id", "bigint", desc="Produto (pode ficar vazio se o produto for excluído)"),
            col("sku", "varchar", 40, False, "Código na hora da compra"),
            col("product_name", "varchar", 140, False, "Nome na hora da compra"),
            col("unit_price", "decimal", (12, 2), False, "Preço unitário"),
            col("quantity", "int", null=False, desc="Quantidade"),
            col("line_total", "decimal", (12, 2), False, "Preço x quantidade"),
        ],
        "unique": [],
        "index": [["order_id"], ["product_id"]],
        "fk": [("order_id", "vl_order"), ("product_id", "vl_product")],
    },
    {
        "name": "vl_order_status_history",
        "desc": "Linha do tempo de cada pedido.",
        "cols": [
            col("id", "id"),
            col("order_id", "bigint", null=False, desc="Pedido"),
            col("from_status", "varchar", 20, desc="Situação anterior"),
            col("to_status", "varchar", 20, False, "Nova situação"),
            col("note", "varchar", 300, desc="Observação"),
            col("user_id", "bigint", desc="Quem alterou (vazio = sistema)"),
            col("created_at", "timestamp", null=False, desc="Quando"),
        ],
        "unique": [],
        "index": [["order_id"]],
        "fk": [("order_id", "vl_order"), ("user_id", "vl_user")],
    },
    {
        "name": "vl_payment",
        "desc": "Pagamentos dos pedidos. Número de cartão nunca é gravado.",
        "cols": [
            col("id", "id"),
            col("order_id", "bigint", null=False, desc="Pedido"),
            col("provider", "varchar", 20, False, "Provedor", values=["SIMULATED", "STRIPE"]),
            col("method", "varchar", 20, False, "Forma de pagamento", values=["CARD", "PIX", "BOLETO", "CHECKOUT"]),
            col("status", "varchar", 20, False, "Situação",
                values=["PENDING", "PAID", "FAILED", "CANCELED", "REFUNDED"]),
            col("amount", "decimal", (12, 2), False, "Valor"),
            col("currency", "varchar", 3, False, "Moeda (BRL)"),
            col("installments", "int", null=False, desc="Parcelas"),
            col("external_id", "varchar", 120, desc="Identificador no provedor"),
            col("checkout_url", "varchar", 500, desc="Página de pagamento do provedor"),
            col("card_brand", "varchar", 20, desc="Bandeira"),
            col("card_last4", "varchar", 4, desc="Últimos 4 dígitos"),
            col("pix_code", "varchar", 300, desc="Código PIX copia e cola"),
            col("boleto_line", "varchar", 60, desc="Linha digitável do boleto"),
            col("failure_reason", "varchar", 200, desc="Motivo da recusa"),
            col("refunded_amount", "decimal", (12, 2), False, "Valor estornado"),
            col("paid_at", "timestamp", desc="Confirmação"),
            col("expires_at", "timestamp", desc="Validade do PIX ou boleto"),
        ] + audit(),
        "unique": [],
        "index": [["order_id"], ["external_id"]],
        "fk": [("order_id", "vl_order")],
    },
]

ORDER = ["vl_user", "vl_session", "vl_address", "vl_category", "vl_product", "vl_cart", "vl_cart_item", "vl_coupon",
         "vl_order", "vl_order_item", "vl_order_status_history", "vl_payment", "vl_inventory_movement"]


def sql_type(c, v):
    k = c["kind"]
    if k == "id":
        return {
            "h2": "BIGINT GENERATED BY DEFAULT AS IDENTITY",
            "postgresql": "BIGINT GENERATED BY DEFAULT AS IDENTITY",
            "mysql": "BIGINT NOT NULL AUTO_INCREMENT",
            "mariadb": "BIGINT NOT NULL AUTO_INCREMENT",
            "sqlserver": "BIGINT IDENTITY(1,1) NOT NULL",
            "oracle": "NUMBER(19) GENERATED BY DEFAULT ON NULL AS IDENTITY",
        }[v]
    if k == "bigint":
        return "NUMBER(19)" if v == "oracle" else "BIGINT"
    if k == "int":
        return "NUMBER(10)" if v == "oracle" else "INTEGER" if v in ("h2", "postgresql") else "INT"
    if k == "varchar":
        n = c["size"]
        return f"VARCHAR2({n} CHAR)" if v == "oracle" else f"NVARCHAR({n})" if v == "sqlserver" else f"VARCHAR({n})"
    if k == "decimal":
        p, s = c["size"]
        return f"NUMBER({p},{s})" if v == "oracle" else f"DECIMAL({p},{s})" if v in ("mysql", "mariadb", "sqlserver") else f"NUMERIC({p},{s})"
    if k == "bool":
        return {"h2": "BOOLEAN", "postgresql": "BOOLEAN", "mysql": "BOOLEAN", "mariadb": "BOOLEAN",
                "sqlserver": "BIT", "oracle": "NUMBER(1)"}[v]
    if k == "timestamp":
        return {"h2": "TIMESTAMP(6)", "postgresql": "TIMESTAMP(6)", "mysql": "DATETIME(6)", "mariadb": "DATETIME(6)",
                "sqlserver": "DATETIME2(6)", "oracle": "TIMESTAMP(6)"}[v]
    raise ValueError(k)


def short(t):
    return t[3:]


def render(v):
    by = {t["name"]: t for t in TABLES}
    out = []
    for name in ORDER:
        t = by[name]
        lines = []
        for c in t["cols"]:
            if c["kind"] == "id":
                lines.append(f"    {c['name']} {sql_type(c, v)} PRIMARY KEY")
                continue
            lines.append(f"    {c['name']} {sql_type(c, v)}{'' if c['null'] else ' NOT NULL'}")
        for u in t["unique"]:
            lines.append(f"    CONSTRAINT uk_{short(name)}_{'_'.join(u)} UNIQUE ({', '.join(u)})")
        tail = " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci" if v in ("mysql", "mariadb") else ""
        out.append(f"CREATE TABLE {name} (\n" + ",\n".join(lines) + f"\n){tail};")
        out.append("")
    for name in ORDER:
        t = by[name]
        for ix in t["index"]:
            out.append(f"CREATE INDEX ix_{short(name)}_{'_'.join(ix)} ON {name} ({', '.join(ix)});")
    for name in ORDER:
        t = by[name]
        for colname, ref in t["fk"]:
            out.append(f"ALTER TABLE {name} ADD CONSTRAINT fk_{short(name)}_{colname.replace('_id', '')} "
                       f"FOREIGN KEY ({colname}) REFERENCES {ref} (id);")
    out.append("")
    out.append("")
    return "\n".join(out)


def mermaid_type(c):
    k = c["kind"]
    return {"id": "bigint", "bigint": "bigint", "int": "int", "varchar": "varchar", "decimal": "decimal",
            "bool": "boolean", "timestamp": "timestamp"}[k]


def doc():
    by = {t["name"]: t for t in TABLES}
    fk_cols = {(t["name"], c) for t in TABLES for c, _ in t["fk"]}
    uniq_cols = {(t["name"], u[0]) for t in TABLES for u in t["unique"] if len(u) == 1}
    m = ["```mermaid", "erDiagram"]
    rels = [
        ("vl_user", "||--o{", "vl_session", "abre"),
        ("vl_user", "||--o{", "vl_address", "cadastra"),
        ("vl_user", "||--o|", "vl_cart", "tem"),
        ("vl_cart", "||--o{", "vl_cart_item", "contém"),
        ("vl_product", "||--o{", "vl_cart_item", "está em"),
        ("vl_category", "|o--o{", "vl_product", "agrupa"),
        ("vl_user", "||--o{", "vl_order", "faz"),
        ("vl_order", "||--|{", "vl_order_item", "contém"),
        ("vl_product", "|o--o{", "vl_order_item", "vendido em"),
        ("vl_order", "||--o{", "vl_order_status_history", "registra"),
        ("vl_order", "||--o{", "vl_payment", "é pago por"),
        ("vl_product", "||--o{", "vl_inventory_movement", "movimenta"),
        ("vl_order", "|o--o{", "vl_inventory_movement", "gera"),
        ("vl_user", "|o--o{", "vl_inventory_movement", "faz"),
        ("vl_user", "|o--o{", "vl_order_status_history", "altera"),
    ]
    for a, card, b, label in rels:
        m.append(f'    {a} {card} {b} : "{label}"')
    for name in ORDER:
        t = by[name]
        m.append(f"    {name} {{")
        for c in t["cols"]:
            keys = []
            if c["kind"] == "id":
                keys.append("PK")
            if (name, c["name"]) in fk_cols:
                keys.append("FK")
            if (name, c["name"]) in uniq_cols:
                keys.append("UK")
            k = (" " + ",".join(keys)) if keys else ""
            m.append(f"        {mermaid_type(c)} {c['name']}{k}")
        m.append("    }")
    m.append("```")

    sections = []
    for name in ORDER:
        t = by[name]
        fks = dict(t["fk"])
        rows = ["| Coluna | Tipo | Obrigatório | Chave | Descrição |", "|---|---|---|---|---|"]
        for c in t["cols"]:
            if c["kind"] == "varchar":
                typ = f"texto({c['size']})"
            elif c["kind"] == "decimal":
                typ = f"decimal({c['size'][0]},{c['size'][1]})"
            else:
                typ = {"id": "bigint, autoincremento", "bigint": "bigint", "int": "inteiro", "bool": "booleano",
                       "timestamp": "data e hora"}[c["kind"]]
            key = []
            if c["kind"] == "id":
                key.append("PK")
            if c["name"] in fks:
                key.append(f"FK → `{fks[c['name']]}.id`")
            if (name, c["name"]) in uniq_cols:
                key.append("Único")
            desc = c["desc"]
            if c["values"]:
                desc += ": " + ", ".join(f"`{x}`" for x in c["values"])
            rows.append(f"| `{c['name']}` | {typ} | {'sim' if not c['null'] or c['kind'] == 'id' else 'não'} | {' · '.join(key)} | {desc} |")
        extra = []
        multi = [u for u in t["unique"] if len(u) > 1]
        if multi:
            extra.append("Restrição de unicidade: " + "; ".join("(" + ", ".join(f"`{x}`" for x in u) + ")" for u in multi) + ".")
        if t["index"]:
            extra.append("Índices: " + ", ".join("`" + "_".join(i) + "`" for i in t["index"]) + ".")
        sections.append(f"### `{name}`\n\n{t['desc']}\n\n" + "\n".join(rows) + ("\n\n" + " ".join(extra) if extra else ""))
    return "\n".join(m), "\n\n".join(sections)


def main():
    only_doc = "--somente-doc" in sys.argv
    for v in ([] if only_doc else VENDORS):
        d = MIGRATIONS / v
        d.mkdir(parents=True, exist_ok=True)
        (d / "V1__schema.sql").write_text(render(v), encoding="utf-8", newline="\n")
    diagram, tables = doc()
    template = (Path(__file__).parent / "banco-de-dados.template.md").read_text(encoding="utf-8")
    DOC.parent.mkdir(parents=True, exist_ok=True)
    DOC.write_text(template.replace("{{DIAGRAMA}}", diagram).replace("{{TABELAS}}", tables), encoding="utf-8", newline="\n")
    print("ok")


if __name__ == "__main__":
    main()
