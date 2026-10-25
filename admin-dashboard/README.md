# Krouen Tes admin dashboard

A small web page for the Krouen Tes team. It lets admins:

- **Overview**: see users, new sign-ups this week, recipes waiting for review, and a 12-week audience growth chart.
- **Home page**: choose which recipes the Featured row on Home shows (and their order), and which category chip is selected first.
- **Categories**: add, edit, reorder, hide or delete the category chips in the app.
- **Recipes**: add and edit Khmer recipes (English + Khmer), approve or reject, hide or publish.
- **Notifications**: write news (English + Khmer, optional picture and recipe link) for the app's bell tab. People see a dot on the bell; phones don't get a pop-up alert yet.
- **Users**: see app users (phone numbers are hidden until you click Show), block or unblock them, and manage dashboard admins.

The app shows only what is published here: there are no built-in or world recipes any more. Changes reach phones within seconds, and the app keeps an offline copy after the first load.

## Try it without Firebase

Open `public/index.html?demo` through any local web server (for example `npx serve public`, then go to `/?demo`). Demo mode uses made-up data and saves nothing.

## One-time setup (Firebase project `krouen-tes`)

1. **Create the database.** In the Firebase console, open Firestore Database, then Create database. Choose production mode and a region close to Cambodia, such as `asia-southeast1` (Singapore). You can't change the region later.
2. **Turn on Google sign-in.** Open Authentication, then Sign-in method, and enable Google. Admins sign in with a Google account; app users keep using phone numbers.
3. **Add a web app.** Open Project settings, then Your apps, then Add app (Web). You don't need to copy the config: Firebase Hosting provides it.
4. **Install the tools and deploy.** On a computer with Node.js:
   ```
   npm install -g firebase-tools
   firebase login
   cd admin-dashboard
   firebase deploy --only firestore,hosting
   ```
   The dashboard is then at `https://krouen-tes.web.app`.
5. **Add the first admin.** Open the dashboard and sign in with Google. It shows your user ID and says you are not an admin yet. In the Firebase console, open Firestore, start a collection named `admins`, use your user ID as the document ID, and add a field `email` with your email. Reload the dashboard.
6. **Optional: import sample content.** On the Overview page, click **Import sample content** to start from 13 Khmer recipes and 16 categories you can edit or delete.

After that, other people can sign in and click **Ask to become an admin**. Approve them on Users, under Dashboard admins.

## Updating the dashboard

After changes to this folder, publish again from `admin-dashboard`:

```
firebase deploy --only firestore,hosting
```

This sends the database rules and the web page together. New pages need their new rules, so always deploy both.

## Good to know

- **Blocking a user** signs them out of the app the next time it is open with internet, and they can't post recipes. It does not delete their account.
- **Deleting a category** is only possible when no recipe uses it. Use Hide to take it off the app for now.
- **Security rules** are in `firestore.rules`. Anyone can read categories and published recipes. Only admins can change them. Users can only see and edit their own profile, and they can't unblock themselves.
- **Running locally against the real database:** copy `public/js/config.example.js` to `public/js/config.js` and fill in the web app values. `config.js` is not committed.
