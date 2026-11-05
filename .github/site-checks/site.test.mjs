import assert from "node:assert/strict";
import { before, after, test } from "node:test";
import { createServer } from "node:http";
import { readFile, mkdir } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { resolve, sep, extname } from "node:path";
import * as playwright from "playwright";

const root = fileURLToPath(new URL("../../docs/", import.meta.url));
const artifacts = fileURLToPath(
  new URL("../../output/playwright/", import.meta.url),
);
const engine = process.env.SITE_BROWSER || "chromium";
const axePath = fileURLToPath(
  new URL("node_modules/axe-core/axe.min.js", import.meta.url),
);
let browser, server, origin;
const types = {
  ".html": "text/html",
  ".css": "text/css",
  ".js": "text/javascript",
  ".webp": "image/webp",
  ".png": "image/png",
  ".svg": "image/svg+xml",
};
before(async () => {
  assert.ok(["chromium", "firefox", "webkit"].includes(engine));
  server = createServer(async (req, res) => {
    try {
      const pathname = decodeURIComponent(
        new URL(req.url, "http://localhost").pathname,
      );
      const path = resolve(
        root,
        `.${pathname === "/" ? "/index.html" : pathname}`,
      );
      if (!path.startsWith(resolve(root) + sep)) {
        res.writeHead(403).end();
        return;
      }
      const content = await readFile(path);
      res
        .writeHead(200, {
          "Content-Type": types[extname(path)] || "application/octet-stream",
        })
        .end(content);
    } catch {
      res.writeHead(404).end();
    }
  });
  await new Promise((done) => server.listen(0, "127.0.0.1", done));
  origin = `http://127.0.0.1:${server.address().port}`;
  browser = await playwright[engine].launch();
  await mkdir(artifacts, { recursive: true });
});
after(async () => {
  await browser?.close();
  if (server) await new Promise((done) => server.close(done));
});

function check(name, fn, options = {}) {
  test(name, { timeout: 60000 }, async () => {
    const context = await browser.newContext({
      viewport: { width: 390, height: 844 },
      locale: "ja-JP",
      reducedMotion: "reduce",
      ...options,
    });
    const page = await context.newPage();
    const errors = [];
    page.on("pageerror", (error) => errors.push(error.message));
    try {
      await page.goto(origin);
      await fn(page, context);
      assert.deepEqual(errors, [], "Unexpected page script errors");
    } catch (error) {
      await page
        .screenshot({
          path: resolve(
            artifacts,
            `${engine}-${name.replace(/\W+/g, "-")}-failure.png`,
          ),
          fullPage: true,
        })
        .catch(() => {});
      throw error;
    } finally {
      await context.close();
    }
  });
}
async function language(page, value) {
  await page.locator(`[data-language="${value}"]`).first().click();
}
async function previewReady(page, name) {
  await page.waitForFunction(
    (requested) => {
      const panel = document.querySelector("#app-preview");
      const image = document.querySelector("[data-preview-image]");
      return panel.getAttribute("aria-busy") === "false" &&
        image.complete && image.naturalWidth > 0 &&
        new RegExp(`${requested}(?:-\\d+)?\\.webp$`).test(image.currentSrc);
    },
    name,
  );
  assert.match(
    await page.locator("[data-preview-image]").evaluate((el) => el.currentSrc),
    new RegExp(`${name}(?:-\\d+)?\\.webp$`),
  );
}
async function accessibility(page) {
  await page.addScriptTag({ path: axePath });
  const violations = await page.evaluate(async () =>
    (
      await axe.run(document, {
        runOnly: {
          type: "tag",
          values: ["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"],
        },
      })
    ).violations.map((v) => ({
      id: v.id,
      targets: v.nodes.map((n) => n.target),
    })),
  );
  assert.deepEqual(violations, []);
}

check("responsive localized landing and accessibility", async (page) => {
  for (const lang of ["ja", "en"]) {
    await language(page, lang);
    assert.equal(await page.locator("html").getAttribute("lang"), lang);
    for (const width of [320, 390, 768, 1440, 1920]) {
      await page.setViewportSize({ width, height: 900 });
      assert.ok(
        await page.evaluate(
          () => document.documentElement.scrollWidth <= innerWidth,
        ),
        `${lang} overflow at ${width}`,
      );
      assert.deepEqual(
        await page
          .locator("[data-i18n]")
          .evaluateAll((nodes) =>
            nodes
              .filter((n) => !n.textContent.trim())
              .map((n) => n.dataset.i18n),
          ),
        [],
      );
      if ([390, 1440].includes(width)) {
        await page.evaluate(() => window.scrollTo(0, 0));
        await accessibility(page);
        await page.screenshot({
          path: resolve(artifacts, `${engine}-${lang}-${width}.png`),
        });
      }
    }
  }
  await page.reload();
  assert.equal(await page.locator("html").getAttribute("lang"), "en");
});

check("service setup requirements and documentation", async (page) => {
  const services = {
    codex: "phone",
    claude: "pc",
    "gemini-cli": "pc",
    "gemini-apps": "windows",
    copilot: "phone",
    github: "phone",
  };
  for (const lang of ["ja", "en"]) {
    await language(page, lang);
    for (const [service, kind] of Object.entries(services)) {
      await page.locator("#setup-provider").selectOption(service);
      assert.equal(
        await page.locator("[data-setup-mode]").getAttribute("data-kind"),
        kind,
      );
      assert.equal(await page.locator("[data-setup-steps] li").count(), 3);
      assert.match(
        await page.locator("[data-setup-docs]").getAttribute("href"),
        /^https:\/\/github.com\/lingmulongtai\//,
      );
    }
    assert.ok(
      (
        await page.locator("[data-windows-guide]").getAttribute("href")
      ).endsWith(lang === "ja" ? "README.ja-JP.md" : "README.md"),
    );
  }
});

check("tour keyboard facets and latest requested image", async (page) => {
  await page.locator("[data-tour=usage]").focus();
  await page.keyboard.press("ArrowRight");
  await previewReady(page, "history");
  await page.locator("[data-trend=spend]").click();
  await previewReady(page, "spend");
  await page.locator("[data-tour=usage]").click();
  await page.locator("[data-appearance=light]").click();
  await previewReady(page, "usage-light");
  await page.route("**/history*.webp", async (route) => {
    await new Promise((done) => setTimeout(done, 180));
    await route.continue();
  });
  await page.evaluate(() =>
    ["trends", "connections", "github"].forEach((name) =>
      document.querySelector(`[data-tour=${name}]`).click(),
    ),
  );
  await previewReady(page, "github");
  await page.waitForTimeout(250);
  await previewReady(page, "github");
  assert.equal(
    await page.locator("#app-preview").getAttribute("aria-labelledby"),
    "tour-tab-github",
  );
  const version = await page
    .locator('script[type="application/ld+json"]')
    .evaluate((el) => JSON.parse(el.textContent).softwareVersion);
  assert.ok(
    (
      await page.locator("[data-release-download]").first().getAttribute("href")
    ).endsWith(`/v${version}/CodexBar-Android-v${version}.apk`),
  );
});

check("failed preview is recoverable and never mislabeled", async (page) => {
  await page.route("**/connections*.webp", (route) => route.abort());
  await page.locator("[data-tour=connections]").click();
  await page.waitForFunction(
    () =>
      document.querySelector("#app-preview").getAttribute("aria-busy") ===
      "false",
  );
  assert.equal(
    await page
      .locator("[data-preview-image]")
      .evaluate((el) => el.style.visibility),
    "hidden",
  );
  assert.equal(await page.locator("[data-preview-zoom]").isDisabled(), true);
  assert.match(await page.locator(".preview-caption").textContent(), /再試行/);
  await page.unroute("**/connections*.webp");
  await page.locator("[data-tour=connections]").click();
  await previewReady(page, "connections");
  assert.equal(await page.locator("[data-preview-zoom]").isEnabled(), true);
});

check("full size viewer keeps focus and restores the page", async (page) => {
  await page.locator("[data-tour=usage]").click();
  await page.locator("[data-appearance=light]").click();
  await previewReady(page, "usage-light");
  const button = page.locator("[data-preview-zoom]");
  await button.scrollIntoViewIfNeeded();
  const scroll = await page.evaluate(() => scrollY);
  await button.click();
  await page.waitForFunction(
    () => document.querySelector("[data-full-preview]").naturalWidth === 540,
  );
  assert.ok(
    (await page.locator("[data-full-preview]").getAttribute("src")).endsWith(
      "/usage-light.webp",
    ),
  );
  await page.keyboard.press("Tab");
  assert.ok(
    await page
      .locator("#screenshot-dialog")
      .evaluate((el) => el.contains(document.activeElement)),
  );
  await accessibility(page);
  await page.keyboard.press("Escape");
  assert.ok(await button.evaluate((el) => el === document.activeElement));
  const restoredScroll = await page.evaluate(() => scrollY);
  assert.ok(
    Math.abs(restoredScroll - scroll) < 2,
    `Scroll changed from ${scroll} to ${restoredScroll}`,
  );
  assert.equal(
    await page
      .locator("html")
      .evaluate((el) => el.classList.contains("modal-open")),
    false,
  );
});

check("phone transfer copy success and permission fallback", async (page) => {
  await language(page, "en");
  await page.locator("[data-open-transfer]").click();
  await page.waitForFunction(
    () => document.querySelector(".transfer-dialog img").naturalWidth === 328,
  );
  await page.evaluate(() =>
    Object.defineProperty(navigator, "clipboard", {
      configurable: true,
      value: {
        writeText: async (value) => {
          window.copiedLink = value;
        },
      },
    }),
  );
  await page.locator("[data-copy-url]").click();
  const expected = "https://lingmulongtai.github.io/CodexBar-android/#download";
  assert.equal(await page.evaluate(() => window.copiedLink), expected);
  assert.equal(
    await page.locator("[data-copy-status]").textContent(),
    "Link copied.",
  );
  await page.evaluate(() =>
    Object.defineProperty(navigator, "clipboard", {
      configurable: true,
      value: {
        writeText: async () => {
          throw Error("denied");
        },
      },
    }),
  );
  await page.locator("[data-copy-url]").click();
  assert.ok(
    await page
      .locator("#transfer-url")
      .evaluate(
        (el) =>
          document.activeElement === el &&
          el.selectionStart === 0 &&
          el.selectionEnd === el.value.length,
      ),
  );
  assert.match(
    await page.locator("[data-copy-status]").textContent(),
    /URL selected/,
  );
  await accessibility(page);
  await page.keyboard.press("Escape");
  assert.ok(
    await page
      .locator("[data-open-transfer]")
      .evaluate((el) => el === document.activeElement),
  );
});

check("optional storage and mobile navigation", async (page) => {
  await page.addInitScript(() => {
    for (const method of ["getItem", "setItem"])
      Storage.prototype[method] = () => {
        throw Error("blocked");
      };
  });
  await page.reload();
  await language(page, "en");
  assert.equal(await page.locator("html").getAttribute("lang"), "en");
  await page.locator(".menu-toggle").click();
  assert.equal(await page.locator("#mobile-nav").isVisible(), true);
  await page.keyboard.press("Escape");
  assert.equal(await page.locator("#mobile-nav").isVisible(), false);
  await page.locator(".menu-toggle").click();
  await page.locator('#mobile-nav a[href="#connect"]').click();
  assert.equal(await page.locator("#mobile-nav").isVisible(), false);
});

check(
  "no JavaScript still offers content and official downloads",
  async (page) => {
    assert.ok(await page.locator("h1").isVisible());
    assert.equal(
      await page
        .locator("[data-release-download]")
        .first()
        .getAttribute("href"),
      "https://github.com/lingmulongtai/CodexBar-android/releases/latest",
    );
    assert.equal(await page.locator(".setup-controls").isVisible(), false);
    assert.ok(await page.locator("#setup-guide noscript").isVisible());
    assert.equal(await page.locator("[data-preview-zoom]").isVisible(), false);
    await page.locator("#install-help summary").click();
    assert.equal(await page.locator("#install-help").getAttribute("open"), "");
  },
  { javaScriptEnabled: false },
);
