# Repository working agreement

## Development style

- Preserve the existing architecture, naming, formatting, dependencies, and release workflow.
- Prefer the smallest change that completely solves the requested behavior.
- Keep unrelated user changes out of the current task and its commits.
- Do not commit credentials, local configuration, caches, generated files, or build outputs.

## Commit policy

- Create a commit whenever one meaningful, independently reviewable unit is complete and its focused checks pass.
- Prefer several small atomic commits over one catch-all commit when the work naturally separates into concerns such as domain logic, presentation mapping, UI, widget behavior, tests, documentation, build configuration, or release metadata.
- Keep each commit understandable, buildable where practical, and safe to revert independently.
- Stage only the files or hunks belonging to that concern, then review the staged diff before committing.
- Use concise imperative Conventional Commit messages unless an established repository convention requires otherwise.
- Do not create empty or no-op commits, artificial file splits, meaningless formatting churn, duplicate changes, or history noise solely to increase contribution counts.
- Do not amend, squash, rebase, force-push, or otherwise rewrite history without an explicit request.
- Push only after the relevant checks pass and publication is part of the requested task. Do not push directly to a protected/default branch unless explicitly requested.

## Verification

- Before each commit, run the narrowest relevant unit test, lint, compile, or build check for that logical unit.
- After the final implementation commit, run the broadest relevant test, lint, and build checks whose cost is reasonable.
- Review the final diff and commit sequence for mixed concerns, regressions, unnecessary changes, and missing tests.
- Report checks that could not be run and the remaining unverified scope honestly.

## Completion report

- Report each commit SHA, subject, and purpose.
- Report the checks executed and their outcomes.
- Report remaining risks, deferred work, and whether the branch was pushed or released.
