# Website browser checks

Development-only checks for the dependency-free `docs/` site. No npm package is shipped with the website. Tests start an ephemeral local server and use synthetic screenshots; they do not log in to any service.

Requires Node 22 or later. From this directory:

```sh
npm ci
npx playwright install --with-deps chromium firefox webkit
npm test
SITE_BROWSER=firefox npm test
SITE_BROWSER=webkit npm test
```

In PowerShell, set `$env:SITE_BROWSER='firefox'` (or `webkit`) before `npm test`; remove that environment variable for the Chromium default.

Eight journeys run per browser: Japanese/English responsive layouts and axe accessibility checks; provider-specific setup; keyboard-controlled tour, facets and racing image requests; failed-image recovery; full-size dialog focus/scroll restoration; copy success and permission-denied fallback; optional storage and mobile navigation; and JavaScript-disabled content/downloads. Screenshots go to ignored local `output/playwright/`, or a seven-day CI artifact. The Pages deployment waits for every browser check and static asset validation.

This checks browser engines, not physical Android devices or the Safari app. Lighthouse measurements are a separate manual performance check, not a claim about every network or device.
