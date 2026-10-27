"""Conteúdo do PDF Banco de dados da Velo. O modelo vem de scripts/schema/gerar_esquema.py (fonte única)."""
import importlib.util

from base import RAIZ, Documento


def _esquema():
    spec = importlib.util.spec_from_file_location("gerar_esquema", RAIZ / "scripts" / "schema" / "gerar_esquema.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


RELACOES = [
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

PARTES = [
    ("Pessoas e acesso", ["vl_user", "vl_session", "vl_address", "vl_cart"], []),
    ("Catálogo, itens do carrinho e cupons", ["vl_category", "vl_product", "vl_cart_item", "vl_coupon"], ["vl_cart"]),
    ("Pedidos, itens e pagamentos", ["vl_order", "vl_order_item", "vl_order_status_history", "vl_payment"],
     ["vl_user", "vl_product"]),
    ("Estoque", ["vl_inventory_movement"], ["vl_product", "vl_order", "vl_user"]),
]


def _er(es, completas, referencias, direcao="TB"):
    by = {t["name"]: t for t in es.TABLES}
    fk = {(t["name"], c) for t in es.TABLES for c, _ in t["fk"]}
    uk = {(t["name"], u[0]) for t in es.TABLES for u in t["unique"] if len(u) == 1}
    todas = set(completas) | set(referencias)
    m = ["erDiagram", f"    direction {direcao}"]
    for a, card, b, rot in RELACOES:
        if a in todas and b in todas and (a in completas or b in completas):
            m.append(f'    {a} {card} {b} : "{rot}"')
    for nome in completas:
        m.append(f"    {nome} {{")
        for c in by[nome]["cols"]:
            k = []
            if c["kind"] == "id":
                k.append("PK")
            if (nome, c["name"]) in fk:
                k.append("FK")
            if (nome, c["name"]) in uk:
                k.append("UK")
            m.append(f"        {es.mermaid_type(c)} {c['name']}{(' ' + ','.join(k)) if k else ''}")
        m.append("    }")
    return "\n".join(m)


def _ligacoes():
    m = ["erDiagram", "    direction LR"]
    for a, card, b, rot in RELACOES:
        m.append(f'    {a} {card} {b} : "{rot}"')
    return "\n".join(m)


ESTADOS = """
stateDiagram-v2
    direction TB
    [*] --> PENDING_PAYMENT: pedido realizado
    PENDING_PAYMENT --> PAID: pagamento confirmado
    PENDING_PAYMENT --> CANCELED: cancelado ou prazo vencido
    PAID --> PROCESSING: separação
    PAID --> CANCELED: cancelado com estorno
    PROCESSING --> SHIPPED: enviado com rastreio
    PROCESSING --> CANCELED: cancelado com estorno
    SHIPPED --> DELIVERED: entregue
    DELIVERED --> REFUNDED: devolução com estorno
    CANCELED --> [*]
    REFUNDED --> [*]
"""


def montar(temp):
    es = _esquema()
    by = {t["name"]: t for t in es.TABLES}
    d = Documento("Velo · Banco de dados", temp)
    d.capa("Banco de dados", "Modelo de dados, diagrama de relacionamentos e como ligar a Velo a cada banco suportado.")
    d.lugar_do_sumario()

    d.capitulo("Visão geral", "Este documento descreve o modelo de dados que a Velo usa e que qualquer banco escolhido "
               "precisa seguir: as 13 tabelas, seus campos, tipos, chaves e relacionamentos, além de como ligar a "
               "aplicação a cada banco.")
    d.nota("Versão 1.15.0", "O esquema não muda nesta versão. As novidades (webhooks de saída, Mercado Pago, consulta de "
           "CEP, favoritos) não criam tabelas: o histórico de entregas de webhook fica em memória, os favoritos e os vistos "
           "recentemente ficam no navegador, e o pagamento do Mercado Pago usa a mesma `vl_payment`.")
    d.secao("Bancos suportados")
    d.p("O mesmo modelo roda em seis bancos. As tabelas são criadas sozinhas na primeira execução pelo Flyway, a partir "
        "do script `V1__schema.sql` do banco escolhido, em `backend/src/main/resources/db/migration/`.")
    d.tabela(["Banco", "Versão mínima", "Perfil", "Pasta do script"], [
        ["H2 (arquivo local)", "2.2", "nenhum (padrão)", "`h2/`"],
        ["PostgreSQL", "13", "`postgresql`", "`postgresql/`"],
        ["MySQL", "8.0", "`mysql`", "`mysql/`"],
        ["MariaDB", "10.6", "`mariadb`", "`mariadb/`"],
        ["SQL Server", "2019", "`sqlserver`", "`sqlserver/`"],
        ["Oracle", "19c", "`oracle`", "`oracle/`"],
    ], larguras=["30%", "18%", "26%", "26%"])
    d.secao("As 13 tabelas")
    grupos = [("Pessoas e acesso", ["vl_user", "vl_session", "vl_address"]),
              ("Catálogo e estoque", ["vl_category", "vl_product", "vl_inventory_movement"]),
              ("Carrinho e cupons", ["vl_cart", "vl_cart_item", "vl_coupon"]),
              ("Pedidos e pagamentos", ["vl_order", "vl_order_item", "vl_order_status_history", "vl_payment"])]
    linhas = []
    for g, ts in grupos:
        for k, t in enumerate(ts):
            linhas.append([g if k == 0 else "", f"`{t}`", by[t]["desc"]])
    d.tabela(["Grupo", "Tabela", "O que guarda"], linhas, larguras=["20%", "28%", "52%"])

    d.capitulo("Diagrama de relacionamentos", "Primeiro a visão das ligações entre as tabelas; em seguida o diagrama "
               "completo, com todos os campos e chaves, em quatro partes.")
    d.diagrama(_ligacoes(), "Ligações entre as tabelas", altura_max_mm=150)
    d.secao("Como ler o diagrama", nova_pagina=True)
    d.tabela(["Símbolo na ponta da linha", "Significado"], [
        ["`||` (dois traços)", "exatamente um"], ["`|o` (traço e círculo)", "zero ou um"],
        ["`}|` (pé de galinha e traço)", "um ou mais"], ["`}o` (pé de galinha e círculo)", "zero ou mais"],
        ["PK", "chave primária"], ["FK", "chave estrangeira, aponta para o id de outra tabela"],
        ["UK", "valor único na tabela"],
    ], larguras=["40%", "60%"])
    d.secao("Ligações")
    d.lista([
        "Um usuário tem várias sessões, vários endereços, no máximo um carrinho e vários pedidos.",
        "Um carrinho tem vários itens, cada um apontando para um produto; o mesmo produto aparece uma única vez por carrinho.",
        "Um produto pertence a no máximo uma categoria e tem um histórico de movimentos de estoque.",
        "Um pedido tem um ou mais itens, uma linha do tempo de situações e um ou mais pagamentos (cada nova tentativa "
        "cria um pagamento).",
        "Um movimento de estoque pode estar ligado ao pedido que o gerou e ao usuário que o fez.",
        "Cupons se ligam ao pedido pelo código (`vl_order.coupon_code`), sem chave estrangeira, para o pedido continuar "
        "íntegro mesmo se o cupom for excluído.",
    ])
    d.secao("Diagrama completo")
    d.p("Todas as tabelas com tipos, campos e chaves, divididas em quatro partes para caber na página. As tabelas que "
        "aparecem só com o nome são referência: os campos delas estão na parte em que aparecem completas. O diagrama é "
        "vetorial; use o zoom do leitor de PDF para ver os detalhes.")
    for k, (titulo, completas, refs) in enumerate(PARTES, 1):
        legenda = f"Parte {k} de 4: {titulo.lower()} (" + ", ".join(f"`{t}`" for t in completas) + ")"
        if refs:
            legenda += ". " + ", ".join(f"`{t}`" for t in refs) + (" aparece" if len(refs) == 1 else " aparecem") + \
                       " só como referência"
        d.raw('<div class="quebra"></div>')
        d.diagrama(_er(es, completas, refs, "LR" if k in (1, 2, 3) else "TB"), legenda, altura_max_mm=225, escala_max=1.0)

    d.capitulo("Regras e convenções", "O que vale para todas as tabelas, em qualquer banco.")
    d.secao("Convenções do modelo")
    d.lista([
        "Todas as tabelas começam com `vl_` e as chaves primárias se chamam `id` (inteiro de 64 bits, autoincremento).",
        "Datas e horas ficam em **UTC**. A tela converte para o horário de Brasília.",
        "Valores em dinheiro usam `decimal(12,2)` e a moeda é sempre BRL.",
        "Situações e tipos são gravados como texto em maiúsculas (por exemplo `PENDING_PAYMENT`), para continuarem "
        "legíveis em qualquer banco.",
        "Pedidos copiam o endereço e o nome e preço de cada item no momento da compra. Alterar um produto depois não muda "
        "pedidos antigos.",
        "Senhas são guardadas só como hash BCrypt. Sessões guardam apenas o SHA-256 do token. Números de cartão nunca são "
        "gravados, só a bandeira e os 4 últimos dígitos.",
        "`version` nas tabelas de produto, cupom e pedido evita que duas pessoas sobrescrevam a mesma alteração.",
        "Nomes de restrições seguem um padrão: `uk_` para valores únicos, `ix_` para índices e `fk_` para chaves "
        "estrangeiras, seguidos da tabela sem o prefixo e das colunas.",
    ])
    d.secao("Tipos em cada banco")
    d.tabela(["Tipo no modelo", "H2 / PostgreSQL", "MySQL / MariaDB", "SQL Server", "Oracle"], [
        ["bigint, autoincremento", "`BIGINT GENERATED BY DEFAULT AS IDENTITY`", "`BIGINT AUTO_INCREMENT`",
         "`BIGINT IDENTITY(1,1)`", "`NUMBER(19) GENERATED BY DEFAULT ON NULL AS IDENTITY`"],
        ["bigint", "`BIGINT`", "`BIGINT`", "`BIGINT`", "`NUMBER(19)`"],
        ["inteiro", "`INTEGER`", "`INT`", "`INT`", "`NUMBER(10)`"],
        ["texto(n)", "`VARCHAR(n)`", "`VARCHAR(n)` utf8mb4", "`NVARCHAR(n)`", "`VARCHAR2(n CHAR)`"],
        ["decimal(12,2)", "`NUMERIC(12,2)`", "`DECIMAL(12,2)`", "`DECIMAL(12,2)`", "`NUMBER(12,2)`"],
        ["booleano", "`BOOLEAN`", "`BOOLEAN`", "`BIT`", "`NUMBER(1)`"],
        ["data e hora", "`TIMESTAMP(6)`", "`DATETIME(6)`", "`DATETIME2(6)`", "`TIMESTAMP(6)`"],
    ], larguras=["17%", "24%", "20%", "17%", "22%"], cls="compacta")
    d.secao("Fluxo de situações do pedido")
    d.diagrama(ESTADOS, "Situações do pedido em `vl_order.status`", altura_max_mm=120, largura_mm=150)
    d.p("O estoque sai no momento da compra (movimento `SALE`) e volta quando o pedido é cancelado ou estornado "
        "(movimento `RETURN`). Pedidos não pagos em 60 minutos são cancelados automaticamente. Cada mudança fica "
        "registrada em `vl_order_status_history`.")

    d.capitulo("Tabelas", "Cada tabela com seus campos, tipos, obrigatoriedade, chaves e índices, na ordem em que são "
               "criadas.")
    fk_cols = {(t["name"], c) for t in es.TABLES for c, _ in t["fk"]}
    uniq = {(t["name"], u[0]) for t in es.TABLES for u in t["unique"] if len(u) == 1}
    for nome in es.ORDER:
        t = by[nome]
        fks = dict(t["fk"])
        d.secao(nome)
        d.p(t["desc"])
        linhas = []
        for c in t["cols"]:
            if c["kind"] == "varchar":
                tipo = f"texto({c['size']})"
            elif c["kind"] == "decimal":
                tipo = f"decimal({c['size'][0]},{c['size'][1]})"
            else:
                tipo = {"id": "bigint, autoincremento", "bigint": "bigint", "int": "inteiro", "bool": "booleano",
                        "timestamp": "data e hora"}[c["kind"]]
            chave = []
            if c["kind"] == "id":
                chave.append("PK")
            if c["name"] in fks:
                chave.append(f"FK → `{fks[c['name']]}.id`")
            if (nome, c["name"]) in uniq:
                chave.append("Único")
            desc = c["desc"] + (": " + ", ".join(f"`{x}`" for x in c["values"]) if c["values"] else "")
            obrig = "sim" if (not c["null"] or c["kind"] == "id") else 'RAW:<span class="nao">não</span>'
            linhas.append([f"`{c['name']}`", tipo, obrig, " · ".join(chave), desc])
        d.tabela(["Coluna", "Tipo", "Obrigatório", "Chave", "Descrição"], linhas,
                 larguras=["22%", "17%", "11%", "17%", "33%"], cls="compacta")
        extra = []
        multi = [u for u in t["unique"] if len(u) > 1]
        for u in t["unique"]:
            extra.append(f"`uk_{nome[3:]}_{'_'.join(u)}` (único: {', '.join(u)})")
        for ix in t["index"]:
            extra.append(f"`ix_{nome[3:]}_{'_'.join(ix)}` (índice: {', '.join(ix)})")
        for col, ref in t["fk"]:
            extra.append(f"`fk_{nome[3:]}_{col.replace('_id', '')}` ({col} → {ref}.id)")
        if extra:
            d.p("**Restrições e índices:** " + "; ".join(extra) + ".", cls="apoio")
        if multi:
            pass

    d.capitulo("Como parear o banco", "Do zero até a Velo rodando no banco escolhido, em três passos.")
    d.secao("Sem configurar nada (H2)")
    d.p("Rode a aplicação. O banco é um arquivo em `backend/data/velo.mv.db`, criado na primeira execução com os dados de "
        "demonstração. Para começar do zero, pare a aplicação e apague a pasta `backend/data`.")
    d.secao("1. Subir o banco")
    d.p("Se você tem Docker, o arquivo `docker-compose.yml` já traz cada banco pronto, com usuário `velo` e banco `velo`:")
    d.codigo("docker compose --profile postgresql up -d")
    d.p("Troque `postgresql` por `mysql`, `mariadb`, `sqlserver` ou `oracle`. Sem Docker, instale o banco normalmente e "
        "rode o SQL de preparação do passo 2.")
    d.tabela(["Perfil do Docker", "Porta", "Primeira subida"], [
        ["`postgresql`", "5432", "poucos segundos"],
        ["`mysql`", "3306", "cerca de 20 segundos"],
        ["`mariadb`", "3307", "cerca de 15 segundos"],
        ["`sqlserver`", "1433", "cerca de 30 segundos; o serviço `sqlserver-init` cria o banco e o usuário e encerra sozinho"],
        ["`oracle`", "1521", "de 1 a 2 minutos; acompanhe com `docker compose logs -f oracle` até aparecer "
                             "`DATABASE IS READY TO USE`"],
    ], larguras=["22%", "12%", "66%"])
    d.p("Para desligar, use `docker compose --profile postgresql down`. Acrescente `-v` para apagar também os dados.")
    d.secao("2. Criar o banco e o usuário (sem Docker)")
    d.p("Pule este passo se usou o Docker. Troque `velo` pela senha que quiser.")
    d.codigo("CREATE USER velo WITH PASSWORD 'velo';\nCREATE DATABASE velo OWNER velo ENCODING 'UTF8';", "PostgreSQL")
    d.codigo("CREATE DATABASE velo CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;\nCREATE USER 'velo'@'%' IDENTIFIED BY "
             "'velo';\nGRANT ALL PRIVILEGES ON velo.* TO 'velo'@'%';\nFLUSH PRIVILEGES;", "MySQL / MariaDB")
    d.codigo("CREATE DATABASE velo COLLATE Latin1_General_100_CI_AS_SC_UTF8;\nGO\nCREATE LOGIN velo WITH PASSWORD = "
             "'Velo@Senha2026';\nGO\nUSE velo;\nCREATE USER velo FOR LOGIN velo;\nALTER ROLE db_owner ADD MEMBER velo;\nGO",
             "SQL Server")
    d.codigo("ALTER SESSION SET CONTAINER = FREEPDB1;\nCREATE USER velo IDENTIFIED BY velo QUOTA UNLIMITED ON USERS;\n"
             "GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE VIEW TO velo;", "Oracle")
    d.secao("3. Apontar a Velo para o banco")
    d.p("Escolha o perfil e, se precisar, o endereço e a senha:")
    d.codigo("export SPRING_PROFILES_ACTIVE=postgresql\nexport VELO_DB_URL=jdbc:postgresql://localhost:5432/velo\n"
             "export VELO_DB_USER=velo\nexport VELO_DB_PASSWORD=velo", "Linux / macOS")
    d.codigo('$env:SPRING_PROFILES_ACTIVE = "postgresql"\n$env:VELO_DB_URL = "jdbc:postgresql://localhost:5432/velo"\n'
             '$env:VELO_DB_USER = "velo"\n$env:VELO_DB_PASSWORD = "velo"', "Windows (PowerShell)")
    d.p("Depois rode a aplicação como no manual. Os perfis já trazem o endereço padrão de cada banco (`localhost`), então "
        "`VELO_DB_URL` só é necessária se o banco estiver em outra máquina ou porta.")
    d.tabela(["Perfil", "URL padrão", "Usuário / senha padrão"], [
        ["`postgresql`", "`jdbc:postgresql://localhost:5432/velo`", "`velo` / `velo`"],
        ["`mysql`", "`jdbc:mysql://localhost:3306/velo?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC`",
         "`velo` / `velo`"],
        ["`mariadb`", "`jdbc:mariadb://localhost:3307/velo`", "`velo` / `velo`"],
        ["`sqlserver`", "`jdbc:sqlserver://localhost:1433;databaseName=velo;encrypt=true;trustServerCertificate=true`",
         "`velo` / `Velo@Senha2026`"],
        ["`oracle`", "`jdbc:oracle:thin:@//localhost:1521/FREEPDB1`", "`velo` / `velo`"],
    ], larguras=["16%", "58%", "26%"])
    d.p("Na primeira conexão o Flyway cria as 13 tabelas e registra a versão em `flyway_schema_history`. Com o banco "
        "vazio, a Velo também cadastra os dados de demonstração (usuários, produtos, cupons e pedidos de exemplo). Para "
        "não criar esses dados, defina `VELO_SEED=false`.")
    d.secao("Problemas comuns")
    d.tabela(["Sintoma", "O que fazer"], [
        ["`Connection refused`", "O banco não está rodando ou a porta está diferente. Confira com `docker compose ps` ou "
                                 "ajuste `VELO_DB_URL`."],
        ["`password authentication failed` / `Login failed`", "Usuário ou senha diferentes dos informados em "
                                                              "`VELO_DB_USER` e `VELO_DB_PASSWORD`."],
        ["`Validate failed: Migrations have failed validation`", "O banco já tem outro esquema. Use um banco vazio só "
                                                                 "para a Velo."],
        ["SQL Server: `The server selected protocol version TLS10`", "Atualize o SQL Server ou mantenha "
                                                                    "`trustServerCertificate=true` na URL."],
        ["Oracle: `ORA-01950: no privileges on tablespace`", "Dê cota ao usuário: `ALTER USER velo QUOTA UNLIMITED ON USERS;`"],
    ], larguras=["40%", "60%"])

    d.capitulo("Evolução do esquema", "Como mudar o modelo depois da primeira versão publicada, sem quebrar bancos que "
               "já estão em uso.")
    d.p("O arquivo `V1__schema.sql` de cada banco é gerado por `scripts/schema/gerar_esquema.py`, a fonte única do "
        "modelo. Este PDF lê o mesmo arquivo, então as tabelas daqui sempre batem com o SQL.")
    d.passos([
        "Altere a entidade Java e o modelo em `gerar_esquema.py`.",
        "Crie um `V2__descricao.sql` em cada pasta de `db/migration` com o `ALTER TABLE` correspondente. Nunca edite um "
        "`V1` já aplicado, pois o Flyway recusa scripts alterados.",
        "Rode `python scripts/schema/gerar_esquema.py --somente-doc` para atualizar `docs/banco-de-dados.md` sem tocar no `V1`.",
        "Rode `python3 scripts/docs/gerar_pdfs.py` para atualizar este PDF e o manual.",
    ])
    return d
