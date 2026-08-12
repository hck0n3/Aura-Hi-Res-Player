# Privacy Policy for Aura Hi-Res Player App

**Last updated:** 16 July 2026

## Introduction

Aura Hi-Res Player ("we," "our," or "us") is committed to protecting your privacy. This Privacy Policy explains what information stays on your device, what leaves it, and who receives it when you use our mobile application (the "App").

**In short:** the App contains no analytics, tracking, advertising or usage-profiling SDK. We do not build a profile of you, and we have no account system for your music data. However, the App is a client for YouTube Music and other online services, so requests to those services necessarily leave your device — and one of our own servers receives a license key and a device identifier to run the subscription. This policy describes all of that honestly, including the parts that are not private by nature.

## Information We Collect

### 1. Data That Stays on Your Device

The App has no user account with us and does not sync your library to us. The following is recorded **locally on your device only** and is never uploaded to us:

- **Listening data**: songs played, paused and skipped, and your listening history — used for the in-app statistics screens and the on-device recommendation engine
- **Playlists**: playlists you create and modify
- **Search history**, and **app settings and preferences**
- **Local music library**: access to your device's music files for playback
- **Session credentials**: cookies and tokens for third-party services you choose to log in to

You can erase all of it by clearing the App's data or uninstalling the App.

### 2. Data We (the Developer) Receive

We operate one server (a Cloudflare Worker). It receives:

- **License and demo checks** (always, while the subscription gate is active): your **license key** and a **device identifier**. The device identifier is your Android `ANDROID_ID`, or a randomly generated ID if `ANDROID_ID` is unavailable. It is required to enforce one device per subscription and to prevent the free demo period from being reset by clearing app data. It is not used for advertising or profiling.
- **AI playlist requests** (only if you use the AI playlist feature): the text prompt you type and, when modifying an existing playlist, the titles and artists of its tracks.
- **Song recognition** (only if you use the recognition feature): an audio fingerprint derived from the microphone, captured only while that feature is actively running.

We do not receive your library, your listening history, your playlists or your search history.

### 3. Analytics and Crash Reporting: None in the Published App

**The published Aura Hi-Res Player app contains no active analytics and sends us no crash reports.**

For transparency about how this works, since the code is public and references Firebase:

- The App is published in a build variant ("gms") that links the Firebase libraries, but Firebase is **never initialised**, because the Firebase Gradle plugins are only applied when a `google-services.json` configuration file is present, and no such file exists in this repository or in our release pipeline. Without it, Firebase has no project to report to, and both Firebase Analytics and Firebase Crashlytics collect and transmit nothing.
- Firebase Analytics is additionally **never called anywhere in the App's code**.
- The alternative "foss" build variant contains no crash-reporting backend at all — its crash reporter is an empty no-op.
- **Caveat, stated precisely:** anyone who builds the App from source themselves *can* supply their own `google-services.json` to a "gms" build and thereby enable Firebase Crashlytics (and Firebase Analytics auto-collection) in **their own** build. That is a developer option; it is not how the app we distribute is built. If this ever changes for the app we publish, this policy will be updated first.

Crashes are handled **on your device**: the App shows you a crash screen and writes a crash log locally, which you may choose to share with us manually.

## How We Use Your Information

### 1. App Functionality

- Provide music streaming and playback services
- Manage your playlists and music library
- Enable search and discovery features
- Verify your subscription or demo status

### 2. Personalization

- Recommend music based on your listening habits. **This runs on your device.** The recommendation engine reads your local listening data; it does not upload it. To enrich recommendations it does make limited third-party lookups — artist names to iTunes for genres, and the video IDs of songs you liked to YouTube for related tracks. Both are limited in volume and run only on Wi-Fi.
- Customize the app interface according to your preferences
- Remember your settings and preferences

### 3. Improvement

We improve the App from bug reports you send us voluntarily and from local crash logs you choose to share. We do not measure your usage remotely.

## Data Sharing and Disclosure

### 1. Requests That Always Leave Your Device

These are inherent to the App working at all:

- **YouTube / YouTube Music (Google)**: search, browse and playback requests, video IDs, playback tracking, and — if you log in — your account cookies. Your IP address is visible to Google. This is unavoidable in a YouTube Music client.
- **Google image CDNs** (`googleusercontent.com`, `ytimg.com`): cover art and thumbnails, which exposes your IP address to Google.
- **Our license server**: as described above.
- **Remote configuration**: the App downloads a small configuration file from our public GitHub repository to keep streaming working when YouTube changes its player. It is a plain download and contains no data about you (your IP is visible to GitHub).
- **Update check**: the App checks GitHub for new releases. This is **on by default and can be turned off** in settings.

### 2. Requests That Depend on Features You Use

- **Lyrics providers**: when lyrics are displayed, the song title, artist, album and duration are sent to lyrics providers in turn until one has a match. You can configure the provider order.
- **iTunes (Apple)**: artist, album and track names, for genre and discography lookups.
- **Apple Music and other metadata / cover-video providers**: track, artist and album names when those features fetch artwork, canvases or album information.
- **Wikipedia**: the artist's name, when artist information is shown.

### 3. Optional Integrations (Off Unless You Enable Them)

- **Last.fm scrobbling**: requires you to log in. Sends track, artist, album and timestamps, plus "loves".
- **Last.fm taste import**: off by default; requires being enabled *and* a username. Sends your username to retrieve your top artists and loved tracks.
- **ListenBrainz**: off by default. Sends artist, track, release and playback timings with your token.
- **Spotify import**: sends the playlist and library requests needed to import from your Spotify account.
- **SponsorBlock**: off by default. Sends video IDs.
- **Lyric translation**: the lyrics text is sent to Google Translate's public endpoint (no API key) to be translated, with a fallback to a third-party AI endpoint.
- **AI playlist generation**: your prompt (and track names when modifying a playlist) goes to our relay, falling back to a public third-party AI endpoint. If you supply your own API key for another AI provider, your prompt goes to that provider instead.
- **Song recognition**: microphone audio fingerprints, only while you run the feature.
- **Listen Together, AutoEQ, podcasts and similar optional features**: send only what that feature needs (for example, a room code and current track; a headphone model name; a podcast search term).
- **Logging in to YouTube, Spotify or Last.fm**: your credentials go directly to that provider.

Third parties receive these requests and handle them under **their own** privacy policies, and they can see your IP address.

### 4. No Sale of Personal Data

We do not sell, trade, or rent your personal information to third parties for marketing purposes. The App contains no advertising and builds no advertising profile.

### 5. Legal Requirements

We may disclose your information if required by law or to protect our rights and safety.

## Data Storage and Security

### 1. Local Storage

- Your music playlists, listening history and preferences are stored locally on your device
- We use secure local storage to protect your data

### 2. Cloud Storage

- We do not store your library or preferences on our servers, and the App offers no cloud sync of them
- If you log in to a third-party service (YouTube, Spotify), actions you take may be reflected in **that** account, as per their privacy policies

### 3. Data Retention

- We keep no analytics data, because we collect none
- Our license server retains your license key and device identifier for as long as needed to operate your subscription and to keep a record that a demo was started on that device
- Local app data is retained until you uninstall the app or clear app data

## Your Rights and Choices

### 1. Data Access

- You can view your data through the app settings
- You can export your playlists and preferences

### 2. Data Deletion

- Uninstalling the app will remove all local data
- You can clear app data through device settings
- Contact us to request deletion of the license/demo record associated with your device identifier

### 3. Privacy Controls

- There is no analytics to disable, because the App collects none
- You can control which services you connect to, and leave the optional integrations above turned off
- You can turn off automatic update checks in settings
- You can manage permissions through your device settings. The App requests permissions only for specific features: microphone (song recognition, only while in use), storage/media files (local music and downloads), Bluetooth state (playback behaviour with audio devices) and notifications (playback controls)

## Third-Party Services

### 1. YouTube Music

- **Privacy Policy**: [YouTube Privacy Policy](https://policies.google.com/privacy)
- **Data Collection**: YouTube may collect data about your music preferences and usage

### 2. Spotify

- **Privacy Policy**: [Spotify Privacy Policy](https://www.spotify.com/legal/privacy-policy/)
- **Data Collection**: Spotify may collect data about your listening habits

### 3. Other Providers

Lyrics, metadata, artwork, translation, AI, scrobbling and recognition providers each operate under their own privacy policies. The App contacts them only for the features described above.

## Children's Privacy

Our App is not intended for children under 13 years of age. We do not knowingly collect personal information from children under 13.

## International Users

Our license server runs on Cloudflare's global network, so your license request may be processed at a location outside your country. The third-party services listed above process data in their own locations under their own policies.

## Changes to This Privacy Policy

We may update this Privacy Policy from time to time. We will notify you of any changes by:
- Posting the new Privacy Policy in the App
- Updating the "Last Updated" date
- Sending you a notification if significant changes are made

## Contact Us

If you have any questions about this Privacy Policy or our data practices, or to send error reports and suggestions, please contact us:

- **Email**: aurahires@gmail.com
- **GitHub**: [https://github.com/hck0n3/Aura-Hi-Res-Player](https://github.com/hck0n3/Aura-Hi-Res-Player)
- **Issues**: [https://github.com/hck0n3/Aura-Hi-Res-Player/issues](https://github.com/hck0n3/Aura-Hi-Res-Player/issues)
- **Discussions**: [https://github.com/hck0n3/Aura-Hi-Res-Player/discussions](https://github.com/hck0n3/Aura-Hi-Res-Player/discussions)

When you use **Report & suggest** in the App, your device opens your own email app with a message addressed to aurahires@gmail.com. We receive only what you choose to send (and optional diagnostic logs you attach). Nothing is uploaded silently.

## Data Protection Compliance

This Privacy Policy complies with:
- **GDPR** (General Data Protection Regulation) for EU users
- **CCPA** (California Consumer Privacy Act) for California users
- **PIPEDA** (Personal Information Protection and Electronic Documents Act) for Canadian users

## Summary

- The App has **no analytics, tracking or advertising SDK**, and sends us no usage data or crash reports
- Your library, history, playlists and searches stay **on your device**
- Our only server receives your **license key and a device identifier** to run the subscription, plus AI prompts or recognition fingerprints **only if you use those features**
- Because the App streams from YouTube Music, **Google receives your requests and IP address** — that is inherent to the App, not analytics
- Optional integrations (Last.fm, ListenBrainz, Spotify, SponsorBlock, AI, translation) are **off until you enable them**
- We do not sell your personal data
- We are committed to protecting your privacy and being transparent about our practices

---

**By using Aura Hi-Res Player, you agree to the collection and use of information in accordance with this Privacy Policy.**
