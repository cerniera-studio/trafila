# Trafila

Every job application on one road, from applied to offer, each with its next step and date in view, whichever way it came: direct, through an agency, LinkedIn or a job board. For the Galaxy Z Fold 8 Ultra, in the Cerniera family. *Trafila* is the drawn-out process, and the bronze die that shapes pasta.

## Now (0.1)

- **Today** first: what you owe (oldest first), then any live thread that has gone ten working days without a word, as one quiet line: chase or close. Never a badge.
- **The road:** live applications furthest along first, each with a dot for every stage lit up to where it stands (rosso at it). A thread gone quiet drifts to the verge and dims.
- **The file:** firm, role, stage, route, agency or consultant, contact, when you last heard, the next step and its day, notes. Every change is kept the moment it is made, so a turn or a fold loses nothing. Moving the stage on counts as hearing from them.
- **On the cover** the road alone, and a file rises as a sheet. **On the main screen** (any window 600 dp wide) the road is the left page and the chosen file the right.
- **Share a job link** to Trafila from LinkedIn, Indeed or an agency's site and it starts an application with the link in its notes.
- **Import and export** as CSV: header `firm, role, route, via, contact, stage, last_contact, next, next_on, notes`, dates as yyyy-mm-dd. Applications already on the road (same firm and role) are skipped.

Everything stays on the phone (one SQLite table). Nothing is sent anywhere. This repository is public, so no applications are kept in it.

## Next

The road drawn as a road (cars that pull forward and close up), Concentrato on a fast scroll, interviews matched to the calendar, a chase becoming a Nodo knot in one tap, and an interview with a place handed to Partenza. See `claude/trafila-design.md` in the Cerniera project.

## Build

GitHub Actions tests the rules (`gradle testDebugUnitTest`) and builds a signed APK on every push. The first build of `main` makes the app's signing key once, encrypted with the org secret, and commits it. Install from **Releases**.
