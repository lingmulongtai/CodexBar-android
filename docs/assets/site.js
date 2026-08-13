document.documentElement.classList.add("js");

const header = document.querySelector("[data-header]");
const updateHeader = () => {
  header?.classList.toggle("is-scrolled", window.scrollY > 18);
};

updateHeader();
window.addEventListener("scroll", updateHeader, { passive: true });

const revealItems = [...document.querySelectorAll(".reveal")];

if ("IntersectionObserver" in window) {
  const revealObserver = new IntersectionObserver(
    (entries, observer) => {
      for (const entry of entries) {
        if (!entry.isIntersecting) continue;
        entry.target.classList.add("is-visible");
        observer.unobserve(entry.target);
      }
    },
    { rootMargin: "0px 0px -7%", threshold: 0.08 },
  );

  revealItems.forEach((item) => revealObserver.observe(item));
} else {
  revealItems.forEach((item) => item.classList.add("is-visible"));
}

const themeSection = document.querySelector("[data-theme-section]");
const themeImage = document.querySelector("[data-theme-image]");
const themeName = document.querySelector("[data-theme-name]");
const themePhone = themeImage?.closest(".theme-phone");
const themeButtons = [...document.querySelectorAll("[data-theme-button]")];

const selectTheme = (button) => {
  if (!themeSection || !themeImage || !themeName || !themePhone || !button) return;

  const theme = button.dataset.themeButton;
  const nextSource = button.dataset.image;
  const nextAlt = button.dataset.alt;
  const nextName = button.querySelector("strong")?.textContent?.trim();

  if (!theme || !nextSource || !nextAlt || !nextName) return;

  themeButtons.forEach((candidate) => {
    const isSelected = candidate === button;
    candidate.setAttribute("aria-selected", String(isSelected));
    candidate.tabIndex = isSelected ? 0 : -1;
  });

  themeSection.dataset.themeSection = theme;
  themeName.textContent = nextName;

  if (themeImage.getAttribute("src") === nextSource) return;

  themePhone.classList.add("is-changing");
  const preload = new Image();
  preload.src = nextSource;

  const finishSwap = () => {
    themeImage.src = nextSource;
    themeImage.alt = nextAlt;
    window.setTimeout(() => themePhone.classList.remove("is-changing"), 70);
  };

  if (preload.complete) {
    finishSwap();
  } else {
    preload.addEventListener("load", finishSwap, { once: true });
    preload.addEventListener("error", () => themePhone.classList.remove("is-changing"), {
      once: true,
    });
  }
};

themeButtons.forEach((button, index) => {
  button.tabIndex = button.getAttribute("aria-selected") === "true" ? 0 : -1;
  button.addEventListener("click", () => selectTheme(button));
  button.addEventListener("keydown", (event) => {
    if (!["ArrowDown", "ArrowRight", "ArrowUp", "ArrowLeft", "Home", "End"].includes(event.key)) {
      return;
    }

    event.preventDefault();
    let nextIndex = index;

    if (event.key === "ArrowDown" || event.key === "ArrowRight") {
      nextIndex = (index + 1) % themeButtons.length;
    } else if (event.key === "ArrowUp" || event.key === "ArrowLeft") {
      nextIndex = (index - 1 + themeButtons.length) % themeButtons.length;
    } else if (event.key === "Home") {
      nextIndex = 0;
    } else if (event.key === "End") {
      nextIndex = themeButtons.length - 1;
    }

    const nextButton = themeButtons[nextIndex];
    nextButton.focus();
    selectTheme(nextButton);
  });
});

const currentYear = String(new Date().getFullYear());
document.querySelectorAll("[data-current-year]").forEach((element) => {
  element.textContent = currentYear;
});
