# HeliBoard - StreamPaste

> A specialized fork of [HeliBoard](https://github.com/HeliBorg/HeliBoard) engineered for **Stream Paste** — reliably paste massive text files, copied snippets, and multi-megabyte payloads into any Android app without clipboard freezes, IPC limits, or system crashes.

HeliBoard-StreamPaste is 100% offline, privacy-conscious, and requires **no internet permission**.

[<img src="https://user-images.githubusercontent.com/663460/26973090-f8fdc986-4d14-11e7-995a-e7c5e79ed925.png" alt="Get StreamPaste APK from GitHub" height="80">](https://github.com/umair2k1/HeliBoard-StreamPaste/releases)

---

## Table of Contents

- [Why HeliBoard-StreamPaste?](#why-heliboard-streampaste)
  - [The Problem](#the-problem)
  - [The Solution: Stream Paste](#the-solution-stream-paste)
- [How to Use Stream Paste](#how-to-use-stream-paste)
- [Technical Highlights](#technical-highlights)
- [Downloads](#downloads)
- [Base HeliBoard Features](#base-heliboard-features)
- [Contributing](#contributing)
- [Upstream & License](#upstream--license)
- [Credits](#credits)

---

## Why HeliBoard-StreamPaste?

### The Problem
On Android, attempting to copy and paste large bodies of text (such as source code files, database dumps, terminal logs, markdown documents, books, or large JSON/CSV payloads) often fails completely:
- **Binder IPC limit (`TransactionTooLargeException`)**: Android clipboard transactions cannot exceed the ~1 MB system Binder buffer limit.
- **System Clipboard Truncation / Crashes**: Many Android versions and clipboard managers silently truncate or crash when dealing with large payloads.
- **Application Freezes (ANRs)**: Target apps usually choke or trigger ANR dialogs when attempting to process and render hundreds of thousands of characters in a single input frame.

### The Solution: Stream Paste
**HeliBoard-StreamPaste** provides a robust, multi-channel chunked streaming architecture:
1. **Multiple Input Channels**:
   - **Direct Clipboard Fallback**: Use standard **Copy** in any app. Tapping Stream Paste automatically streams from the system clipboard or copied file URIs if no share was staged.
   - **Text Selection Action (`ACTION_PROCESS_TEXT`)**: Select text in any app, tap **Stream Paste** directly in the floating text selection toolbar, and it stages instantly without needing the share dialog.
   - **Share-to-Stage**: Share text or files directly to **"Stream Paste"** from any file manager or editor via Android's native share menu.
   - **Automatic Large Paste Interception**: Normal pastes exceeding 5,000 characters automatically route into safe paced streaming to prevent editor ANRs.
2. **Off-Memory Atomic Cache**: Payloads are staged directly to an atomic on-disk cache without blowing up application memory or hitting Binder IPC constraints (supports up to **256 MB** payloads with disk-headroom safety checks).
3. **Chunked Streaming Injection**: When you trigger Stream Paste, HeliBoard streams the text into the active field in paced, UTF-8-safe chunks (2,048 code points per chunk with surrogate-pair preservation and a 15ms interval) via the keyboard's input connection.
4. **Live In-Keyboard Progress & Cancel**: A dedicated status strip replaces the suggestion bar during streaming, displaying live progress (percentage and KB transferred) with an immediate **Cancel** button.
5. **Generational Auto-Cleanup**: Once pasted (or upon cancellation), the staged cache is safely discarded with generational safeguards preventing race conditions against newly staged shares.

---

## How to Use Stream Paste

### Method 1: Standard Copy (Fastest)
1. Select text in any app and tap standard **Copy** (or copy a text file in a file manager).
2. Tap the input field in your target app (e.g. Termux, code editor, notes).
3. Tap the **Stream Paste** icon on HeliBoard's top toolbar. The text will stream into the field with live progress.

### Method 2: Text Selection Menu
1. Select text in any app (Chrome, PDF viewer, editor).
2. Tap **Stream Paste** in the floating text selection popup toolbar (or 3-dot overflow menu).
3. Open your target app and tap the **Stream Paste** icon on the toolbar.

### Method 3: Share Menu (Files & Heavy Payloads)
1. In any file manager or editor, select a text file or payload.
2. Tap **Share** and select **Stream Paste** (HeliBoard). A toast will confirm your text has been staged.
3. Open your target app and tap the **Stream Paste** icon on the toolbar.

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

Pre-built debug APKs with Stream Paste enabled are built automatically on GitHub Actions and available on the [Releases](https://github.com/umair2k1/HeliBoard-StreamPaste/releases) page.

---

## Base HeliBoard Features

HeliBoard-StreamPaste retains all the features of upstream HeliBoard:
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

- **Stream Paste Issues & PRs**: For bugs, improvements, or feature requests relating to **Stream Paste**, please open an issue or pull request in [this repository](https://github.com/umair2k1/HeliBoard-StreamPaste/issues).
- **Core HeliBoard Issues**: For bugs, dictionaries, or feature requests regarding general HeliBoard functionality, please visit the [upstream repository](https://github.com/HeliBorg/HeliBoard).

---

## Upstream & License

HeliBoard-StreamPaste is based on [HeliBoard](https://github.com/HeliBorg/HeliBoard) by [Helium314](https://github.com/Helium314) (which is a fork of OpenBoard / AOSP Keyboard).

HeliBoard is licensed under the **GNU General Public License v3.0** (GPL-3.0). See [LICENSE](LICENSE) for details.
- Parts based on AOSP Keyboard are licensed under [Apache 2.0](LICENSE-Apache-2.0).
- App icons are licensed under [Creative Commons BY-SA 4.0](LICENSE-CC-BY-SA-4.0).

---

## Credits

- Stream Paste streaming functionality implemented by [@umair2k1](https://github.com/umair2k1)
- [HeliBoard](https://github.com/HeliBorg/HeliBoard) by [Helium314](https://github.com/Helium314) and [contributors](https://github.com/HeliBorg/HeliBoard/graphs/contributors)
- [OpenBoard](https://github.com/openboard-team/openboard)
- [AOSP Keyboard](https://android.googlesource.com/platform/packages/inputmethods/LatinIME/)
- [LineageOS](https://review.lineageos.org/admin/repos/LineageOS/android_packages_inputmethods_LatinIME)
- [Simple Keyboard](https://github.com/rkkr/simple-keyboard)
- [Indic Keyboard](https://gitlab.com/indicproject/indic-keyboard)
- [FlorisBoard](https://github.com/florisboard/florisboard/)
- Icon by [Fabian OvrWrt](https://github.com/FabianOvrWrt) with contributions from [The Eclectic Dyslexic](https://github.com/the-eclectic-dyslexic)
