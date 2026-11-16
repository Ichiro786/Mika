---
name: maps.google.com
display: Google Maps
description: Place details and driving, transit, walking routes
domains: ["maps.google.com"]
aliases: ["Google Maps", "谷歌地图"]
apps: []
open: maps.google.com
mode: desktop
---
## What it is
Google's web map, for place information and routes; usually no sign-in needed. The actual address is under `google.com/maps`, sharing the google.com domain with Google Flights and others.

## Pages and URLs
- Search: `https://www.google.com/maps/search/<encoded query>`.
- Directions: `https://www.google.com/maps/dir/<encoded origin>/<encoded destination>`.

## Features
- **Places**: name, address, rating and review count, open status, full opening hours, phone. Opening hours and phone only show after expanding the details.
- **Directions**: open the directions panel, enter origin and destination, and switch travel mode (driving, transit, walking, etc.). Each mode lists several options, each with duration, distance, the roads or transit lines used, and notes.

## Known behavior
- A place name in the URL does not mean the page has selected that place; there are many branches with the same name, so check the name, address and city on the page.
- Pressing Enter directly in the origin or destination box may pick the current location, a recently searched place, or another place of the same name; clicking from the candidate list is more reliable.
- After switching travel mode or opening another option card, the page content refreshes as a whole, and data read earlier belongs to the previous state.
