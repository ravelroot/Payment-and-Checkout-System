"""Peças comuns aos PDFs da Velo: marcação do conteúdo, imagens, diagramas e geração com o Chromium."""
import glob
import html
import io
import json
import os
import re
import shutil
import subprocess
import tempfile
from pathlib import Path

AQUI = Path(__file__).resolve().parent
RAIZ = AQUI.parents[1]
IMAGENS = RAIZ / "docs" / "images"
VERSAO = "1.15.0"
DATA = "Outubro de 2026"
AUTOR = "Erik L. Pimenta"

LARGURA_UTIL_MM = 172  # A4 menos as margens laterais

TEMA_MERMAID = {
    "startOnLoad": False,
    "theme": "base",
    "fontFamily": "Public Sans",
    "themeVariables": {
        "fontFamily": "Public Sans", "fontSize": "13px",
        "background": "#FFFFFF", "primaryColor": "#FFFFFF", "primaryTextColor": "#15181B",
        "primaryBorderColor": "#1F5C4C", "secondaryColor": "#F6F7F7", "tertiaryColor": "#F6F7F7",
        "lineColor": "#5F666B", "textColor": "#15181B", "mainBkg": "#FFFFFF", "nodeBorder": "#1F5C4C",
        "clusterBkg": "#F6F7F7", "clusterBorder": "#E2E5E6", "edgeLabelBackground": "#FFFFFF",
        "actorBkg": "#F6F7F7", "actorBorder": "#1F5C4C", "actorTextColor": "#15181B", "actorLineColor": "#B5BBBE",
        "signalColor": "#15181B", "signalTextColor": "#15181B", "labelBoxBkgColor": "#F6F7F7",
        "labelBoxBorderColor": "#1F5C4C", "labelTextColor": "#15181B", "loopTextColor": "#1F5C4C",
        "noteBkgColor": "#F6F7F7", "noteBorderColor": "#E2E5E6", "noteTextColor": "#15181B",
        "activationBkgColor": "#E9EFEC", "activationBorderColor": "#1F5C4C", "sequenceNumberColor": "#FFFFFF",
        "attributeBackgroundColorOdd": "#FFFFFF", "attributeBackgroundColorEven": "#F8F7F3",
        "stateBkg": "#FFFFFF", "stateLabelColor": "#15181B", "compositeBackground": "#F6F7F7",
        "transitionColor": "#5F666B", "transitionLabelColor": "#15181B", "specialStateColor": "#1F5C4C",
        "innerEndBackground": "#1F5C4C",
    },
    "er": {"layoutDirection": "TB", "entityPadding": 12, "minEntityWidth": 90, "fontSize": 12, "useMaxWidth": False},
    "sequence": {"useMaxWidth": False, "mirrorActors": False, "actorMargin": 46, "messageMargin": 30,
                 "boxMargin": 8, "noteMargin": 8, "messageFontSize": 12, "actorFontSize": 13, "noteFontSize": 12},
    "flowchart": {"useMaxWidth": False, "curve": "basis", "padding": 12, "nodeSpacing": 34, "rankSpacing": 40,
                  "htmlLabels": True},
    "state": {"useMaxWidth": False},
}


def md(texto):
    """Escapa o texto e aplica a marcação mínima: `código`, **negrito**, [[novo]]."""
    s = html.escape(texto, quote=False)
    s = re.sub(r"`([^`]+)`", r"<code>\1</code>", s)
    s = re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", s)
    s = s.replace("[[novo]]", '<span class="novo">novo</span>')
    return s


def localizar_mermaid():
    candidatos = [os.environ.get("VELO_MERMAID_JS", "")]
    try:
        raiz_npm = subprocess.run(["npm", "root", "-g"], capture_output=True, text=True, timeout=15).stdout.strip()
        if raiz_npm:
            candidatos += [f"{raiz_npm}/@mermaid-js/mermaid-cli/node_modules/mermaid/dist/mermaid.min.js",
                           f"{raiz_npm}/mermaid/dist/mermaid.min.js"]
    except Exception:
        pass
    mmdc = shutil.which("mmdc")
    if mmdc:
        base = Path(os.path.realpath(mmdc)).parent
        candidatos += glob.glob(str(base / ".." / "**" / "mermaid" / "dist" / "mermaid.min.js"), recursive=True)[:1]
    candidatos += ["/opt/npm-tools/node_modules/mermaid/dist/mermaid.min.js",
                   str(RAIZ / "frontend" / "node_modules" / "mermaid" / "dist" / "mermaid.min.js")]
    for c in candidatos:
        if c and Path(c).is_file():
            return Path(c).resolve()
    return None


class Documento:
    def __init__(self, cabecalho, temp):
        self.cabecalho = cabecalho
        self.temp = Path(temp)
        self.partes = []
        self.sumario = []  # (nivel, id, titulo, numero_do_capitulo)
        self.cap = 0
        self.n = 0
        self.diagramas = []

    # --- estrutura ---
    def _id(self):
        self.n += 1
        return f"s{self.n}"

    def raw(self, h):
        self.partes.append(h)

    def capa(self, titulo, subtitulo):
        logo = self._logo()
        self.raw(f"""
<section class="capa">
  <div class="faixa"></div><div class="fio"></div>
  <div class="conteudo">
    {f'<img class="logo" src="{logo}" alt="">' if logo else ''}
    <div class="marca">Velo</div>
    <div class="titulo">{md(titulo)}</div>
    <div class="subtitulo">{md(subtitulo)}</div>
    <div class="regua"></div>
    <div class="autor">Feito por <b>{AUTOR}</b></div>
    <div class="rodape"><span><b>Versão {VERSAO}</b> · {DATA} · Licença MIT</span><span>Código aberto</span></div>
  </div>
</section>""")

    def lugar_do_sumario(self):
        self.raw("<!--SUMARIO-->")

    def capitulo(self, titulo, abertura=None):
        self.cap += 1
        i = self._id()
        self.sumario.append((1, i, titulo, self.cap))
        ab = f'<p class="abertura">{md(abertura)}</p>' if abertura else ""
        self.raw(f'<section class="capitulo"><div class="rotulo">Capítulo {self.cap}</div>'
                 f'<h1 id="{i}">{md(titulo)}</h1>{ab}</section>')

    def secao(self, titulo, nova_pagina=False, novo=False):
        i = self._id()
        self.sumario.append((2, i, titulo, None))
        cls = ' class="quebra"' if nova_pagina else ""
        selo = ' <span class="novo">novo</span>' if novo else ""
        self.raw(f'<h2 id="{i}"{cls}>{md(titulo)}{selo}</h2>')

    def sub(self, titulo):
        self.raw(f"<h3>{md(titulo)}</h3>")

    def p(self, texto, cls=""):
        self.raw(f'<p class="{cls}">{md(texto)}</p>' if cls else f"<p>{md(texto)}</p>")

    def lista(self, itens):
        self.raw('<ul class="lista">' + "".join(f"<li>{md(x)}</li>" for x in itens) + "</ul>")

    def passos(self, itens):
        self.raw('<ol class="passos">' + "".join(f"<li>{md(x)}</li>" for x in itens) + "</ol>")

    def codigo(self, texto, rotulo=None):
        r = f'<span class="rotulo-cod">{html.escape(rotulo)}</span>' if rotulo else ""
        self.raw(f"<pre>{r}<code>{html.escape(texto.strip(chr(10)))}</code></pre>")

    def nota(self, titulo, texto, alerta=False):
        self.raw(f'<div class="nota{" alerta" if alerta else ""}"><span class="t">{md(titulo)}</span><p>{md(texto)}</p></div>')

    def tabela(self, cab, linhas, larguras=None, cls="", alinhar=None):
        alinhar = alinhar or {}
        col = ""
        if larguras:
            col = "<colgroup>" + "".join(f'<col style="width:{w}">' for w in larguras) + "</colgroup>"
        th = "".join(f'<th class="{alinhar.get(k, "")}">{md(c)}</th>' for k, c in enumerate(cab))
        corpo = []
        for linha in linhas:
            tds = []
            for k, c in enumerate(linha):
                conteudo = c[4:] if isinstance(c, str) and c.startswith("RAW:") else md(str(c))
                tds.append(f'<td class="{alinhar.get(k, "")}">{conteudo}</td>')
            corpo.append("<tr>" + "".join(tds) + "</tr>")
        self.raw(f'<table class="{cls}">{col}<thead><tr>{th}</tr></thead><tbody>{"".join(corpo)}</tbody></table>')

    # --- imagens ---
    def _logo(self):
        orig = IMAGENS / "logo-transparente.png"
        if not orig.is_file():
            return None
        try:
            from PIL import Image
            im = Image.open(orig).convert("RGBA")
            alfa = im.getchannel("A")
            caixa = alfa.getbbox()
            alfa = alfa.crop(caixa)
            tinta = Image.new("RGBA", alfa.size, (0x19, 0x1A, 0x17, 255))
            tinta.putalpha(alfa)
            destino = self.temp / "logo-tinta.png"
            tinta.save(destino)
            return destino.as_uri()
        except Exception:
            return orig.as_uri()

    def tela(self, arquivo, legenda, largura_mm=LARGURA_UTIL_MM, altura_max_mm=205, junto=False):
        self.raw(self._figura(arquivo, legenda, largura_mm, altura_max_mm, junto))

    def telas_lado_a_lado(self, itens, altura_max_mm=120):
        lado = (LARGURA_UTIL_MM - 5 * (len(itens) - 1)) / len(itens)
        self.raw('<div class="dupla mantem">' + "".join(self._figura(a, l, lado, altura_max_mm) for a, l in itens) + "</div>")

    def _figura(self, arquivo, legenda, largura_mm, altura_max_mm, junto=False):
        orig = IMAGENS / arquivo
        cls = ' class="junto"' if junto else ""
        cap = f"<figcaption>{md(legenda)}</figcaption>"
        if not orig.is_file():
            alt = min(largura_mm * 850 / 1360, altura_max_mm) if "celular" not in arquivo else min(largura_mm * 2.05, altura_max_mm)
            return (f'<figure{cls}><div class="pendente" style="width:{largura_mm:.1f}mm;height:{alt:.1f}mm">'
                    f'<div class="ic"></div><div class="l1">Captura em atualização</div>'
                    f'<div class="l2">docs/images/{html.escape(arquivo)}</div></div>{cap}</figure>')
        from PIL import Image
        im = Image.open(orig)
        im.load()
        w, h = im.size
        if w > 1600:
            im = im.resize((1600, round(h * 1600 / w)), Image.LANCZOS)
            w, h = im.size
        destino = self.temp / (Path(arquivo).stem + ".jpg")
        fundo = Image.new("RGB", im.size, (255, 255, 255))
        if im.mode in ("RGBA", "LA", "P"):
            im = im.convert("RGBA")
            fundo.paste(im, mask=im.getchannel("A"))
        else:
            fundo.paste(im.convert("RGB"))
        fundo.save(destino, "JPEG", quality=85, optimize=True, progressive=False)
        larg = largura_mm
        alt = larg * h / w
        if alt > altura_max_mm:
            alt = altura_max_mm
            larg = alt * w / h
        return (f'<figure{cls}><img class="tela" src="{destino.as_uri()}" style="width:{larg:.1f}mm;height:{alt:.1f}mm" alt="">'
                f"{cap}</figure>")

    # --- diagramas ---
    def diagrama(self, fonte, legenda, largura_mm=LARGURA_UTIL_MM, altura_max_mm=200, escala_max=1.0):
        k = len(self.diagramas)
        self.diagramas.append(fonte)
        self.raw(f'<figure class="diagrama" data-w="{largura_mm}" data-h="{altura_max_mm}" data-s="{escala_max}">'
                 f'<pre class="mermaid" id="mm{k}">{html.escape(fonte)}</pre><figcaption>{md(legenda)}</figcaption></figure>')

    # --- montagem ---
    def html_final(self, paginas, css):
        itens = []
        for nivel, i, titulo, num in self.sumario:
            pg = paginas.get(i, "")
            n = f'<span class="num">{num}</span>' if nivel == 1 else ""
            itens.append(f'<li class="n{nivel}"><a href="#{i}">{n}<span class="t">{md(titulo)}</span>'
                         f'<span class="pontos"></span><span class="pg">{pg}</span></a></li>')
        sumario = f'<section class="sumario"><h1>Sumário</h1><ol>{"".join(itens)}</ol></section>'
        corpo = "\n".join(self.partes).replace("<!--SUMARIO-->", sumario)
        return (f'<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><base href="{AQUI.as_uri()}/">'
                f"<style>{css}</style></head><body>{corpo}</body></html>")


JS_DIAGRAMAS = """
async (config) => {
  await document.fonts.ready;
  for (const f of ['400 13px "Public Sans"', '600 13px "Public Sans"', '400 30px "Instrument Serif"', '12px "Velo Mono"'])
    await document.fonts.load(f);
  if (!document.querySelector('pre.mermaid')) return 'ok';
  mermaid.initialize(config);
  await mermaid.run({ querySelector: 'pre.mermaid' });
  const mm = 96 / 25.4;
  for (const fig of document.querySelectorAll('figure.diagrama')) {
    const svg = fig.querySelector('svg');
    if (!svg) continue;
    const vb = svg.viewBox.baseVal;
    const maxW = parseFloat(fig.dataset.w) * mm, maxH = parseFloat(fig.dataset.h) * mm;
    const s = Math.min(parseFloat(fig.dataset.s || '1'), maxW / vb.width, maxH / vb.height);
    svg.removeAttribute('style');
    svg.setAttribute('width', (vb.width * s).toFixed(1));
    svg.setAttribute('height', (vb.height * s).toFixed(1));
  }
  return 'ok';
}
"""


def gerar_pdf(navegador, doc, css_modelo, mermaid_js, saida_temp):
    """Duas passagens: a primeira descobre a página de cada título, a segunda escreve o Sumário com os números."""
    from pypdf import PdfReader

    css = css_modelo.replace("{{CABECALHO}}", doc.cabecalho).replace("{{VERSAO}}", f"Versão {VERSAO}")
    paginas = {i: "00" for _, i, _, _ in doc.sumario}
    pdf = None
    for passagem in (1, 2):
        conteudo = doc.html_final(paginas, css)
        if mermaid_js:
            conteudo = conteudo.replace("</head>", f'<script src="{mermaid_js.as_uri()}"></script>'
                                        f'<script>mermaid.initialize({json.dumps(TEMA_MERMAID)});</script></head>')
        arq = Path(saida_temp) / f"{doc.cabecalho.split('·')[-1].strip().replace(' ', '-')}-{passagem}.html"
        arq.write_text(conteudo, encoding="utf-8")
        pagina = navegador.new_page()
        pagina.goto(arq.as_uri(), wait_until="load")
        pagina.evaluate(JS_DIAGRAMAS, TEMA_MERMAID)
        opcoes = dict(format="A4", print_background=True, prefer_css_page_size=True,
                      margin={"top": "0", "bottom": "0", "left": "0", "right": "0"})
        try:
            pdf = pagina.pdf(**opcoes, outline=True, tagged=True)
        except TypeError:
            pdf = pagina.pdf(**opcoes)
        pagina.close()
        if passagem == 1:
            leitor = PdfReader(io.BytesIO(pdf))
            destinos = leitor.named_destinations
            achados = {}
            for nome, d in destinos.items():
                try:
                    achados[str(nome).lstrip("/")] = leitor.get_destination_page_number(d) + 1
                except Exception:
                    pass
            faltando = [i for i in paginas if i not in achados]
            if faltando:
                raise RuntimeError(f"Não encontrei a página destes títulos: {faltando[:5]}")
            paginas = {i: str(achados[i]) for i in paginas}
    return pdf, paginas


def gravar(pdf_bytes, destinos, titulo, assunto, palavras):
    from pypdf import PdfReader, PdfWriter

    escritor = PdfWriter(clone_from=PdfReader(io.BytesIO(pdf_bytes)))
    escritor.add_metadata({"/Title": titulo, "/Subject": assunto, "/Author": AUTOR, "/Creator": "Velo",
                           "/Producer": "Velo", "/Keywords": palavras})
    buf = io.BytesIO()
    escritor.write(buf)
    dados = buf.getvalue()
    for d in destinos:
        Path(d).write_bytes(dados)
    return len(PdfReader(io.BytesIO(dados)).pages), len(dados)
