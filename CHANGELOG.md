# Changelog

## Unreleased

- Added admin metadata rematching for movies and series, with in-place refresh after Jellyfin applies the new match.
- Added customizable Home categories including top rated, popular, recently released, unwatched, genre, and other library-specific rows.
- Updated Home recently added and recently released rows to combine movies and TV shows in a single row, including migration of existing home settings.
- Added optional seasonal Home rows for Halloween in October and Christmas in December.
- Added customizable Seerr Discover categories in Settings, including enable/disable controls, ordering, and server-provided movie and TV genres.
- Added a dedicated Seerr movie and TV search tab to Discover.
- Updated Seerr Discover to hide titles already available in the Jellyfin library and apply category changes immediately.
- Fixed Seerr setup and login reliability, including preserving session cookies and autofilling the Seerr URL from the current Jellyfin server with port 5055.
- Fixed rapid nav drawer navigation causing focus to jump to the profile item instead of entering the selected page.
- Fixed leaving a section with left so focus returns directly to its nav drawer item instead of briefly flashing to the profile item at the top-left (most visible in Search and Favourites).
- Fixed app startup so the Home nav item receives initial focus instead of Search or the profile item.
- Improved Home first load by showing a loading screen until all rows are ready; refreshes still update in place.
- Fixed Home so background refreshes no longer show a loading spinner in the top-right corner.
- Improved Top Rated Movies and Top Rated TV home rows to rotate through a shuffled selection on each refresh.
- Improved Library TV guide load performance by reducing the scheduled window from 6 hours to 3 hours ahead.
- Updated Library TV to use app theme colors for the top banner, channel list, program grid, and channel settings.
- Fixed Library TV currently playing program labels so they stay white and readable on tinted cells.
- Improved Library TV channel focus with an animated theme accent ring and soft glow, without changing the cell background or layout.
- Fixed Library TV so the last opened channel continues in a top-right picture-in-picture view when returning to the guide, without the clock or long descriptions overlapping it.
- Fixed Library TV guide focus when entering the guide and returning from the nav drawer so the channel list no longer jumps and focus restores to the channel or program cell you left.
- Fixed returning from playback in the series season episode list so the screen no longer reloads the season from episode 1 and focus/highlight restores to the episode that was opened.
- Fixed reopening the same episode after backing out of playback so the app creates a fresh playback entry instead of reusing a released player session and getting stuck loading.
- Fixed reopening an episode from the series season list so it refreshes the episode resume position after playback and starts from the latest saved stop point.
- Fixed app startup so it restores the last selected user from the cached session instead of opening the user selection screen when the Jellyfin refresh is unavailable.
- Fixed the Home nav drawer item so selecting it after Home was already preview-loaded no longer forces an unnecessary Home reload.
- Fixed nav drawer activation so pressing right or enter on a drawer item opens it instead of only closing the drawer.
- Fixed returning from the nav drawer to Home so focus restores to the row you left without the content scrolling/jumping, and the first directional press after returning navigates normally instead of losing focus.
- Improved nav drawer navigation by keeping screen state across visits so Home, library, Discover, and grid pages avoid unnecessary reloads.
