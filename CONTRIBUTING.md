# Contributing

Thanks for wanting to help. Until 1.0, the best way to contribute is by opening an issue.

## Pull requests are closed until 1.0

The library is still early (0.x), and its API and internals are changing quickly. To keep the design consistent while that settles, **pull requests aren't being accepted until 1.0**, and any that are opened will be closed.

Instead, open an issue describing what you'd like to see. Good ideas from issues go into the library, and you'll be credited in the changelog. This will open up after 1.0.

## Opening an issue

Issues are welcome for anything about the library:

| You want to…                                                                                             | Open                  |
|----------------------------------------------------------------------------------------------------------|-----------------------|
| Report something broken or behaving oddly                                                                | A **bug report**      |
| Suggest an improvement to something that exists                                                          | A **feature request** |
| Ask for something new (a new option, look or behavior)                                                   | A **feature request** |
| Ask for support for a list type, platform or library (for example staggered grids, Paging, a new target) | A **support request** |
| Point out something wrong or unclear in the docs                                                         | A **docs issue**      |

Before opening one, please search the [existing issues](https://github.com/Amaanprobably/compose-fast-scrollbar/issues) in case it's already there. If it is, add a 👍 or a comment with your use case instead. That helps decide what comes first.

For general questions ("how do I…"), check the [guide](GUIDE.md) first, then ask in [Discussions](https://github.com/Amaanprobably/compose-fast-scrollbar/discussions).

### A good bug report

- The platform and device (for example "Pixel 7, Android 15" or "iPhone simulator, iOS 18")
- Your Compose (or Compose Multiplatform) version and the library version
- The list type (`LazyColumn`, `LazyRow`, grid) and anything unusual about it: `reverseLayout`, headers, very uneven items, paging
- What you expected and what happened
- A short screen recording if it's visual. Scrollbar bugs are much easier to see than to describe.
- A minimal code snippet, if you can

### A good feature request

- What you're trying to do, and why: the app or screen it's for
- What you'd like the API or behavior to look like, if you have an idea
- How you're working around it today, if at all

The use case matters more than the exact API. "I have a chat with date headers and want the label to show the date" is more useful than "add a `headerLabel` parameter".

## Building from source

Useful for reproducing a bug, or trying things in your own fork.

### Project structure

| Module                                  | Purpose                                                |
|-----------------------------------------|--------------------------------------------------------|
| `/scrollbar`                            | The library. Source lives in `commonMain`.             |
| `/shared`                               | The demo app's shared UI, used by all three demo apps. |
| `/androidApp`, `/desktopApp`, `/iosApp` | Demo apps for trying the library on each platform.     |

> `iosX64` (the Intel-Mac simulator) isn't a target, because Compose Multiplatform doesn't publish artifacts for it.

### Setup

- JDK 21
- Android Studio (latest stable), or IntelliJ IDEA with the Android plugin
- For iOS: macOS with Xcode

### Running the demo apps

- Android: `./gradlew :androidApp:assembleDebug`
- Desktop: `./gradlew :desktopApp:run`
- iOS: open the [/iosApp](./iosApp) directory in Xcode and run it from there (requires macOS)

The demo app has a screen for every feature, plus stress tests under Playground (items inserted at the top, two lists on one screen, heavy items). If you can reproduce a bug there, mention which screen in your issue.

### Running tests

- Android host (unit tests): `./gradlew :scrollbar:testAndroidHostTest`
- Android device or emulator (includes UI and back-gesture tests): `./gradlew :scrollbar:connectedAndroidDeviceTest`
- Desktop: `./gradlew :scrollbar:desktopTest`
- iOS: `./gradlew :scrollbar:iosSimulatorArm64Test` (requires macOS)

## License

The library is licensed under the [Apache License, Version 2.0](LICENSE), so you're free to fork it and change it for your own use.

The demo app's artwork (launcher icon and characters) is licensed separately under [CC BY-NC-ND 4.0](ASSETS-LICENSE.md), and can't be modified or reused.