# Changelog

## Unreleased

- Fixed returning from playback in the series season episode list so the screen no longer reloads the season from episode 1 and focus/highlight restores to the episode that was opened.
- Fixed reopening the same episode after backing out of playback so the app creates a fresh playback entry instead of reusing a released player session and getting stuck loading.
- Fixed reopening an episode from the series season list so it refreshes the episode resume position after playback and starts from the latest saved stop point.
