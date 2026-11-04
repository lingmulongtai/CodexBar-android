(() => {
  const repo = "https://github.com/lingmulongtai/CodexBar-android/";
  const profiles = {
    codex: {
      name: "Codex",
      kind: "phone",
      docs: repo + "#device-code-sign-in-exact-flow",
      ja: {
        mode: "スマホだけ",
        description:
          "使用枠はスマホだけで確認できます。PCのローカルトークン履歴は、追加のPC連携で表示します。",
        steps: [
          "「接続」からCodexを開き、アカウント接続を選ぶ。",
          "アプリのコードをコピーし、公式のサインイン画面で認可する。",
          "アプリに戻り、接続完了の表示を待つ。",
        ],
      },
      en: {
        mode: "Phone only",
        description:
          "Check subscription quotas on your phone. Local PC token history is available through an additional PC connection.",
        steps: [
          "Open Codex in Connections and choose Connect account.",
          "Copy the code and authorize on the official sign-in page opened by the app.",
          "Return to CodexBar and wait for the connected state.",
        ],
      },
    },
    claude: {
      name: "Claude",
      kind: "pc",
      docs: repo + "blob/main/companion/claude/README.md",
      ja: {
        mode: "PCが必要",
        description:
          "Windows・macOS・Linuxの公式Claude Codeとcompanionを使います。スマホ単独のアカウント接続には対応していません。",
        steps: [
          "PCの公式Claude Codeでサインインし、使用枠を確認する。",
          "このアプリのReleaseからClaude companionを入手して起動する。",
          "Androidの「接続 → Claude」でQRを読み取り、ペアリングを確認する。",
        ],
      },
      en: {
        mode: "PC required",
        description:
          "Use official Claude Code and the companion on Windows, macOS, or Linux. Phone-only sign-in is not supported.",
        steps: [
          "Sign in to official Claude Code on your computer and confirm your usage limits.",
          "Get and start the Claude companion from this app’s Release.",
          "Scan its QR in Connections → Claude on Android, then confirm pairing.",
        ],
      },
    },
    "gemini-cli": {
      name: "Gemini CLI",
      kind: "pc",
      docs: repo + "blob/main/companion/gemini/README.md",
      ja: {
        mode: "PCが必要",
        description:
          "PCの公式Gemini CLIとcompanionで、モデルごとの使用枠を取得します。Googleの認証情報はPCに残ります。",
        steps: [
          "PCの公式Gemini CLIでサインインし、使用枠を確認する。",
          "このアプリのReleaseからGemini companionを入手して起動する。",
          "AndroidでcompanionのQRを読み取り、ペアリングを確認する。",
        ],
      },
      en: {
        mode: "PC required",
        description:
          "The official Gemini CLI and a local companion retrieve per-model limits. Google credentials stay on your computer.",
        steps: [
          "Sign in to official Gemini CLI on your computer and confirm the quota view.",
          "Get and start the Gemini companion from this app’s Release.",
          "Scan the companion QR on Android and confirm pairing.",
        ],
      },
    },
    "gemini-apps": {
      name: "Gemini Apps",
      kind: "windows",
      docs: "https://github.com/lingmulongtai/Win-CodexBar-Mobile-Connect/blob/main/docs/gemini-apps.md",
      ja: {
        mode: "Windowsが必要",
        description:
          "Win-CodexBar + Mobile Connectで取得します。Gemini CLIとは別の使用枠です。",
        steps: [
          "Windowsの公式Geminiアプリにサインインする。",
          "Win-CodexBarの「Providers → Gemini Apps」で接続する。",
          "AndroidでWindowsとペアリングし、取得元にWindowsを選ぶ。",
        ],
      },
      en: {
        mode: "Windows required",
        description:
          "Retrieve limits through Win-CodexBar + Mobile Connect. These are separate from Gemini CLI quotas.",
        steps: [
          "Sign in to the official Gemini app on Windows.",
          "Connect Gemini Apps under Providers in Win-CodexBar.",
          "Pair Android with Windows and select Windows acquisition.",
        ],
      },
    },
    copilot: {
      name: "GitHub Copilot",
      kind: "phone",
      docs: repo + "#device-code-sign-in-exact-flow",
      ja: {
        mode: "スマホだけ",
        description:
          "Copilotの使用枠をスマホで確認できます。プロフィールや活動を見るGitHubダッシュボードは、別途連携できます。",
        steps: [
          "「接続」からGitHub Copilotを開く。",
          "アプリのコードをコピーし、GitHubで認可する。",
          "アプリに戻り、使用枠の取得と保存が完了するまで待つ。",
        ],
      },
      en: {
        mode: "Phone only",
        description:
          "Check Copilot quotas on your phone. The GitHub dashboard for your profile and activity has its own connection.",
        steps: [
          "Open GitHub Copilot in Connections.",
          "Copy the app’s code and authorize on GitHub.",
          "Return to the app and wait for quota validation and saving to finish.",
        ],
      },
    },
    github: {
      name: "GitHub",
      kind: "phone",
      docs: repo + "blob/main/docs/github-profile-widgets.md",
      ja: {
        mode: "スマホだけ",
        description:
          "プロフィール・コミット・リポジトリを表示します。非公開の貢献には、追加の読み取り認可を使います。",
        steps: [
          "「GitHub」タブのログインを選ぶ。",
          "表示されたコードをコピーし、GitHubで認可する。",
          "共有するリポジトリを選び、必要なら非公開の貢献も連携する。",
        ],
      },
      en: {
        mode: "Phone only",
        description:
          "See your profile, commits, and repositories. Private contributions use an additional read-only authorization.",
        steps: [
          "Choose Sign in on the GitHub tab.",
          "Copy the displayed code and authorize on GitHub.",
          "Choose which repositories to share, and authorize private contributions if needed.",
        ],
      },
    },
  };
  const picker = document.querySelector("#setup-provider");
  if (!picker) return;
  let language = "ja";
  function render(nextLanguage) {
    language = nextLanguage === "en" ? "en" : "ja";
    document.querySelector(".setup-controls").hidden = false;
    const profile = profiles[picker.value] ?? profiles.codex;
    const copy = profile[language];
    document.querySelector("[data-setup-name]").textContent = profile.name;
    const mode = document.querySelector("[data-setup-mode]");
    mode.textContent = copy.mode;
    mode.dataset.kind = profile.kind;
    document.querySelector("[data-setup-summary]").textContent =
      copy.description;
    document.querySelector("[data-setup-steps]").replaceChildren(
      ...copy.steps.map((step) => {
        const li = document.createElement("li");
        li.textContent = step;
        return li;
      }),
    );
    document.querySelector("[data-setup-docs]").href = profile.docs;
    document.querySelector("[data-windows-guide]").href =
      "https://github.com/lingmulongtai/Win-CodexBar-Mobile-Connect/blob/main/" +
      (language === "ja" ? "README.ja-JP.md" : "README.md");
  }
  picker.addEventListener("change", () => render(language));
  window.CodexBarSetup = { render };
})();
