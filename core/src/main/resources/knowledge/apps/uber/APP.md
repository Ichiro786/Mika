---
name: uber
display: Uber
packages: ["com.ubercab"]
aliases: ["Uber", "优步", "uber"]
sites: []
open: Uber
description: Ride options, prices and trips
---
## What it is
Uber is a ride-hailing app, package `com.ubercab`.

## Screens
Bottom entries: Home, Services, Activity, Account. On Home, "Where to?" is where you enter the trip; entries such as Later are used to schedule a trip for a specific time.

## Features
- **Price estimate**: after entering pickup and destination you get a list of ride types with the type name, estimated price in the original currency, estimated arrival time, capacity and surge pricing. Prices usually require sign-in to show.
- **Trips**: Activity holds past and current trips; a trip that has been requested shows the driver, license plate and arrival time.

## Known behavior
- The default pickup is the device GPS, but it can be changed to an address in another city; the UI has a "Search another city" entry.
- The address input has a clear button X; after typing, pick the matching item from the suggestion list.
- Airport addresses prompt follow-ups such as "Which airline"; Skip lets you skip them.
- The input's content may change while typing, so read it again after typing.
