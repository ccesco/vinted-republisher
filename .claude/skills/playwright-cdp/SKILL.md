---
name: playwright-cdp
description: Playwright for Java driving a normal Chrome over CDP in the Vinted Republisher project (connection, locators, waits, page objects in the browser adapter, @Tag("browser") tests against local pages, safety on the real Vinted account). Use when writing or changing the browser adapter, a browser test or a page test, or when a browser test is flaky.
---

# Playwright over CDP
Adapted from Jeffallan/claude-skills (MIT), skill `playwright-expert`, rewritten for Playwright Java and a Chrome driven over CDP.

## Why CDP
Vinted blocks the Chrome that Playwright launches itself. The tool starts a **normal Chrome** (`chrome.path`, `chrome.debug-port`, bound to 127.0.0.1) and Playwright attaches with `connectOverCDP`. Never switch to `launch()`, headless mode or bundled Chromium to "simplify" a test or a fix.

## Safety
- No automated test ever touches vinted.fr. Browser tests run against **local HTML pages** reproducing the parts of Vinted the adapter reads (served by the test).
- Never inspect, click or navigate in the Chrome connected to the user's real account without asking first. Never delete, edit or republish a real listing without explicit approval.
- Never log or store cookies or session tokens outside the Chrome profile.

## Adapter structure
- Playwright lives only in `adapter/out/browser`. It implements domain ports (`VintedAccount`…) and returns domain objects; no Playwright type leaks out.
- One small page object per Vinted page the adapter uses (listing page, wardrobe, edit form), holding its locators; the adapter orchestrates them.
- Selectors stable first: role and accessible name (`getByRole`, `getByLabel`, `getByText`), then `data-testid` if Vinted has one; CSS classes (often generated) last, isolated in the page object with a comment saying why.
- Avoid `first()`/`nth()` without a reason written in the code.

## Waiting
- Rely on Playwright auto-waiting and web-first assertions (`assertThat(locator).isVisible()`), or `locator.waitFor(...)` with a state.
- Never `page.waitForTimeout(...)` or `Thread.sleep(...)`. Business delays (the 5-minute wait between republications) go through an injected `java.time.Clock`, not the browser.
- Set explicit timeouts from configuration when Vinted is slow, not magic numbers.

## Tests
- Domain and application tests use fakes (`FakeVintedAccount`), never a browser.
- Adapter and page tests that start Chrome are tagged `@Tag("browser")`: excluded from `mvn test`, run with `mvn test -Pbrowser` before handing back.
- Each test independent: its own page/context, no state shared between tests, no order dependency.
- Flaky test: find the cause (skill `systematic-debugging`), enable a Playwright trace (`context.tracing().start(...)` / `stop(...)` to a file under `target/`, opened with the Playwright CLI `show-trace`), replace the bad wait; never retry until green or mark it `@Disabled`.
