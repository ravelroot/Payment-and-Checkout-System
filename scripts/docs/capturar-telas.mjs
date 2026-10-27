// Gera as capturas de tela usadas no README e nos PDFs (docs/images/tela-*.png).
// Uso: com a Velo rodando (padrão http://localhost:8080 ou VELO_URL), rode
//   node scripts/docs/capturar-telas.mjs
// Requer o pacote "playwright" com o Chromium instalado.
import { createRequire } from "node:module";
import { mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const { chromium } = createRequire(import.meta.url)("playwright");

const BASE = process.env.VELO_URL ?? "http://localhost:8080";
const OUT = join(dirname(fileURLToPath(import.meta.url)), "..", "..", "docs", "images");
const SENHA = "Velo@2026";
mkdirSync(OUT, { recursive: true });

const wait = (ms) => new Promise((r) => setTimeout(r, ms));

// O token fica só em memória: navegar com page.goto encerraria a sessão.
// Por isso a troca de tela usa o histórico do próprio React Router.
async function go(page, path) {
  await page.evaluate((p) => {
    window.history.pushState({}, "", p);
    window.dispatchEvent(new PopStateEvent("popstate"));
  }, path);
  await page.waitForLoadState("networkidle").catch(() => {});
  await wait(700);
}

async function login(page, email, theme = "light") {
  await page.goto(BASE + "/entrar");
  await page.evaluate((t) => localStorage.setItem("velo.theme", JSON.stringify(t)), theme);
  await page.goto(BASE + "/entrar");
  await page.fill('input[type="email"]', email);
  await page.fill('input[type="password"]', SENHA);
  await page.click('form button.btn.lg');
  await page.waitForURL((u) => !u.pathname.startsWith("/entrar"), { timeout: 15000 });
  await page.waitForLoadState("networkidle").catch(() => {});
  await wait(600);
}

async function shot(page, name, opts = {}) {
  await page.mouse.move(2, 2);
  await wait(250);
  await page.screenshot({ path: join(OUT, `tela-${name}.png`), ...opts });
  console.log("ok", name);
}

const browser = await chromium.launch();
const desktop = { viewport: { width: 1360, height: 850 }, deviceScaleFactor: 1, locale: "pt-BR", timezoneId: "America/Sao_Paulo" };

// Entrada
{
  const ctx = await browser.newContext(desktop);
  const page = await ctx.newPage();
  await page.goto(BASE + "/entrar");
  await page.evaluate(() => localStorage.setItem("velo.theme", JSON.stringify("light")));
  await page.goto(BASE + "/entrar");
  await wait(600);
  await shot(page, "login");
  await ctx.close();
}

// Cliente
{
  const ctx = await browser.newContext(desktop);
  const page = await ctx.newPage();
  await login(page, "cliente@velo.dev");
  // favoritos de exemplo
  await go(page, "/");
  const hearts = page.locator(".fav-button");
  if ((await hearts.count()) > 3) {
    await hearts.nth(1).click();
    await hearts.nth(3).click();
  }
  await page.evaluate(() => window.scrollTo(0, 0));
  await wait(400);
  await page.screenshot({ path: join(OUT, "tela-vitrine.png") });
  console.log("ok vitrine");

  const first = await page.locator(".product-tile-name").nth(4).getAttribute("href");
  await go(page, first);
  await page.click(".installments-head");
  await page.fill("#ship-cep", "22240-003");
  await page.click(".ship-form button");
  await wait(800);
  await shot(page, "produto");

  // carrinho com alguns itens
  await go(page, "/");
  const adds = page.locator(".quick-add:not([disabled])");
  for (const i of [0, 2, 5]) {
    await page.locator(".product-tile").nth(i).hover();
    await adds.nth(i).click();
    await wait(500);
  }
  await page.keyboard.press("Escape");
  await go(page, "/carrinho");
  await wait(3500);
  await shot(page, "carrinho");

  await go(page, "/checkout");
  await wait(600);
  await page.fill('input[autocomplete="cc-number"]', "4242 4242 4242 4242");
  await page.fill('input[autocomplete="cc-name"]', "CLARA CLIENTE");
  await page.fill('input[autocomplete="cc-exp"]', "12/30");
  await wait(500);
  await shot(page, "checkout");

  // pedido com PIX
  await page.click('.method:has-text("PIX")');
  await wait(400);
  await page.click(".summary .btn.lg");
  await page.waitForURL(/\/pedidos\//, { timeout: 15000 });
  await wait(3800);
  await page.locator(".pix-box").scrollIntoViewIfNeeded();
  await page.evaluate(() => window.scrollBy(0, 160));
  await wait(400);
  await shot(page, "pix");

  await go(page, "/pedidos");
  const delivered = page.locator('.order-row:has-text("Entregue"), .order-row:has-text("Enviado")').first();
  if (await delivered.count()) {
    await delivered.click();
    await wait(1000);
  }
  await shot(page, "pedido");

  await go(page, "/conta");
  await shot(page, "conta");
  await ctx.close();
}

// Celular
{
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true, locale: "pt-BR", timezoneId: "America/Sao_Paulo" });
  const page = await ctx.newPage();
  await login(page, "cliente@velo.dev");
  await go(page, "/");
  await shot(page, "celular");
  await ctx.close();
}

// Administração
{
  const ctx = await browser.newContext(desktop);
  const page = await ctx.newPage();
  await login(page, "admin@velo.dev");
  await go(page, "/admin");
  await wait(800);
  await shot(page, "painel");
  await go(page, "/admin/pedidos");
  await shot(page, "admin-pedidos");
  await go(page, "/admin/produtos");
  await page.locator("tbody tr").first().click();
  await wait(1000);
  await shot(page, "admin-produto");
  await go(page, "/admin/integracoes");
  await shot(page, "integracoes");
  await go(page, "/admin");
  await page.keyboard.press("Control+k");
  await wait(300);
  await page.keyboard.type("VL1");
  await wait(900);
  await shot(page, "comandos");
  await page.keyboard.press("Escape");
  await ctx.close();
}

// Tema escuro
{
  const ctx = await browser.newContext({ ...desktop, colorScheme: "dark" });
  const page = await ctx.newPage();
  await login(page, "cliente@velo.dev", "dark");
  await go(page, "/");
  await shot(page, "escuro");
  await ctx.close();
}

await browser.close();
