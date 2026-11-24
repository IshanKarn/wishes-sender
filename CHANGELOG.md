# Changelog

All notable changes to WishesSender. Format: [Keep a Changelog](https://keepachangelog.com/), versions: [SemVer](https://semver.org/).

## [1.1.0] – 2026-11-24

Database normalisation and data-integrity fixes – see [docs/CHANGE_REQUEST_CR-001.md](docs/CHANGE_REQUEST_CR-001.md).
**No breaking API changes.** A v1.0.0 database is upgraded automatically on the first start
(take a backup first – see README "Upgrading from v1.0.0").

### Added
- `event_types` lookup table (BIRTHDAY, ANNIVERSARY, FESTIVAL, CUSTOM) referenced by `events`, `gifts` and `wish_templates`.
- CHECK constraints for every categorical column (`relation`, `gender`, `religion`, `role`, `tone`, `language`,
  `source`, `status`, gift `category`, festival `religion`) and for numbers/flags
  (`gift_min >= 0`, `gift_max >= gift_min`, `yearly`, `days_before`, `price > 0`, `remind_days_before`, `ai_enabled`, `share_details`, `is_read`).
- `UNIQUE (user_id, name)` on `connections` – the same person cannot be added twice.
- `events.birthday_of` virtual column + `UNIQUE` key – at most one birthday per connection.
- `SchemaMigrator` – upgrades a v1.0.0 database once, safely (idempotent, logs `[migration] ...`).
- `Rules` helper – one list of allowed values, friendly `400` messages, case-insensitive input (`friend` → `Friend`).
- `ApiErrorHandler` – database constraint errors return `400`/`409` with a message instead of `500`.
- `GET /api/lookups` – all allowed values and event types for dropdowns.
- `CHANGELOG.md`, `docs/CHANGE_REQUEST_CR-001.md`.

### Changed
- `users.password_hash` widened from `VARCHAR(100)` to `VARCHAR(255)` (room for argon2).
- `gifts.event_type` is nullable: `NULL` = suits any event. The API still shows and accepts `"ANY"`.
- The date of birth is stored only as the BIRTHDAY event. `GET /api/connections` still returns `dob`.
- Editing a person's date of birth now moves their birthday event (before, the two could disagree).
  Clearing it removes the birthday event; deleting the birthday event clears the date of birth.
- Adding a second BIRTHDAY event returns `409` with a clear message; birthdays are always yearly.
- Saving a wish checks that its event belongs to the same person (`404` otherwise).
- Settings: `remind_days_before` must be 0–30, `ai_enabled` / `share_details` must be true/false.
- Non-numeric numbers (e.g. `gift_min: "abc"`) return `400` instead of `500`.
- Admin: gift *Category* is a dropdown; gift price must be > 0 (`min="1"`).
- Frontend: errors when adding an event/festival or admin gift/festival are shown as a toast; the profile refreshes the
  birthday after adding/deleting a birthday event; budget inputs have `min="0"`.
- Lists ordered by `created_at` use `id` as a tie-breaker (stable order).

### Removed
- `wishes.user_id` and `notifications.user_id` – the owner comes from `connections` (API responses still include `user_id`).
- `connections.dob` – replaced by the BIRTHDAY event.

### Migration notes (existing v1.0.0 data)
- Each `connections.dob` is copied into its birthday event (profile value wins when they differ).
- Extra BIRTHDAY events of the same person become CUSTOM events (nothing is deleted).
- Duplicate connection names of one user are renamed `Name (2)`, `Name (3)`, ...
- Gift `ANY` → `NULL`; unknown gift categories → `Other`; `gift_max < gift_min` are swapped;
  unknown relation → `Friend`; unknown gender/religion → empty.
- v1.0.0 cannot run on an upgraded database – restore your backup to roll back.

## [1.0.0] – 2026-11-24

First MVP: registration/login, connections, events, festival list, reminder scheduler, Wish Studio
(Gemini + template fallback), gift suggestions, wish history, dashboard, insights, settings, admin panel.
