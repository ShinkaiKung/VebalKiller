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

# Gson's TypeToken relies on generic signatures. Runtime annotations are kept
# as well so future @SerializedName fields remain safe under R8.
-keepattributes Signature
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault

# MemoryRecord is persisted as JSON in Room. Preserve its serialized field
# names so an app update can still read records written by an older version.
-keep class com.github.ShinkaiKung.verbalkiller.logic.MemoryRecord {
    <fields>;
}

# Preserve line numbers for actionable release crash reports.
-keepattributes SourceFile,LineNumberTable

# Hide original source filenames while retaining the line mapping.
-renamesourcefileattribute SourceFile
