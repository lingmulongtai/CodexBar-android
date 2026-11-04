(() => {
  function open(dialog, trigger) {
    if (!dialog || typeof dialog.showModal !== "function") return;
    dialog.showModal();
    document.documentElement.classList.add("modal-open");
    dialog.addEventListener(
      "close",
      () => {
        document.documentElement.classList.remove("modal-open");
        trigger.focus({ preventScroll: true });
      },
      { once: true },
    );
  }
  const transfer = document.querySelector("#transfer-dialog");
  const transferButton = document.querySelector("[data-open-transfer]");
  const input = document.querySelector("#transfer-url");
  const status = document.querySelector("[data-copy-status]");
  if (transferButton && typeof transfer?.showModal === "function") {
    transferButton.hidden = false;
    transferButton.addEventListener("click", () => {
      status.textContent = "";
      open(transfer, transferButton);
    });
    document
      .querySelector("[data-copy-url]")
      .addEventListener("click", async () => {
        try {
          await navigator.clipboard.writeText(input.value);
          status.textContent =
            document.documentElement.lang === "ja"
              ? "リンクをコピーしました。"
              : "Link copied.";
        } catch {
          input.focus();
          input.select();
          status.textContent =
            document.documentElement.lang === "ja"
              ? "URLを選択しました。端末のコピー操作でコピーしてください。"
              : "URL selected. Use your device’s copy action to copy it.";
        }
      });
  }
  const zoom = document.querySelector("[data-preview-zoom]");
  const dialog = document.querySelector("#screenshot-dialog");
  if (zoom && typeof dialog?.showModal === "function") {
    zoom.hidden = false;
    zoom.addEventListener("click", () => {
      const preview = document.querySelector("[data-preview-image]");
      if (
        document.querySelector("#app-preview").getAttribute("aria-busy") ===
          "true" ||
        preview.style.visibility === "hidden"
      )
        return;
      const full = dialog.querySelector("[data-full-preview]");
      full.src = preview.src;
      full.alt = preview.alt;
      open(dialog, zoom);
    });
  }
})();
