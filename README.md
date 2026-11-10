# WalkBreak

A Pomodoro-style Android timer: **25 min focus → 5 min walk**, repeating until you stop it.
Durations are adjustable on the main screen and remembered.

## How to run it
1. Install **Android Studio** (free) on your computer.
2. Unzip this folder, then in Android Studio choose **File → Open** and pick the `walkbreak` folder.
3. Wait for the Gradle sync to finish (first time downloads a few hundred MB). If it asks to create a Gradle wrapper or "use Gradle from gradle-wrapper.properties", accept.
4. On your phone: Settings → About phone → tap **Build number** 7 times → Developer options → turn on **USB debugging**.
5. Plug the phone in, select it at the top of Android Studio, press the green ▶ Run button.
6. On first launch, **allow notifications** — the reminders are notifications.

## How it works
- `TimerService.kt` — a foreground service (the persistent notification) runs the loop, so it keeps going with the screen off or the app closed. At each switch it posts a high-priority notification with alarm sound + vibration.
- `MainActivity.kt` — the big countdown screen, Start/Stop, Skip, and the minute settings. Background turns green during walk time.
- The ongoing notification has its own live countdown plus **Skip** and **Stop** buttons.

## Tips
- Some phones (Xiaomi, Samsung, Huawei…) kill background apps aggressively. If reminders stop arriving: Settings → Apps → WalkBreak → Battery → **Unrestricted**.
- To change the reminder sound/vibration: long-press a reminder notification → settings.

## Ideas for v2
- Step counter during walk time (Android's step sensor) to check you actually walked.
- Daily history / streaks.
- Pause button.
