# College Football Scores — Android App 2

** Name: ** Can Cayir  
** UVA Computing ID: ** mtr2zb

## Overview

College Football Scores is an Android application built using Kotlin and Jetpack Compose. It fetches NCAA football scores from a public API and stores them locally so previously loaded games remain available without an internet connection.

### Features
- View FBS and FCS college football games.
- Navigate between weeks 1–14.
- Display upcoming, live, and final games.
- Show scores, game clock, quarter, overtime, and winning teams.
- Manually refresh scores.
- Browse previously loaded games offline.
- Display loading indicators, error messages, and empty states.

## How to Run

1. Open the `CollegeFootballScores` project in Android Studio.
2. Allow Gradle to sync. The project uses JDK 17 and Android SDK 35.
3. Select an Android emulator or physical device.
4. Run the `app` configuration.
5. Use the week selector and FBS/FCS toggle to browse games.

An internet connection is required to fetch new scores. Previously loaded games can be viewed offline.

## API

The app uses the NCAA API:

`https://ncaa-api.henrygd.me/scoreboard/football/{division}/{year}/{week}`

The API organizes games by season, division, and week rather than calendar date.

## Week Calculation

The initial week is calculated using September 3, 2026, as the beginning of Week 1. The week increases every seven days and is limited to weeks 1–14.

For example, October 15, 2026, corresponds to Week 7.

The selected week and division are maintained by the ViewModel during screen rotation.

## Architecture

**Retrofit + Gson:** Retrieves and parses NCAA scoreboard data from the API.

**Room Database:** Stores game information locally. Each game has a composite primary key consisting of division, year, week, and game ID. Existing records are replaced when updated scores are received, preventing duplicate entries.

**Repository:** Coordinates API requests and database operations. The database acts as the single source of truth for the UI.

**ViewModel:** Manages the selected week, division, and UI state using StateFlow. The screen uses Loading, Success, and Error states.

**Jetpack Compose:** Displays game cards, navigation controls, loading indicators, and offline/error messages.

## Offline Support

When scores are fetched successfully, they are saved in the Room database.

If the network becomes unavailable, the app displays cached scores for previously loaded weeks and divisions. An offline message indicates that saved data is being shown.

If no cached data exists and the API cannot be reached, the app displays an error message with a Retry button.

Weeks with no scheduled games display an empty-state message instead of an error.

## Testing

**Tested using:** Pixel 7 emulator — Android 15 (API 35).

The following functionality was manually tested:

- Application builds and launches successfully.
- Game scores load and appear on the screen.
- Final scores and winning teams display correctly.
- FBS/FCS selection changes the displayed games.
- Week navigation loads games for the selected week.
- Previously fetched games remain accessible without an internet connection.

**Additional test cases to verify:**

- Live-game clock and overtime display.
- Updated scores replacing existing Room records.
- Error and Retry when offline with no cached data.
- Empty weeks showing the correct message.
- Screen rotation preserving the selected week and division.

## Notes for the Grader

The app uses Room as its single source of truth. Network responses update the database, and the UI displays stored game records.

For offline testing, first load a week while connected to the internet, then disable connectivity and revisit that week.

Live score changes can only be observed while games are actively in progress.

## AI Usage Note

I utilized ChatGPT to establish the project's core structure, including Retrofit, Room, Repository, ViewModel, and the Jetpack Compose UI. I reviewed the generated code and added explanatory comments to keep the key components clear for future reference. I tested the application on a Pixel 7 emulator, verifying functionalities such as game loading, FBS/FCS transitions, week-by-week navigation, and access to cached games while offline.