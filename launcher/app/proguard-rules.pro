# Shrink and optimise, but keep class and method names: the size win comes from dropping unused
# AndroidX code, not from renaming, and readable stack traces matter more in a sideloaded app
# where crash reports arrive as screenshots.
-dontobfuscate

# The Home Assistant detail pages call these from JavaScript.
-keepclassmembers class com.gathin.tvhome.HaDetailActivity$Bridge {
    @android.webkit.JavascriptInterface <methods>;
}
