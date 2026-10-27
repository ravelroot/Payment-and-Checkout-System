# Gerador dos PDFs da documentação

Gera os dois PDFs da Velo com a identidade visual da interface:

- `Manual-Velo.pdf` (manual técnico do projeto)
- `Banco-de-Dados-Velo.pdf` (modelo de dados, diagrama e pareamento)

Cada um é gravado em duas cópias iguais: na raiz do projeto e em `docs/`.

## Como rodar

Na raiz do projeto:

```bash
python3 scripts/docs/gerar_pdfs.py
```

Leva poucos segundos. Precisa de:

1. Python 3 com `playwright` (Chromium já instalado) e `pypdf` (`pip install pypdf`) e `Pillow`.
2. O `mermaid.min.js`, que vem com o mermaid-cli (`npm i -g @mermaid-js/mermaid-cli`). O script procura sozinho; se não achar, aponte `VELO_MERMAID_JS` para o arquivo.

## Como funciona

| Arquivo | Papel |
|---|---|
| `gerar_pdfs.py` | Ponto de entrada: monta os dois documentos, gera os PDFs e grava os metadados |
| `manual.py` | Texto do manual, capítulo por capítulo |
| `banco.py` | Texto do PDF do banco; tabelas e diagramas vêm de `scripts/schema/gerar_esquema.py` |
| `base.py` | Marcação (títulos, tabelas, passos, código, avisos, telas e diagramas) e geração com o Chromium |
| `estilo.css` | Identidade visual: cores, tipografia, capa, sumário, cabeçalho e rodapé |
| `fontes/` | Instrument Serif, Public Sans e DejaVu Sans Mono, embutidas no PDF |

- O Sumário é feito em duas passagens: a primeira descobre a página de cada título, a segunda escreve os números.
- Os diagramas são desenhados com Mermaid dentro da própria página, com as mesmas fontes, e saem em vetor.
- As capturas vêm de `docs/images/tela-*.png`, convertidas para JPEG 85%. Se uma captura não existir, aparece um quadro com o nome do arquivo e a legenda, e o script segue normalmente. Basta rodar de novo quando a captura chegar.
- Para mudar o texto, edite `manual.py` ou `banco.py`. Use `` `código` `` e `**negrito**` dentro dos textos.
