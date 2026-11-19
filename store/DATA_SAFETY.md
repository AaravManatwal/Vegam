# Play Console: Data safety and declarations (answers)

## Data safety form
- Does your app collect or share any of the required user data types? **No**
- Is all of the user data collected by your app encrypted in transit? **Not applicable (no data collected)**
- Do you provide a way for users to request that their data is deleted? **Not applicable (no data leaves the device)**

(Downloads go from the websites the user chooses directly to the device; the developer receives nothing.)

## Foreground service declaration (type: dataSync)
**Why the app needs it:** The user explicitly starts file downloads that can take minutes to hours. The app shows an ongoing notification with progress and a Pause action and must keep running while the screen is off so the transfer does not stop.

**User-visible:** yes (persistent notification with progress, speed, ETA and Pause).
**Demo video (30 s):** start a large download, press Home, pull down the notification shade, show progress and Pause.

Known limitation: on Android 15 the dataSync type has a time limit (about 6 hours per day). A future version may move to the user-initiated data transfer job API for very long downloads.

## Permissions used
INTERNET, ACCESS_NETWORK_STATE, POST_NOTIFICATIONS, FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC. No storage permission (app folder or the system folder picker).

## Content rating
No violence, no user-generated content, no ads: expect "Everyone".
