HOW TO CHANGE THE APP ICON
==========================
Replace these files, keeping the SAME NAMES and the pixel sizes below.

1) ic_launcher_foreground.png  (your logo on a TRANSPARENT background; keep the artwork inside the centre 66% so it is not cropped)
   mipmap-mdpi 108x108 | hdpi 162x162 | xhdpi 216x216 | xxhdpi 324x324 | xxxhdpi 432x432
   Used for the modern adaptive icon, the themed (monochrome) icon and the Android 12 splash screen.

2) Background of the adaptive icon (the navy gradient): res/drawable/ic_launcher_background.xml

3) ic_launcher.png (rounded square) and ic_launcher_round.png (circle) - full artwork including background
   mipmap-mdpi 48x48 | hdpi 72x72 | xhdpi 96x96 | xxhdpi 144x144 | xxxhdpi 192x192
   Only used by very old Android versions / some launchers.

4) Google Play listing icon: app/src/main/ic_launcher-playstore.png  (512x512)

Easiest way: in Android Studio use File > New > Image Asset, pick "Launcher Icons (Adaptive and Legacy)" and it writes all of these for you.
