# Contributing to MovieShelf

Thanks for helping! Small fixes and big ideas are both welcome.

## Reporting a bug or idea

Open an issue with:

- the device (e.g. "Lumio Vision Google TV", "Windows 11") and app version,
- what you did, what happened, and what you expected,
- a photo or screenshot if it's about the layout.

## Making a change

1. Fork the repo and create a branch: `git checkout -b fix/sidebar-focus`.
2. Set up the build as in the README (JDK 17+, Android SDK, `local.properties`).
3. Make the change. Keep the code in the shared `commonMain` module whenever possible, so the TV and Windows versions stay the same.
4. Run the tests: `./gradlew :composeApp:desktopTest`. UI changes should also be checked on a TV (or an Android TV emulator) with the D-pad, and with mouse and keyboard on desktop.
5. Open a pull request describing what changed and how you tested it. Screenshots help.

## Ground rules

- Both inputs must work: a TV remote (D-pad + Back) and Windows mouse/keyboard.
- Keep the layout minimal, high-contrast and readable from across a room.
- Never commit API keys, tokens or signing files. `local.properties` is git-ignored for this reason.
- No analytics, ads or accounts. Runtime data comes only from the sources listed in the README.
- The UX rules in [CLAUDE.md](CLAUDE.md) apply to everyone, human or AI.

By contributing, you agree that your contribution is licensed under the MIT License.
