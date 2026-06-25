# Changelog

## Unreleased

- Added admin metadata rematching for movies and series, with in-place refresh after Jellyfin applies the new match.
- Added customizable Home categories including top rated, popular, recently released, unwatched, genre, and other library-specific rows.
- Added optional seasonal Home rows for Halloween in October and Christmas in December.
- Added customizable Seerr Discover categories in Settings, including enable/disable controls, ordering, and server-provided movie and TV genres.
- Added a dedicated Seerr movie and TV search tab to Discover.
- Updated Seerr Discover to hide titles already available in the Jellyfin library and apply category changes immediately.
- Fixed Seerr setup and login reliability, including preserving session cookies and autofilling the Seerr URL from the current Jellyfin server with port 5055.
- Fixed rapid nav drawer navigation causing focus to jump to the profile item instead of entering the selected page.
- Fixed leaving a section with left so focus returns directly to its nav drawer item instead of briefly flashing to the profile item at the top-left (most visible in Search and Favourites).
- Fixed app startup so the Home nav item receives initial focus instead of Search or the profile item.
- Improved Home startup responsiveness by showing row placeholders immediately and filling rows as they load.
- Fixed Library TV so the last opened channel continues in a top-right picture-in-picture view when returning to the guide, without the clock or long descriptions overlapping it.
- Fixed returning from playback in the series season episode list so the screen no longer reloads the season from episode 1 and focus/highlight restores to the episode that was opened.
- Fixed reopening the same episode after backing out of playback so the app creates a fresh playback entry instead of reusing a released player session and getting stuck loading.
- Fixed reopening an episode from the series season list so it refreshes the episode resume position after playback and starts from the latest saved stop point.
- Fixed app startup so it restores the last selected user from the cached session instead of opening the user selection screen when the Jellyfin refresh is unavailable.
- Fixed the Home nav drawer item so selecting it after Home was already preview-loaded no longer forces an unnecessary Home reload.
- Fixed nav drawer activation so pressing right or enter on a drawer item opens it instead of only closing the drawer.
- Fixed returning from the nav drawer to Home so focus restores to the row you left without the content scrolling/jumping, and the first directional press after returning navigates normally instead of losing focus.
