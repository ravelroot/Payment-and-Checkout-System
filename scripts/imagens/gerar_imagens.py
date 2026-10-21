from pathlib import Path

DESTINO = Path(__file__).resolve().parents[2] / "frontend" / "public" / "products"

PAPEL = "#ECEEEE"
AREIA = "#DCE0E1"
PEDRA = "#B9BFC2"
MALVA = "#8C9599"
TINTA = "#1B1F22"
CLARO = "#F7F8F8"
GRAFITE = "#3A4145"

FUNDOS = [PAPEL]


def sombra(cx=400, cy=640, rx=210, ry=26):
    return f'<ellipse cx="{cx}" cy="{cy}" rx="{rx}" ry="{ry}" fill="{TINTA}" opacity="0.10"/>'


DESENHOS = {
    "caneca": lambda: sombra(rx=190) + f'''
<path d="M520 330h40a70 70 0 0 1 0 140h-40" fill="none" stroke="{TINTA}" stroke-width="34"/>
<rect x="250" y="270" width="290" height="370" rx="44" fill="{TINTA}"/>
<rect x="250" y="270" width="290" height="44" rx="22" fill="{GRAFITE}"/>
<path d="M330 230c-20-30 20-50 0-80M400 230c-20-30 20-50 0-80M470 230c-20-30 20-50 0-80" fill="none" stroke="{CLARO}" stroke-width="12" stroke-linecap="round" opacity="0.8"/>''',
    "luminaria": lambda: sombra(cx=330, rx=170) + f'''
<rect x="220" y="610" width="220" height="34" rx="17" fill="{TINTA}"/>
<path d="M330 615 L300 380 L470 230" fill="none" stroke="{TINTA}" stroke-width="22" stroke-linecap="round" stroke-linejoin="round"/>
<circle cx="300" cy="380" r="22" fill="{GRAFITE}"/>
<path d="M440 200 L600 300 L540 380 Z" fill="{TINTA}"/>
<path d="M545 382 L600 300 L720 470 Z" fill="{CLARO}" opacity="0.55"/>''',
    "vaso": lambda: sombra(rx=200) + f'''
<path d="M250 450h300l-30 190H280z" fill="{GRAFITE}"/>
<rect x="230" y="420" width="340" height="50" rx="12" fill="{TINTA}"/>
<path d="M400 420c-10-80-90-120-120-200 70 10 110 70 120 140 10-90 50-150 110-190 0 90-40 160-110 250z" fill="{TINTA}"/>
<circle cx="330" cy="550" r="6" fill="{PEDRA}"/><circle cx="460" cy="590" r="5" fill="{PEDRA}"/><circle cx="400" cy="520" r="4" fill="{PEDRA}"/>''',
    "vela": lambda: sombra(rx=170) + f'''
<rect x="270" y="360" width="260" height="280" rx="30" fill="{TINTA}"/>
<rect x="270" y="360" width="260" height="70" rx="30" fill="{CLARO}"/>
<rect x="330" y="470" width="140" height="90" rx="10" fill="{CLARO}" opacity="0.9"/>
<rect x="352" y="500" width="96" height="10" rx="5" fill="{TINTA}"/><rect x="368" y="524" width="64" height="8" rx="4" fill="{PEDRA}"/>
<rect x="395" y="320" width="10" height="50" rx="5" fill="{TINTA}"/>
<path d="M400 230c30 40 30 70 0 92-30-22-30-52 0-92z" fill="{TINTA}"/>''',
    "almofada": lambda: sombra(rx=240) + f'''
<path d="M190 250q210-40 420 0q40 200 0 390q-210 40-420 0q-40-190 0-390z" fill="{TINTA}"/>
''' + "".join(f'<path d="M{230 + i*44} 270q-20 180 0 350" fill="none" stroke="{GRAFITE}" stroke-width="10"/>' for i in range(8)),
    "caderno": lambda: sombra(rx=210) + f'''
<rect x="240" y="170" width="320" height="460" rx="20" fill="{TINTA}" transform="rotate(-6 400 400)"/>
<rect x="510" y="170" width="16" height="460" fill="{GRAFITE}" transform="rotate(-6 400 400)"/>
<path d="M440 600 l0 90 l18 -18 l18 18 l0 -90 z" fill="{MALVA}" transform="rotate(-6 400 400)"/>
''' + "".join(f'<circle cx="{300 + (i%5)*40}" cy="{260 + (i//5)*40}" r="4" fill="{GRAFITE}" transform="rotate(-6 400 400)"/>' for i in range(25)),
    "caneta": lambda: sombra(rx=240, ry=20) + f'''
<g transform="rotate(-38 400 400)">
<rect x="130" y="370" width="480" height="60" rx="30" fill="{TINTA}"/>
<rect x="520" y="370" width="16" height="60" fill="{GRAFITE}"/>
<path d="M610 370 L690 400 L610 430 Z" fill="{GRAFITE}"/>
<rect x="210" y="350" width="170" height="20" rx="10" fill="{TINTA}"/>
</g>''',
    "teclado": lambda: sombra(rx=290) + f'''
<rect x="110" y="320" width="580" height="250" rx="28" fill="{TINTA}"/>
''' + "".join(
        f'<rect x="{140 + c*52 + (r%2)*12}" y="{345 + r*52}" width="42" height="40" rx="8" fill="{GRAFITE}"/>'
        for r in range(4) for c in range(10 if r < 3 else 0)
    ) + f'<rect x="230" y="501" width="340" height="40" rx="8" fill="{GRAFITE}"/><rect x="140" y="501" width="80" height="40" rx="8" fill="{MALVA}"/>',
    "mouse": lambda: sombra(rx=150) + f'''
<rect x="290" y="210" width="220" height="420" rx="110" fill="{TINTA}"/>
<path d="M400 210v150" stroke="{GRAFITE}" stroke-width="8"/>
<rect x="388" y="270" width="24" height="56" rx="12" fill="{MALVA}"/>''',
    "suporte": lambda: sombra(rx=260) + f'''
<path d="M180 620 L600 620 L640 380" fill="none" stroke="{TINTA}" stroke-width="26" stroke-linecap="round" stroke-linejoin="round"/>
<path d="M200 560 L640 380" stroke="{GRAFITE}" stroke-width="18" stroke-linecap="round"/>
<g transform="rotate(-22 420 470)"><rect x="180" y="440" width="480" height="24" rx="10" fill="{TINTA}"/><rect x="200" y="200" width="440" height="250" rx="16" fill="{TINTA}"/><rect x="222" y="222" width="396" height="206" rx="6" fill="{GRAFITE}"/></g>''',
    "fone": lambda: sombra(rx=220) + f'''
<path d="M230 470V380a170 170 0 0 1 340 0v90" fill="none" stroke="{TINTA}" stroke-width="34" stroke-linecap="round"/>
<rect x="180" y="410" width="110" height="200" rx="50" fill="{TINTA}"/>
<rect x="510" y="410" width="110" height="200" rx="50" fill="{TINTA}"/>
<rect x="270" y="440" width="30" height="140" rx="15" fill="{GRAFITE}"/><rect x="500" y="440" width="30" height="140" rx="15" fill="{GRAFITE}"/>''',
    "caixa-som": lambda: sombra(rx=170) + f'''
<rect x="270" y="190" width="260" height="450" rx="120" fill="{TINTA}"/>
''' + "".join(f'<circle cx="{310 + (i%6)*36}" cy="{300 + (i//6)*36}" r="7" fill="{GRAFITE}"/>' for i in range(54)) + f'<rect x="340" y="200" width="120" height="20" rx="10" fill="{GRAFITE}"/>',
    "fone-intra": lambda: sombra(rx=200) + f'''
<rect x="240" y="400" width="320" height="230" rx="90" fill="{TINTA}"/>
<path d="M240 470h320" stroke="{GRAFITE}" stroke-width="8"/>
<circle cx="400" cy="560" r="10" fill="{MALVA}"/>
<g><circle cx="330" cy="310" r="56" fill="{TINTA}"/><rect x="316" y="320" width="28" height="110" rx="14" fill="{TINTA}"/></g>
<g><circle cx="470" cy="290" r="56" fill="{GRAFITE}"/><rect x="456" y="300" width="28" height="110" rx="14" fill="{GRAFITE}"/></g>''',
    "toca-discos": lambda: sombra(rx=290) + f'''
<rect x="120" y="300" width="560" height="320" rx="30" fill="{TINTA}"/>
<circle cx="350" cy="455" r="130" fill="{GRAFITE}"/>
<circle cx="350" cy="455" r="100" fill="none" stroke="{TINTA}" stroke-width="3"/><circle cx="350" cy="455" r="70" fill="none" stroke="{TINTA}" stroke-width="3"/>
<circle cx="350" cy="455" r="34" fill="{MALVA}"/><circle cx="350" cy="455" r="6" fill="{TINTA}"/>
<path d="M600 350v120l-110 60" fill="none" stroke="{CLARO}" stroke-width="12" stroke-linecap="round" stroke-linejoin="round"/>
<circle cx="600" cy="350" r="20" fill="{CLARO}"/><circle cx="610" cy="580" r="12" fill="{MALVA}"/>''',
    "mochila": lambda: sombra(rx=200) + f'''
<path d="M340 200a60 60 0 0 1 120 0" fill="none" stroke="{TINTA}" stroke-width="22"/>
<rect x="240" y="200" width="320" height="440" rx="90" fill="{TINTA}"/>
<rect x="290" y="440" width="220" height="150" rx="34" fill="{GRAFITE}"/>
<rect x="320" y="470" width="160" height="12" rx="6" fill="{TINTA}"/>
<rect x="290" y="270" width="220" height="14" rx="7" fill="{GRAFITE}"/>''',
    "carteira": lambda: sombra(rx=210) + f'''
<rect x="200" y="300" width="400" height="300" rx="34" fill="{TINTA}"/>
<rect x="250" y="250" width="300" height="200" rx="14" fill="{CLARO}" transform="rotate(-8 400 350)"/>
<rect x="200" y="380" width="400" height="220" rx="34" fill="{TINTA}"/>
<path d="M200 420h400" stroke="{GRAFITE}" stroke-width="6" stroke-dasharray="14 12"/>''',
    "relogio": lambda: sombra(rx=150) + f'''
<rect x="345" y="120" width="110" height="560" rx="30" fill="{GRAFITE}"/>
<circle cx="400" cy="400" r="150" fill="{TINTA}"/>
<circle cx="400" cy="400" r="122" fill="{CLARO}"/>
''' + "".join(f'<rect x="397" y="290" width="6" height="18" rx="3" fill="{TINTA}" transform="rotate({i*30} 400 400)"/>' for i in range(12)) + f'''
<rect x="396" y="320" width="8" height="90" rx="4" fill="{TINTA}" transform="rotate(40 400 400)"/>
<rect x="397" y="300" width="6" height="110" rx="3" fill="{TINTA}" transform="rotate(-70 400 400)"/>
<circle cx="400" cy="400" r="10" fill="{TINTA}"/><rect x="545" y="385" width="26" height="30" rx="6" fill="{TINTA}"/>''',
    "oculos": lambda: sombra(rx=260) + f'''
<circle cx="285" cy="420" r="105" fill="{TINTA}"/><circle cx="515" cy="420" r="105" fill="{TINTA}"/>
<circle cx="285" cy="420" r="80" fill="{GRAFITE}"/><circle cx="515" cy="420" r="80" fill="{GRAFITE}"/>
<path d="M240 380a60 60 0 0 1 50-30" fill="none" stroke="{CLARO}" stroke-width="10" stroke-linecap="round" opacity="0.6"/>
<path d="M470 380a60 60 0 0 1 50-30" fill="none" stroke="{CLARO}" stroke-width="10" stroke-linecap="round" opacity="0.6"/>
<path d="M370 400q30-30 60 0" fill="none" stroke="{TINTA}" stroke-width="18" stroke-linecap="round"/>
<path d="M182 395 L110 360M618 395 L690 360" stroke="{TINTA}" stroke-width="16" stroke-linecap="round"/>''',
    "prensa": lambda: sombra(rx=170) + f'''
<rect x="392" y="140" width="16" height="120" fill="{TINTA}"/><circle cx="400" cy="140" r="22" fill="{TINTA}"/>
<rect x="290" y="250" width="220" height="30" rx="10" fill="{TINTA}"/>
<rect x="300" y="280" width="200" height="340" rx="16" fill="{CLARO}" opacity="0.6" stroke="{TINTA}" stroke-width="10"/>
<rect x="310" y="420" width="180" height="190" rx="8" fill="{GRAFITE}"/>
<path d="M500 320h40a30 30 0 0 1 30 30v170a30 30 0 0 1-30 30h-40" fill="none" stroke="{TINTA}" stroke-width="22"/>
<rect x="280" y="610" width="240" height="30" rx="12" fill="{TINTA}"/>''',
    "moedor": lambda: sombra(rx=150) + f'''
<rect x="310" y="330" width="180" height="310" rx="30" fill="{TINTA}"/>
<rect x="310" y="420" width="180" height="14" fill="{GRAFITE}"/>
<rect x="330" y="290" width="140" height="50" rx="14" fill="{GRAFITE}"/>
<rect x="394" y="200" width="12" height="100" fill="{TINTA}"/>
<path d="M400 205 L560 205" stroke="{TINTA}" stroke-width="14" stroke-linecap="round"/>
<circle cx="572" cy="205" r="26" fill="{MALVA}"/>''',
    "chaleira": lambda: sombra(rx=210) + f'''
<path d="M260 630 L280 380 Q400 330 520 380 L540 630 Z" fill="{TINTA}"/>
<path d="M290 400 Q240 280 120 250" fill="none" stroke="{TINTA}" stroke-width="20" stroke-linecap="round"/>
<path d="M520 400 q90 0 90 110 q0 100 -80 110" fill="none" stroke="{TINTA}" stroke-width="28"/>
<rect x="340" y="320" width="120" height="40" rx="14" fill="{GRAFITE}"/><circle cx="400" cy="312" r="16" fill="{MALVA}"/>''',
    "cafe-graos": lambda: sombra(rx=190) + f'''
<path d="M260 230 L540 230 L560 640 L240 640 Z" fill="{TINTA}"/>
<path d="M260 230 l20 -30 l240 0 l20 30" fill="{GRAFITE}"/>
<rect x="300" y="380" width="200" height="140" rx="12" fill="{CLARO}"/>
<ellipse cx="400" cy="440" rx="30" ry="40" fill="{TINTA}" transform="rotate(25 400 440)"/>
<path d="M392 408q22 30 -6 64" fill="none" stroke="{CLARO}" stroke-width="6" stroke-linecap="round"/>
<rect x="340" y="490" width="120" height="10" rx="5" fill="{PEDRA}"/>
<ellipse cx="610" cy="620" rx="22" ry="30" fill="{TINTA}" transform="rotate(40 610 620)"/><ellipse cx="190" cy="625" rx="20" ry="27" fill="{TINTA}" transform="rotate(-30 190 625)"/>''',
    "tapete-yoga": lambda: sombra(rx=280) + f'''
<rect x="140" y="430" width="420" height="180" rx="40" fill="{GRAFITE}"/>
<circle cx="560" cy="520" r="110" fill="{TINTA}"/>
<circle cx="560" cy="520" r="78" fill="none" stroke="{GRAFITE}" stroke-width="10"/>
<circle cx="560" cy="520" r="44" fill="none" stroke="{GRAFITE}" stroke-width="10"/>
<rect x="250" y="420" width="26" height="200" rx="8" fill="{MALVA}"/>''',
    "garrafa": lambda: sombra(rx=140) + f'''
<rect x="340" y="150" width="120" height="80" rx="20" fill="{GRAFITE}"/>
<rect x="320" y="220" width="160" height="30" rx="10" fill="{TINTA}"/>
<rect x="300" y="240" width="200" height="400" rx="60" fill="{TINTA}"/>
<rect x="330" y="290" width="22" height="280" rx="11" fill="{GRAFITE}"/>''',
}


def svg(nome, i):
    fundo = FUNDOS[i % len(FUNDOS)]
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 800 800" width="800" height="800">'
        f'<rect width="800" height="800" fill="{fundo}"/>'
        f"{DESENHOS[nome]()}</svg>\n"
    )


def main():
    DESTINO.mkdir(parents=True, exist_ok=True)
    for i, nome in enumerate(DESENHOS):
        (DESTINO / f"{nome}.svg").write_text(svg(nome, i), encoding="utf-8")
    print(f"{len(DESENHOS)} imagens em {DESTINO}")


if __name__ == "__main__":
    main()
