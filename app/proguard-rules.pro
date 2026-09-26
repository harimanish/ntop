# ntop: Shizuku user service + AIDL must survive R8 (looked up by
# class name from the Shizuku server process; explicit tag used too).
-keep class com.ntop.app.stats.ProcUserService { *; }
-keep class com.ntop.app.stats.IProcService* { *; }
# ntop Glyph Toy: manifest-registered service + Nothing GDK calls.
-keep class com.ntop.app.stats.GlyphToyService { *; }
-keep class com.nothing.ketchum.** { *; }
-keep class com.nothing.thirdparty.** { *; }

# GlyphMode is written to DataStore as its constant *name* and read back by
# string, so R8 must not rename the constants — otherwise every per-app glyph
# assignment silently stops matching after a release build.
-keepnames enum com.ntop.app.stats.GlyphMode
