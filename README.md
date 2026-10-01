# HeliBoard - MegaPaste

> A specialized fork of [HeliBoard](https://github.com/HeliBorg/HeliBoard) engineered for **MegaPaste (Stream Paste)** — reliably paste massive text files and multi-megabyte payloads into any Android app without clipboard freezes, IPC limits, or system crashes.

HeliBoard-MegaPaste is 100% offline, privacy-conscious, and requires **no internet permission**.

[<img src="https://user-images.githubusercontent.com/663460/26973090-f8fdc986-4d14-11e7-995a-e7c5e79ed925.png" alt="Get MegaPaste APK from GitHub" height="80">](https://github.com/umair2k1/HeliBoard-MegaPaste/releases)

---

## Table of Contents

- [Why HeliBoard-MegaPaste?](#why-heliboard-megapaste)
  - [The Problem](#the-problem)
  - [The Solution: MegaPaste (Stream Paste)](#the-solution-megapaste-stream-paste)
- [How to Use Stream Paste](#how-to-use-stream-paste)
- [Technical Highlights](#technical-highlights)
- [Downloads](#downloads)
- [Base HeliBoard Features](#base-heliboard-features)
- [Contributing](#contributing)
- [Upstream & License](#upstream--license)
- [Credits](#credits)

---

## Why HeliBoard-MegaPaste?

### The Problem
On Android, attempting to copy and paste large bodies of text (such as source code files, database dumps, terminal logs, markdown documents, books, or large JSON/CSV payloads) often fails completely:
- **Binder IPC limit (`TransactionTooLargeException`)**: Android clipboard transactions cannot exceed the ~1 MB system Binder buffer limit.
- **System Clipboard Truncation / Crashes**: Many Android versions and clipboard managers silently truncate or crash when dealing with large payloads.
- **Application Freezes (ANRs)**: Target apps usually choke or trigger ANR dialogs when attempting to process and render hundreds of thousands of characters in a single input frame.

### The Solution: MegaPaste (Stream Paste)
**HeliBoard-MegaPaste** introduces **Stream Paste** — a share-to-stage and chunked streaming architecture:
1. **Share-to-Stage**: Instead of relying on the system clipboard, share text or text files directly to **"Stream Paste"** from any file manager, browser, or editor via Android's native share menu.
2. **Off-Memory Atomic Cache**: Payloads are staged directly to an atomic on-disk cache without blowing up application memory or hitting Binder IPC constraints (supports up to **256 MB** payloads with disk-headroom safety checks).
3. **Chunked Streaming Injection**: When you trigger Stream Paste, HeliBoard streams the text into the active field in paced, UTF-8-safe chunks (2,048 code points per chunk with surrogate-pair preservation and a 15ms interval) via the keyboard's input connection.
4. **Live In-Keyboard Progress & Cancel**: A dedicated status strip replaces the suggestion bar during streaming, displaying live progress (percentage and KB transferred) with an immediate **Cancel** button.
5. **Generational Auto-Cleanup**: Once pasted (or upon cancellation), the staged cache is safely discarded with generational safeguards preventing race conditions against newly staged shares.

---

## How to Use Stream Paste

1. **Stage Your Text**:
   - In any app (file manager, text editor, terminal, browser), select text or open a text file.
   - Tap **Share** and select **Stream Paste** (HeliBoard). A toast will confirm your text has been staged.
2. **Focus the Target Field**:
   - Open the app where you want to paste the text (Termux, code editor, messaging app, notes, web form, etc.) and tap the input field so HeliBoard appears.
3. **Trigger Stream Paste**:
   - Tap the **Stream Paste** icon on HeliBoard's top toolbar.
   - *Tip:* If the icon is not on your toolbar, you can enable it under **HeliBoard Settings → Preferences → Toolbar keys**.
4. **Monitor or Cancel**:
   - Watch the live progress percentage and KB counter on the toolbar.
   - Tap **Cancel** at any time to halt the stream immediately.

---

## Technical Highlights

- **Payload Capacity**: Up to 256 MB per staged payload, protected by a 64 MB minimum storage headroom check.
- **Surrogate-Aware Chunking**: Chunks are split on Unicode code point boundaries (never splitting high/low UTF-16 surrogates), preventing corrupted characters or broken emojis.
- **Paced Input Delivery**: 15ms delay between chunks ensures the recipient application can consume and render text without freezing the main thread.
- **Atomic File Management**: Managed using Android's `AtomicFile` to prevent partially written or corrupted staged shares.
- **Generational Tracking**: Prevents an earlier streaming paste from deleting a newer staged payload if another share was received.
- **100% Offline & Private**: Zero internet permissions (`android.permission.INTERNET` is not declared or requested). All staging and streaming stays strictly local on your device.

---

## Downloads

Pre-built debug APKs with MegaPaste enabled are built automatically on GitHub Actions and available on the [Releases](https://github.com/umair2k1/HeliBoard-MegaPaste/releases) page.

---

## Base HeliBoard Features

HeliBoard-MegaPaste retains all the features of upstream HeliBoard:
<ul>
  <li>Add dictionaries for suggestions and spell check (build your own or download community dictionaries)</li>
  <li>Customize keyboard themes (style, colors, day/night mode, Android 12+ dynamic colors, custom background images)</li>
  <li>Emoji search (inline and separate)</li>
  <li>Fully customizable keyboard layouts (main layouts, symbols, number pad, functional keys)</li>
  <li>Multilingual typing</li>
  <li>Glide / Gesture typing (compatible with optional swypelibs)</li>
  <li>Local clipboard history</li>
  <li>One-handed and split keyboard modes</li>
  <li>Backup and restore of settings and learned words</li>
</ul>

For upstream FAQ, hidden features, and documentation, visit the [upstream HeliBoard wiki](https://github.com/HeliBorg/HeliBoard/wiki).

---

## Contributing

- **MegaPaste Issues & PRs**: For bugs, improvements, or feature requests relating to **MegaPaste / Stream Paste**, please open an issue or pull request in [this repository](https://github.com/umair2k1/HeliBoard-MegaPaste/issues).
- **Core HeliBoard Issues**: For bugs, dictionaries, or feature requests regarding general HeliBoard functionality, please visit the [upstream repository](https://github.com/HeliBorg/HeliBoard).

---

## Upstream & License

HeliBoard-MegaPaste is based on [HeliBoard](https://github.com/HeliBorg/HeliBoard) by [Helium314](https://github.com/Helium314) (which is a fork of OpenBoard / AOSP Keyboard).

HeliBoard is licensed under the **GNU General Public License v3.0** (GPL-3.0). See [LICENSE](LICENSE) for details.
- Parts based on AOSP Keyboard are licensed under [Apache 2.0](LICENSE-Apache-2.0).
- App icons are licensed under [Creative Commons BY-SA 4.0](LICENSE-CC-BY-SA-4.0).

---

## Credits

- MegaPaste streaming paste functionality implemented by [@umair2k1](https://github.com/umair2k1)
- [HeliBoard](https://github.com/HeliBorg/HeliBoard) by [Helium314](https://github.com/Helium314) and [contributors](https://github.com/HeliBorg/HeliBoard/graphs/contributors)
- [OpenBoard](https://github.com/openboard-team/openboard)
- [AOSP Keyboard](https://android.googlesource.com/platform/packages/inputmethods/LatinIME/)
- [LineageOS](https://review.lineageos.org/admin/repos/LineageOS/android_packages_inputmethods_LatinIME)
- [Simple Keyboard](https://github.com/rkkr/simple-keyboard)
- [Indic Keyboard](https://gitlab.com/indicproject/indic-keyboard)
- [FlorisBoard](https://github.com/florisboard/florisboard/)
- Icon by [Fabian OvrWrt](https://github.com/FabianOvrWrt) with contributions from [The Eclectic Dyslexic](https://github.com/the-eclectic-dyslexic)
