const root = document.documentElement;
root.classList.add("js");
const japanese = new Map(
  [...document.querySelectorAll("[data-i18n]")].map((element) => [
    element.dataset.i18n,
    element.textContent.trim(),
  ]),
);
const japaneseDescription = document.querySelector(
  "meta[name=description]",
).content;
const japaneseAlt = new Map(
  [...document.querySelectorAll("[data-i18n-alt]")].map((element) => [
    element.dataset.i18nAlt,
    element.alt,
  ]),
);
let language = navigator.language.toLowerCase().startsWith("ja") ? "ja" : "en";
try {
  const saved = localStorage.getItem("codexbar-site-language");
  if (saved === "ja" || saved === "en") language = saved;
} catch {
  /* Preferences are optional. */
}
const media = "./assets/media/";
const preview = document.querySelector("[data-preview-image]");
const panel = document.querySelector("#app-preview");
const tourButtons = [...document.querySelectorAll("[data-tour]")];
const appearances = [...document.querySelectorAll("[data-appearance]")];
let selectedAppearance = "dark";
let selectedTrend = "history";
let imageRequest = 0;
const tourScreens = {
  usage: {
    image: "usage-dark",
    title: "tour.usage.title",
    description: "tour.usage.description",
    kicker: "01 — YOUR DAILY OVERVIEW",
  },
  trends: {
    image: "history",
    title: "tour.trends.title",
    description: "tour.trends.description",
    kicker: "02 — A LITTLE PERSPECTIVE",
  },
  connections: {
    image: "connections",
    title: "tour.connections.title",
    description: "tour.connections.description",
    kicker: "03 — YOUR SERVICES, CONNECTED",
  },
  github: {
    image: "github",
    title: "tour.github.title",
    description: "tour.github.description",
    kicker: "04 — MADE BY YOU",
  },
};
const tourJapanese = {
  "tour.usage.title": "あとどれくらい？ もう、探し回らない。",
  "tour.usage.description":
    "複数のAIサービスの残量とリセット時刻をまとめて表示。最初に使い切りそうな枠も、使うペースも、すぐに見つかります。",
  "tour.trends.title": "使った分だけ、見えてくる。",
  "tour.trends.description":
    "履歴とグラフで、日々の使い方を振り返る。PCから共有したトークン履歴や推定費用も、同じ「推移」タブに整理できます。",
  "tour.connections.title": "いつものAIを、あなたの順番で。",
  "tour.connections.description":
    "サービスを追加して、接続方法を選ぶ。スマホでの取得も、Windowsとのペアリングも。「接続」と「推移」のサービス順も並び替えられます。",
  "tour.github.title": "今日の「作った」を、ひとつに。",
  "tour.github.description":
    "プロフィール、コミット数、コントリビューションカレンダー。実績やPinnedをウィジェットで追加して、自分のダッシュボードを作れます。",
};
function renderText() {
  root.lang = language;
  document.querySelector("meta[name=description]").content =
    language === "en" ? english["meta.description"] : japaneseDescription;
  document.querySelectorAll("[data-i18n]").forEach((element) => {
    const key = element.dataset.i18n;
    element.textContent =
      language === "en"
        ? (english[key] ?? japanese.get(key))
        : (tourJapanese[key] ?? japanese.get(key));
  });
  document.querySelectorAll("[data-i18n-alt]").forEach((element) => {
    const key = element.dataset.i18nAlt;
    element.alt =
      language === "en"
        ? english[key]
        : (japaneseAlt.get(key) ?? tourJapanese[key]);
  });
  document.querySelectorAll("[data-language]").forEach((button) => {
    button.setAttribute(
      "aria-pressed",
      String(button.dataset.language === language),
    );
  });
  document.title =
    language === "en"
      ? "CodexBar for Android — Less checking. More creating."
      : "CodexBar for Android — つくる時間に、もっと余裕を。";
}
function selectTour(name) {
  const screen = tourScreens[name];
  if (!screen || !preview) return;
  tourButtons.forEach((button) => {
    const selected = button.dataset.tour === name;
    button.setAttribute("aria-selected", String(selected));
    button.tabIndex = selected ? 0 : -1;
  });
  panel.setAttribute("aria-labelledby", `tour-tab-${name}`);
  document.querySelector("[data-preview-index]").textContent =
    `${String(Object.keys(tourScreens).indexOf(name) + 1).padStart(2, "0")} / 04`;
  document.querySelector("[data-preview-kicker]").textContent = screen.kicker;
  document.querySelector("[data-preview-title]").dataset.i18n = screen.title;
  document.querySelector("[data-preview-description]").dataset.i18n =
    screen.description;
  document.querySelector("[data-appearance-controls]").hidden =
    name !== "usage";
  document.querySelector("[data-trends-controls]").hidden = name !== "trends";
  renderText();
  const imageName =
    name === "usage"
      ? `usage-${selectedAppearance}`
      : name === "trends"
        ? selectedTrend
        : screen.image;
  const source = `${media}${imageName}.webp`;
  const request = ++imageRequest;
  panel.setAttribute("aria-busy", "true");
  const next = new Image();
  next.onload = () => {
    if (request !== imageRequest) return;
    preview.src = source;
    preview.style.visibility = "visible";
    preview.dataset.i18nAlt =
      name === "usage"
        ? selectedAppearance === "light"
          ? "image.light"
          : "image.usage"
        : name === "trends" && selectedTrend === "spend"
          ? "image.spend"
          : `image.${name}`;
    document.querySelector(".preview-caption").dataset.i18n = "tour.caption";
    panel.setAttribute("aria-busy", "false");
    renderText();
  };
  next.onerror = () => {
    if (request !== imageRequest) return;
    preview.style.visibility = "hidden";
    document.querySelector(".preview-caption").dataset.i18n = "tour.error";
    panel.setAttribute("aria-busy", "false");
    renderText();
  };
  next.src = source;
}
tourButtons.forEach((button, index) => {
  button.addEventListener("click", () => selectTour(button.dataset.tour));
  button.addEventListener("keydown", (event) => {
    let next;
    if (event.key === "ArrowRight" || event.key === "ArrowDown")
      next = (index + 1) % tourButtons.length;
    if (event.key === "ArrowLeft" || event.key === "ArrowUp")
      next = (index - 1 + tourButtons.length) % tourButtons.length;
    if (event.key === "Home") next = 0;
    if (event.key === "End") next = tourButtons.length - 1;
    if (next === undefined) return;
    event.preventDefault();
    tourButtons[next].focus();
    selectTour(tourButtons[next].dataset.tour);
  });
});
appearances.forEach((button) =>
  button.addEventListener("click", () => {
    selectedAppearance = button.dataset.appearance;
    appearances.forEach((candidate) =>
      candidate.setAttribute("aria-pressed", String(candidate === button)),
    );
    selectTour("usage");
  }),
);
const menu = document.querySelector(".menu-toggle");
document.querySelectorAll("[data-trend]").forEach((button) =>
  button.addEventListener("click", () => {
    selectedTrend = button.dataset.trend;
    document
      .querySelectorAll("[data-trend]")
      .forEach((candidate) =>
        candidate.setAttribute("aria-pressed", String(candidate === button)),
      );
    selectTour("trends");
  }),
);
const mobileNav = document.querySelector("#mobile-nav");
function closeMenu() {
  menu.setAttribute("aria-expanded", "false");
  mobileNav.hidden = true;
}
menu.addEventListener("click", () => {
  const open = menu.getAttribute("aria-expanded") !== "true";
  menu.setAttribute("aria-expanded", String(open));
  mobileNav.hidden = !open;
});
mobileNav
  .querySelectorAll("a")
  .forEach((link) => link.addEventListener("click", closeMenu));
document.addEventListener("keydown", (event) => {
  if (event.key === "Escape" && !mobileNav.hidden) {
    closeMenu();
    menu.focus();
  }
});
document.addEventListener("click", (event) => {
  if (!event.target.closest(".header-bar")) closeMenu();
});
const desktop = matchMedia("(min-width: 641px)");
desktop.addEventListener("change", () => {
  if (desktop.matches) closeMenu();
});
try {
  const version = JSON.parse(
    document.querySelector('script[type="application/ld+json"]').textContent,
  ).softwareVersion;
  if (/^\d+\.\d+\.\d+$/.test(version)) {
    const release = `https://github.com/lingmulongtai/CodexBar-android/releases`;
    document.querySelectorAll("[data-release-download]").forEach((link) => {
      link.href = `${release}/download/v${version}/CodexBar-Android-v${version}.apk`;
    });
    document.querySelectorAll("[data-release-notes]").forEach((link) => {
      link.href = `${release}/tag/v${version}`;
    });
    document
      .querySelector(".release-label")
      .setAttribute("aria-label", `v${version} · Android 8.0+`);
    document.querySelectorAll("[data-version]").forEach((element) => {
      element.textContent = `v${version}`;
    });
  }
} catch {
  /* The server-rendered links still lead to the official latest release. */
}
document.querySelectorAll("[data-current-year]").forEach((element) => {
  element.textContent = String(new Date().getFullYear());
});
const english = {
  "tab.history": "History",
  "tab.spend": "Spend",
  "image.spend": "Estimated usage costs and model breakdowns (sample data)",
  "meta.description":
    "AI quotas, resets, usage trends, and your GitHub activity in one Liquid Glass Android app. Free, open source, with optional Windows connectivity.",
  skip: "Skip to content",
  "nav.features": "Explore",
  "nav.design": "The design",
  "nav.connect": "Connect your PC",
  "nav.download": "Get the app ↓",
  "hero.line1": "Less checking.",
  "hero.line2": "More creating.",
  "hero.description":
    "Codex, Claude, and the rest of your AI toolkit. See your quotas, resets, and pace at a glance. Keep your GitHub activity close, in one beautifully crafted Android app.",
  "hero.download": "Get it for Android",
  "hero.explore": "Take a look inside",
  "hero.fine": "Free and open source. No ads. No trackers.",
  "image.note": "Real app screens. All values shown are sample data.",
  "providers.title": "YOUR EVERYDAY AI. ALL IN ONE PLACE.",
  "providers.note":
    "21 services. Available information and connection methods vary by provider.",
  "tour.kicker": "A LITTLE MORE CLARITY",
  "tour.heading": "A little clarity goes a long way.",
  "tour.description":
    "Check your headroom. Look back at your usage. See what you built. Choose a tab and explore the app.",
  "tour.caption": "Native emulator captures · sample data",
  "tour.error": "Preview unavailable. Select a tab to try again.",
  "tab.usage": "Usage",
  "tab.trends": "Trends",
  "tab.connections": "Connections",
  "tour.usage.title": "Your headroom. Right here.",
  "tour.usage.description":
    "See quotas and reset times across your AI services. Find the limit running out first, and understand your pace before you reach it.",
  "tour.trends.title": "Make sense of what you use.",
  "tour.trends.description":
    "Look back with history and charts. Token history and estimated costs shared from your PC live in the same Trends tab.",
  "tour.connections.title": "Your services. Your order.",
  "tour.connections.description":
    "Add a provider and choose how to connect. Use Android acquisition or pair with Windows, and arrange providers in Connections and Trends.",
  "tour.github.title": "A home for what you build.",
  "tour.github.description":
    "Your profile, commit counts, and contribution calendar. Add achievements and pinned repositories as widgets to make the dashboard yours.",
  "tour.cta": "Make it yours on Android",
  "design.line1": "A pleasure to look at.",
  "design.line2": "Even better to touch.",
  "design.description":
    "The clarity of Liquid Glass, on Android. Thoughtful details for something you use every day.",
  "design.glass.title": "A little light. A little you.",
  "design.glass.description":
    "Light or dark. Daily Unsplash photography beneath translucent cards and softly lit edges.",
  "design.motion.title": "Pages that follow your finger.",
  "design.motion.description":
    "Swipe through connected pages. Hold and slide across tabs. Add a light haptic touch, or leave it off.",
  "design.yours.title": "A place for everything you care about.",
  "design.yours.description":
    "Your provider order. Your GitHub widgets. The information you want, right where you want it.",
  "github.line1": "The AI you use.",
  "github.line2": "The code you create.",
  "github.description":
    "Bring your GitHub profile, daily commits, and contributions into one dashboard. Keep your favorite repositories just a tap away.",
  "github.calendar": "A year of activity, in one calendar.",
  "github.achievements": "Achievements and statistics, as widgets.",
  "github.pinned": "Your go-to repositories, pinned.",
  "github.permissions":
    "Private information follows what you share and authorize. No repository write permissions requested.",
  "connect.line1": "Running on your PC.",
  "connect.line2": "Right here in your hand.",
  "connect.description":
    "Pair with Win-CodexBar + Mobile Connect. Bring PC quotas, local token history, and estimated costs to Android, alongside phone-based acquisition.",
  "connect.download": "Get the Windows app",
  "connect.guide": "Connection guide ↗",
  "connect.note":
    "Shared history, costs, and settings require Windows v0.71.0+. Keep the PC running. Costs are estimates, not your bill.",
  "connect.tailscale": "Same Wi-Fi, or your Tailscale network.",
  "privacy.local.title": "Your credentials stay on your device.",
  "privacy.local.description":
    "Encrypted with Android Keystore. The app talks directly to service APIs.",
  "privacy.open.title": "Open, all the way through.",
  "privacy.open.description":
    "MIT licensed. Inspect the app and companion source on GitHub.",
  "privacy.update.title": "Your next update stays in the app.",
  "privacy.update.description":
    "Download and verify the APK in CodexBar. Confirm the final installation in Android.",
  "download.line1": "A better start",
  "download.line2": "to your next build.",
  "download.description": "For Android 8.0 and later. Free and open source.",
  "download.apk": "Download the APK",
  "download.notes": "Release notes ↗",
  "install.one.title": "Get the signed APK",
  "install.one.description":
    "Download the Android APK from the official GitHub Release.",
  "install.two.title": "Install on Android",
  "install.two.description":
    "Open the APK and approve installation in the Android confirmation screen.",
  "install.three.title": "Connect your everyday services",
  "install.three.description":
    "Add AI providers in Connections. Pair with Windows when you need it.",
  "download.recovery":
    "Using 0.16.1 or 0.16.2? Update once by installing the APK over your existing app. If your browser cannot finish the download, use the GitHub app. Do not uninstall or clear your data.",
  "faq.title": "Before you get started.",
  "faq.docs": "Read the documentation ↗",
  "faq.phone.q": "Can I use it with just my phone?",
  "faq.phone.a":
    "Providers with phone-based acquisition work directly on Android. Authentication methods vary; some need a CLI companion or Windows. Gemini Apps requires Windows and is separate from Gemini CLI.",
  "faq.private.q": "Can I see private GitHub activity?",
  "faq.private.a":
    "Sign in to GitHub and choose which repositories to share. Private contributions use an additional read-only profile authorization. What appears depends on the repositories and permissions you grant.",
  "faq.remote.q": "Can I connect to Windows away from home?",
  "faq.remote.a":
    "Connect your phone and PC to the same Tailscale network. Your phone does not need an exit node. Keep your PC and the Windows app running while you retrieve data.",
  "faq.cost.q": "Are the displayed costs my actual bill?",
  "faq.cost.a":
    "No. They are reference estimates based on local token history and model rates. Subscription fees and actual bills differ. Available history and breakdowns also vary by service.",
  "footer.releases": "Releases",
  "footer.issues": "Report an issue",
  "footer.affiliation": "Not affiliated with the listed AI providers.",
  "image.usage": "Liquid Glass AI quota dashboard (sample data)",
  "image.light": "Liquid Glass light appearance (sample data)",
  "image.github": "GitHub profile and contribution calendar (sample data)",
  "image.trends": "Usage history and charts (sample data)",
  "image.connections": "AI provider connections and ordering (sample data)",
  "image.pinned":
    "GitHub pinned repositories and technology stack (sample data)",
  "image.achievements": "GitHub achievement widgets (sample data)",
};
Object.assign(tourJapanese, {
  "image.spend": "推定費用とモデル別の内訳（サンプルデータ）",
  "tour.error": "画像を読み込めませんでした。タブを選び直して再試行できます。",
  "image.trends": "使用量の履歴とグラフ（サンプルデータ）",
  "image.connections": "AIサービスの接続と並び順（サンプルデータ）",
});
document.querySelectorAll("[data-language]").forEach((button) =>
  button.addEventListener("click", () => {
    language = button.dataset.language;
    try {
      localStorage.setItem("codexbar-site-language", language);
    } catch {
      /* Keep the in-memory preference. */
    }
    renderText();
  }),
);
renderText();
