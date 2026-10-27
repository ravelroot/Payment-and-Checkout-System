import { useMemo, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { useCart } from "../cart/CartContext";
import { Icon } from "../components/Icon";
import { Empty, Page, Pagination, Price, ProductImage, Skeleton } from "../components/Ui";
import { qs } from "../lib/api";
import { money } from "../lib/format";
import { isTyping, tint, useFavorites, useHotkey, useRecent } from "../lib/prefs";
import type { Category, Page as PageOf, ProductCard } from "../lib/types";
import { useApi, useDebounced } from "../lib/useApi";

const SORTS = [
  { value: "featured", label: "Destaques" },
  { value: "newest", label: "Novidades" },
  { value: "price_asc", label: "Menor preço" },
  { value: "price_desc", label: "Maior preço" },
  { value: "name", label: "Nome" }
];

export function StorePage() {
  const { user } = useAuth();
  const [params, setParams] = useSearchParams();
  const [q, setQ] = useState(params.get("q") ?? "");
  const query = useDebounced(q, 280);
  const category = params.get("categoria") ?? "";
  const sort = params.get("ordem") ?? "featured";
  const page = Number(params.get("pagina") ?? 0);
  const available = params.get("disponivel") === "1";
  const onlyFavorites = params.get("favoritos") === "1";
  const favorites = useFavorites(user?.id);
  const recent = useRecent(user?.id);
  const searchRef = useRef<HTMLInputElement>(null);

  useHotkey(
    (e) => e.key === "/" && !isTyping(e),
    (e) => {
      e.preventDefault();
      searchRef.current?.focus();
    }
  );

  const set = (k: string, v: string | null) => {
    const next = new URLSearchParams(params);
    if (v) next.set(k, v);
    else next.delete(k);
    if (k !== "pagina") next.delete("pagina");
    setParams(next, { replace: true });
  };

  const categories = useApi<Category[]>("/api/catalog/categories");
  const products = useApi<PageOf<ProductCard>>(
    "/api/catalog/products" + qs({ q: query, category, sort, page: onlyFavorites ? 0 : page, size: onlyFavorites ? 100 : 12, onlyAvailable: available })
  );
  const totalProducts = useMemo(() => categories.data?.reduce((s, c) => s + (c.active ? c.products : 0), 0) ?? 0, [categories.data]);
  const items = onlyFavorites ? products.data?.items.filter((p) => favorites.has(p.id)) ?? [] : products.data?.items ?? [];
  const currentCategory = categories.data?.find((c) => c.slug === category);
  const filtered = Boolean(query || category || available || onlyFavorites);

  return (
    <Page className="store">
      <section className="store-intro">
        <div className="wrap store-intro-inner">
          <div className="store-intro-text">
            <span className="eyebrow">{currentCategory ? "Categoria" : "Loja Velo"}</span>
            <h1 className="display">{currentCategory ? currentCategory.name : "Objetos para o dia a dia, escolhidos com calma."}</h1>
            {currentCategory?.description ? (
              <p className="muted">{currentCategory.description}</p>
            ) : (
              <p className="muted">Casa, escritório, áudio, café e bem-estar. Envio para todo o Brasil e troca em 7 dias.</p>
            )}
          </div>
          <ul className="benefits" aria-label="Vantagens">
            <li>
              <Icon name="truck" size={18} />
              <span>
                <strong>Frete grátis</strong>
                <span className="muted small">acima de {money(299)}</span>
              </span>
            </li>
            <li>
              <Icon name="card" size={18} />
              <span>
                <strong>10x sem juros</strong>
                <span className="muted small">no cartão de crédito</span>
              </span>
            </li>
            <li>
              <Icon name="pix" size={18} />
              <span>
                <strong>PIX na hora</strong>
                <span className="muted small">aprovação imediata</span>
              </span>
            </li>
          </ul>
        </div>
      </section>

      <section className="wrap store-toolbar">
        <div className="chips" role="group" aria-label="Categorias">
          <button className="chip" aria-pressed={!category} onClick={() => set("categoria", null)}>
            Todos {totalProducts > 0 && <span className="chip-count num">{totalProducts}</span>}
          </button>
          {categories.data
            ?.filter((c) => c.active)
            .map((c) => (
              <button key={c.id} className="chip" aria-pressed={category === c.slug} onClick={() => set("categoria", c.slug)}>
                <span className="chip-swatch" style={{ background: tint(c.slug) }} />
                {c.name} <span className="chip-count num">{c.products}</span>
              </button>
            ))}
        </div>
        <div className="search">
          <Icon name="search" size={18} />
          <input
            ref={searchRef}
            className="input"
            placeholder="Buscar produtos"
            value={q}
            onChange={(e) => {
              setQ(e.target.value);
              set("q", e.target.value || null);
            }}
            aria-label="Buscar produtos"
          />
          <kbd className="kbd" aria-hidden>
            /
          </kbd>
        </div>
        <div className="row store-toolbar-end">
          <button
            className={`toggle ${onlyFavorites ? "on" : ""}`}
            aria-pressed={onlyFavorites}
            onClick={() => set("favoritos", onlyFavorites ? null : "1")}
          >
            <Icon name="heart" size={16} /> Favoritos {favorites.ids.length > 0 && <span className="num">{favorites.ids.length}</span>}
          </button>
          <label className="check">
            <input type="checkbox" checked={available} onChange={(e) => set("disponivel", e.target.checked ? "1" : null)} />
            Só disponíveis
          </label>
          <select className="select compact" value={sort} onChange={(e) => set("ordem", e.target.value)} aria-label="Ordenar">
            {SORTS.map((s) => (
              <option key={s.value} value={s.value}>
                {s.label}
              </option>
            ))}
          </select>
        </div>
      </section>

      <section className="wrap">
        {filtered && products.data && (
          <p className="result-line muted small" aria-live="polite">
            {onlyFavorites ? `${items.length} favorito${items.length === 1 ? "" : "s"}` : `${products.data.total} resultado${products.data.total === 1 ? "" : "s"}`}
            {query && (
              <>
                {" "}
                para <strong>“{query}”</strong>
              </>
            )}
            <button
              className="text-link"
              onClick={() => {
                setQ("");
                setParams(new URLSearchParams(), { replace: true });
              }}
            >
              Limpar filtros
            </button>
          </p>
        )}
        {products.loading && !products.data ? (
          <div className="product-grid">
            {Array.from({ length: 8 }).map((_, i) => (
              <div key={i} className="stack" style={{ gap: 12 }}>
                <div className="skeleton" style={{ aspectRatio: "4 / 5" }} />
                <Skeleton h={14} w="70%" />
                <Skeleton h={14} w="40%" />
              </div>
            ))}
          </div>
        ) : products.data && items.length === 0 ? (
          <Empty
            icon={onlyFavorites ? "heart" : "search"}
            title={onlyFavorites ? "Nenhum favorito ainda" : "Nada por aqui"}
            text={onlyFavorites ? "Toque no coração de um produto para guardar e comparar depois." : "Tente outra busca ou veja todas as categorias."}
            action={
              <button
                className="btn ghost"
                onClick={() => {
                  setQ("");
                  setParams(new URLSearchParams(), { replace: true });
                }}
              >
                {onlyFavorites ? "Ver todos os produtos" : "Limpar filtros"}
              </button>
            }
          />
        ) : (
          <>
            <div className="product-grid">
              {items.map((p) => (
                <ProductTile key={p.id} p={p} favorite={favorites.has(p.id)} onFavorite={() => favorites.toggle(p.id)} />
              ))}
            </div>
            {products.data && !onlyFavorites && (
              <Pagination page={products.data.page} pages={products.data.pages} total={products.data.total} onPage={(p) => set("pagina", String(p))} />
            )}
          </>
        )}
      </section>

      {recent.items.length > 0 && !filtered && (
        <section className="wrap recent">
          <div className="section-head">
            <h2>Vistos recentemente</h2>
            <button className="btn quiet sm" onClick={recent.clear}>
              Limpar
            </button>
          </div>
          <div className="recent-row">
            {recent.items.map((r) => (
              <Link key={r.id} to={`/produto/${r.slug}`} className="recent-item">
                <span className="recent-media" style={{ background: tint(r.categorySlug) }}>
                  <ProductImage src={r.imageUrl} alt={r.name} className="thumb" />
                </span>
                <span>
                  <span className="recent-name">{r.name}</span>
                  <span className="muted small num">{money(r.price)}</span>
                </span>
              </Link>
            ))}
          </div>
        </section>
      )}
    </Page>
  );
}

function ProductTile({ p, favorite, onFavorite }: { p: ProductCard; favorite: boolean; onFavorite: () => void }) {
  const { add } = useCart();
  const out = p.stock <= 0;
  const off = p.compareAtPrice && p.compareAtPrice > p.price ? Math.round((1 - p.price / p.compareAtPrice) * 100) : 0;
  return (
    <article className={`product-tile ${out ? "out" : ""}`}>
      <div className="product-tile-media" style={{ background: tint(p.categorySlug) }}>
        <Link to={`/produto/${p.slug}`} tabIndex={-1} aria-hidden>
          <ProductImage src={p.imageUrl} alt="" />
        </Link>
        <div className="product-tile-tags">
          {off > 0 && <span className="pill promo num">−{off}%</span>}
          {out ? <span className="pill line">Esgotado</span> : p.stock <= 5 ? <span className="pill line">Últimas {p.stock}</span> : null}
        </div>
        <button
          className={`fav-button ${favorite ? "on" : ""}`}
          onClick={onFavorite}
          aria-pressed={favorite}
          aria-label={favorite ? `Tirar ${p.name} dos favoritos` : `Guardar ${p.name} nos favoritos`}
        >
          <Icon name={favorite ? "heartFill" : "heart"} size={17} />
        </button>
        <button className="quick-add" disabled={out} onClick={() => add(p.id, 1, p.name)} aria-label={`Adicionar ${p.name} ao carrinho`}>
          <Icon name="plus" size={16} /> {out ? "Esgotado" : "Adicionar"}
        </button>
      </div>
      <div className="product-tile-body">
        <span className="muted small">{p.category}</span>
        <Link to={`/produto/${p.slug}`} className="product-tile-name">
          {p.name}
        </Link>
        <div className="row tile-price">
          <Price value={p.price} compareAt={p.compareAtPrice} />
        </div>
        <span className="muted small num">10x de {money(p.price / 10)}</span>
      </div>
    </article>
  );
}
