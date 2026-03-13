# HaraZone Prototype -- Known Issues for Testers

Date: 2026-03-13
Version: Prototype v1.0

---

## CRITICAL -- Ship With Awareness

These are known limitations. They do NOT indicate bugs in the app itself.

1. POI HOURS MAY BE INACCURATE
   The AI may recommend places that are currently closed. Real-time opening hours
   are not yet verified against a live data source.
   - Workaround: Double-check hours on Google Maps or the venue's website before visiting.

2. COASTAL POI PINS MAY APPEAR IN WATER
   In coastal or waterfront areas, some POI map pins may be placed slightly offshore
   due to coordinate accuracy limitations.
   - Workaround: Use the place name to search for it on a separate map app if the
     pin location looks wrong.

---

## HIGH -- Noted

3. FOLLOW-UP CHAT RESPONSES MAY LACK POI CARDS
   After the first AI response, follow-up messages (e.g., "Tell me more" or
   "What's nearby?") may return text-only answers without the structured place
   cards. The initial response and intent pill responses will include cards.
   - Workaround: Start a new chat session to get full POI cards again.

---

## MEDIUM -- Cosmetic / UX

4. CHAT MAY SHOW STALE CONVERSATION ON REOPEN
   Reopening the chat overlay may show the previous conversation instead of
   refreshing with new context.
   - Workaround: Navigate to a different area and back, then reopen chat.

5. INTENT PILLS DISAPPEAR AFTER FIRST TAP
   The topic suggestion pills (e.g., "What's on tonight", "Hidden gems") disappear
   after you tap one. There is no way to switch topics without starting over.
   - Workaround: Close and reopen the chat to see the pills again.

6. CHAT DOES NOT ALWAYS END WITH A QUESTION
   The AI is instructed to end each response with a follow-up question, but it
   does not always comply. Conversation may feel like a dead end.
   - Workaround: Type your own follow-up question to keep the conversation going.

7. iOS MAP PINS SHOW DEFAULT RED MARKERS
   On iOS, map pins display as plain red markers without POI name labels or custom
   icons. Android pins show text labels correctly.
   - Workaround: Tap a pin to see the POI name in the detail card.

8. TAPPING POI CARD IN CHAT DOES NOTHING
   POI cards shown inside the AI chat overlay are display-only. Tapping them does
   not open a detail view or take any action.
   - Workaround: Find the same place on the map and tap its pin for the detail card.

---

## SETUP NOTES FOR TESTERS

- NO ONBOARDING FLOW: The app will ask for location permission on first launch.
  Grant it manually when the system dialog appears. There is no guided setup.

- ENGLISH ONLY: All AI content and UI text is in English. Localisation is not
  yet available.

- DEBUG BUILD: The app will appear as `HaraZone DEBUG` on your device. This is
  expected -- it is the debug variant, not the release build.

- INTERNET REQUIRED: AI-powered features (area discovery, chat, POI descriptions)
  require an active internet connection. Cached areas will work offline, but new
  searches will not.
