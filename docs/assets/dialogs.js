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
