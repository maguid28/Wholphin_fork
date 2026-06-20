# Changelog

## Unreleased

- Added customizable Home categories including top rated, popular, recently released, unwatched, genre, and other library-specific rows.
- Added optional seasonal Home rows for Halloween in October and Christmas in December.
- Added customizable Seerr Discover categories in Settings, including enable/disable controls, ordering, and server-provided movie and TV genres.
- Added a dedicated Seerr movie and TV search tab to Discover.
- Updated Seerr Discover to hide titles already available in the Jellyfin library and apply category changes immediately.
- Fixed rapid nav drawer navigation causing focus to jump to the profile item instead of entering the selected page.
- Fixed Library TV so the last opened channel continues in a top-right picture-in-picture view when returning to the guide, without the clock or long descriptions overlapping it.
- Fixed returning from playback in the series season episode list so the screen no longer reloads the season from episode 1 and focus/highlight restores to the episode that was opened.
- Fixed reopening the same episode after backing out of playback so the app creates a fresh playback entry instead of reusing a released player session and getting stuck loading.
- Fixed reopening an episode from the series season list so it refreshes the episode resume position after playback and starts from the latest saved stop point.
- Fixed the Home nav drawer item so selecting it after Home was already preview-loaded no longer forces an unnecessary Home reload.
- Fixed nav drawer activation so pressing right or enter on a drawer item opens it instead of only closing the drawer.
