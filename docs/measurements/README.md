# Memory after a restart, with and without the debloat

Fire TV Stick 4K Max 2nd gen (AFTMA08C15), Fire OS 8.1.8.6, 1669 MB RAM, FireFrame on the home screen, idle, no key pressed.

Method (`restart-memory.csv`): `./fireframe.sh restart <ip>` (reboot, wait for adb, wait 2 minutes), then 5 samples 30 seconds apart of `MemAvailable` from `/proc/meminfo`, the process count (`ps -A`) and FireFrame's PSS.

| Phase | State | MemAvailable, 5 samples (MB) | Mean | Mean without the first sample | Processes |
| --- | --- | --- | --- | --- | --- |
| A | restart, nothing turned off | 591, 627, 629, 634, 627 | 622 | 629 | 307-309 |
| B | `safe` + `screensaver` + `telemetry` turned off (30 packages; Fire OS refused 9), restart | 545, 572, 577, 592, 590 | 575 | 583 | 303-307 |
| C | everything turned back on (`undo`), restart | 543, 596, 596, 604, 584 | 585 | 595 | 309-314 |

Also: the TV had 395 MB available before phase A's restart (it had been running for a long time) and 591-634 MB after it.

What this says (and does not say): a restart alone gave back about 200 MB. Turning off the 30 packages did **not** give a measurable gain in idle memory on this stick: B is not above A or C, and A and C, which are the same state, differ by about 35 MB between themselves. The debloat removed about 4 processes of 308. One run per phase, so small differences are noise. The README's before/after table (Fire TV Stick HD, Fire OS 7) is from the original project and was not repeated here. Prime Video, Alexa and the other optional groups were not part of this run.
