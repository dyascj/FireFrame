# Memory after a restart, with and without the debloat

Fire TV Stick 4K Plus (2025, model AFTMA08C15), Fire OS 8.1.8.6, 1669 MB RAM, FireFrame on the home screen, idle, no key pressed.

Method (`restart-memory.csv`): `./fireframe.sh restart <ip>` (reboot, wait for adb, wait 2 minutes), then 5 samples 30 seconds apart of `MemAvailable` from `/proc/meminfo`, the process count (`ps -A`) and FireFrame's PSS.

| Phase | State | MemAvailable, 5 samples (MB) | Mean | Mean without the first sample | Processes |
| --- | --- | --- | --- | --- | --- |
| A | restart, nothing turned off | 591, 627, 629, 634, 627 | 622 | 629 | 307-309 |
| B | `safe` + `screensaver` + `telemetry` turned off (30 packages; Fire OS refused 9), restart | 545, 572, 577, 592, 590 | 575 | 583 | 303-307 |
| C | everything turned back on (`undo`), restart | 543, 596, 596, 604, 584 | 585 | 595 | 309-314 |

Also: the TV had 395 MB available before phase A's restart (it had been running for a long time) and 591-634 MB after it.

What this says (and does not say): a restart alone gave back about 200 MB. Turning off the 30 packages did **not** give a measurable gain in idle memory on this stick: B is not above A or C, and A and C, which are the same state, differ by about 35 MB between themselves. The debloat removed about 4 processes of 308. One run per phase, so small differences are noise. The README's before/after table (Fire TV Stick HD, Fire OS 7) is from the original project and was not repeated here. Prime Video, Alexa and the other optional groups were not part of this run.

## More runs (same method)

| Run | State | MemAvailable, 5 samples (MB) | Mean |
| --- | --- | --- | --- |
| B2 | 36 packages off: `safe`, `telemetry`, `screensaver`, `amazonapps`, `casting`, `livetv` (YouTube, Prime Video, Alexa left running); restart | 554, 565, 571, 569, 586 | 569 |
| B3 | B2 plus background restrictions on YouTube, Prime Video and the 13 Alexa packages (`cmd appops set ... RUN_ANY_IN_BACKGROUND ignore`, `RUN_IN_BACKGROUND ignore`, standby bucket `restricted`); restart | 553, 568, 572, 565, 564 | 564 |
| C3 | all back on, restart | 592, 612, 615, 619, 613 | 610 |

Data: `restart-memory.csv` (first run), `restart-memory-2.csv` (this table).

What this says:
- Over six runs the states with packages turned off (575, 569, 564 MB) are not above the states without (622, 585, 610 MB): no gain from the debloat in idle memory on this stick, and the spread between identical states is about 35 MB.
- The background restrictions did not keep the apps away: Prime Video (about 110 MB) still ran after the restart. The standby bucket stayed at 10 (active) although `restricted` was requested. Reported by others: running processes keep running, and Fire OS starts Prime Video and YouTube itself at boot (a "prewarm" service) and ignores such restrictions.
- Stopping the apps does work (test on a booted stick, nothing opened): after `am force-stop` of Netflix, Prime Video and YouTube, MemAvailable went from 581 MB to about 760 MB, about 703 MB after six minutes, and they stayed off. Alexa's `vizzini` was restarted by the system at once (it is a persistent process).
- Read from the logs after a restart: Netflix is started by its own boot receiver (`BootCompleteReceiver`) and grows from 94 to 146 MB PSS. A stopped state survives a restart: on the next boot Netflix did not start, and MemAvailable at +150 s was 684 MB (not a clean comparison: other apps were stopped too). The apps start normally when opened; **that they start on demand after a force-stop was not verified** (the test launch command did not open anything).
- Disabling single components such as the Netflix boot receiver is reported to be impossible without root ("Shell cannot change component state"); it was not tried here.
- The state before these runs was restored afterwards (nothing disabled, no restrictions).
