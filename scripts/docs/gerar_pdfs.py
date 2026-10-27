"""Gera Manual-Velo.pdf e Banco-de-Dados-Velo.pdf (na raiz e em docs/).

Uso, a partir da raiz do projeto:  python3 scripts/docs/gerar_pdfs.py
"""
import sys
import tempfile
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from base import AQUI, RAIZ, gerar_pdf, gravar, localizar_mermaid  # noqa: E402
import banco  # noqa: E402
import manual  # noqa: E402


def main():
    inicio = time.time()
    try:
        from playwright.sync_api import sync_playwright
    except ImportError:
        sys.exit("Falta o Playwright: pip install playwright (o Chromium precisa estar instalado).")
    mermaid_js = localizar_mermaid()
    if not mermaid_js:
        sys.exit("Não encontrei o mermaid.min.js. Instale o mermaid-cli (npm i -g @mermaid-js/mermaid-cli) "
                 "ou aponte VELO_MERMAID_JS para o arquivo.")
    css = (AQUI / "estilo.css").read_text(encoding="utf-8")
    trabalhos = [
        (manual, "Manual-Velo.pdf", "Velo — Manual técnico do projeto",
         "E-commerce completo em Java, Spring Boot e React: instalação, telas, painel, pagamentos, integrações, API e banco",
         "Velo, e-commerce, Spring Boot, React, manual"),
        (banco, "Banco-de-Dados-Velo.pdf", "Velo — Banco de dados",
         "Modelo de dados, diagrama de relacionamentos e pareamento com H2, PostgreSQL, MySQL, MariaDB, SQL Server e Oracle",
         "Velo, banco de dados, Flyway, diagrama ER"),
    ]
    with tempfile.TemporaryDirectory(prefix="velo-pdf-") as temp, sync_playwright() as p:
        navegador = p.chromium.launch()
        for modulo, nome, titulo, assunto, chaves in trabalhos:
            doc = modulo.montar(temp)
            pdf, _ = gerar_pdf(navegador, doc, css, mermaid_js, temp)
            paginas, tamanho = gravar(pdf, [RAIZ / nome, RAIZ / "docs" / nome], titulo, assunto, chaves)
            print(f"{nome}: {paginas} páginas, {tamanho / 1024 / 1024:.1f} MB  (raiz e docs/)")
        navegador.close()
    print(f"Pronto em {time.time() - inicio:.0f} s.")


if __name__ == "__main__":
    main()
