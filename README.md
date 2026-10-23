# Krouen Tes "គ្រឿងទេស" — Khmer Recipe App

Android recipe app for Khmer food. Built by Group 4 (ITE Y4): Thomorn Lao (lead/dev),
Souchhean Hok, Soknai Oeurn, Bonna Soeung.

> Based on the open-source EasyFood app; restructured to follow the Krouen Tes pitch deck.

## Setup (first time)

1. Open the folder in Android Studio (Koala or newer, JDK 17).
2. **Firebase:** the app ID is now `com.kroeungtes.app`.
   In the Firebase console → project → *Add app* → Android → package `com.kroeungtes.app`,
   download the new `google-services.json` and put it in `app/`.
   (This file is git-ignored because it holds API keys.)
3. In Firebase → Authentication → enable **Email/Password**.
4. Sync Gradle and run.

## User flow (from the deck)

Loading screen → Sign-up / Log-in → Home → Recipe page → Wishlist
(already signed-in users skip Log-in.)

## Project structure

```
app/src/main/java/com/kroeungtes/app/
├── ui/
│   ├── splash/      SplashActivity — loading screen, decides Login vs Home
│   ├── auth/        LoginActivity, SignupActivity (Firebase Auth)
│   ├── activities/  MainActivity (bottom nav), MealActivity, MealDetailesActivity
│   ├── fragments/   Home, Wishlist (FavoriteMealsFragment), Category, Search
│   └── dialogs/     MealBottomDialog
├── viewmodel/       ViewModels (MVVM + LiveData)
├── adapters/        RecyclerView adapters
└── data/
    ├── remote/      Retrofit API (TheMealDB for now)
    ├── local/       Room database — saved/wishlist recipes
    └── model/       Data classes
```

Brand colors live in `res/values/colors.xml` (`brand_green #00BF63`, `brand_splash #6EF874`, …).
Khmer UI text is in `res/values-km/strings.xml`.

## Roadmap (from the deck)

- **Phase 1 — done:** rebrand, green theme, logo, launch flow, package restructure, build cleanup.
- **Phase 2:** Khmer recipe data in Firebase Firestore (Lok Lak, Trey Ang, Amok…), Khmer/English content.
- **Phase 3:** Create Recipe page, User Account/Profile, ingredient + dietary search (vegan, gluten-free),
  ratings, offline access.
- **Later:** Premium plan ($3.99/month or $39.99/year), food ads for free users,
  downloadable recipe books, grocery partners.

## Libraries

Navigation component · Retrofit · Room · MVVM/LiveData · Coroutines · View Binding · Glide · Firebase Auth
