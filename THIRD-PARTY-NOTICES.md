# Third-Party Notices

This file records third-party components distributed in this repository that are **not** covered by
the repository's GPL-3.0 `LICENSE` file, together with the attribution their licences require.

> **Read this before assuming the GPL-3.0 badge covers everything in this tree.**
> The `LICENSE` file at the repository root applies to Aura's own source code. It does **not** apply to
> the third-party binaries listed below, and those binaries are **not** redistributable under GPL-3.0
> terms. Anyone forking, vendoring or redistributing this repository is responsible for obtaining their
> own licence for them.

---

## Superpowered Audio SDK — proprietary, NOT GPL-3.0

**Files in this repository:**

| Path | Contents |
| --- | --- |
| `app/src/main/cpp/superpowered/lib/<abi>/libSuperpoweredAndroid.a` | Proprietary static libraries (arm64-v8a, armeabi-v7a, x86, x86_64) |
| `app/src/main/cpp/superpowered/include/*.h` | Proprietary SDK headers |

Aura's own JNI glue (`app/src/main/cpp/superpowered/SuperpoweredBridge.cpp`) is Aura code and **is**
covered by the repository `LICENSE`. Everything else in `app/src/main/cpp/superpowered/` is
Superpowered Inc.'s property, licensed — not assigned — to this project.

**Governing agreement:** SUPERPOWERED SDKS MASTER LICENSE AGREEMENT, effective 16 October 2019.
A verbatim copy is included in this repository as required by Section 2.1(a)(ii):

- [`docs/licenses/SUPERPOWERED_SDKS_MASTER_LICENSE_AGREEMENT_2019-10-16.pdf`](docs/licenses/SUPERPOWERED_SDKS_MASTER_LICENSE_AGREEMENT_2019-10-16.pdf) — the authoritative document
- [`docs/licenses/SUPERPOWERED_SDKS_MASTER_LICENSE_AGREEMENT_2019-10-16.txt`](docs/licenses/SUPERPOWERED_SDKS_MASTER_LICENSE_AGREEMENT_2019-10-16.txt) — plain-text extraction, for diffing and searching only

The current version of the agreement always lives at <https://superpowered.com/licensing> and, per the
agreement's own Preamble, a newer effective date there supersedes the copy included here.

**Required attribution** (Section 5.2(b)) — also shown in the app under Ajustes ▸ Acerca de ▸
Información legal:

```
Aura Hi-Res Player uses Superpowered SDKs. Superpowered.com
Copyright 2013 – 2026, Superpowered, Inc. All rights reserved.
```

**Why the static libraries are in a public repository at all:** Section 2.1 of the agreement expressly
permits it — *"The Superpowered SDKs may be embedded into open-source, source-code and/or source-code
repo, provided such use in compliance with the terms of this Agreement"* — subject to the two
conditions in 2.1(a): Superpowered must be mentioned in the README, and a copy of the agreement must be
included. Both are satisfied by this file, the README section, and `docs/licenses/`.

**What that permission does *not* grant.** Section 1.1(b) still forbids furnishing the SDK to third
parties, and Section 1.1(e) forbids combining or distributing the SDK with code under a licence that
would place the SDK itself under terms other than the agreement's. A licence for the Superpowered SDK
is per-Application (Section "What if I have more than one application?") and is not transferable
(Section 1.1(a)), so cloning this repository does **not** convey any right to use these binaries.
Obtain your own licence from <mailto:licensing@superpowered.com>.

---

## Engine availability is verifiable, not assumed

Superpowered validates a licence key at runtime, and the agreement states service may be interrupted
"with or without notice" if a licence is out of compliance (Introduction; Sections 3.1(a)(iii),
8.2). Because `Superpowered::Initialize()` returns `void` and the SDK exposes no licence-status API, a
disabled key would otherwise be **silent**: the app would keep running with an inert equalizer.

Aura therefore runs a one-shot empirical DSP probe immediately after initialization
(`probeSuperpoweredDsp` in `SuperpoweredBridge.cpp`) and records the verdict in the persisted
application log as `SUPERPOWERED engine=HEALTHY|DEGRADED|UNAVAILABLE`. The licence key is never logged.
