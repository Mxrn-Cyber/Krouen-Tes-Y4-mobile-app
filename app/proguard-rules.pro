# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# ===== Krouen Tes =====
# Gson reads and writes these classes by their field names (dashboard recipes, saved
# recipes and profile), so R8 must not rename or remove their fields.
-keep class com.kroeungtes.app.data.model.** { *; }
-keep class com.kroeungtes.app.data.local.KhmerRecipes$* { *; }
-keep class com.kroeungtes.app.data.local.ProfileStore$* { *; }

# Gson needs generic type information (List<MyRecipe>) and its TypeToken subclasses
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Crash reports stay readable: keep file names and line numbers, hide the original file name
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
