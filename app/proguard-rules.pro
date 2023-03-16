-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

-dontwarn androidx.appcompat.graphics.drawable.DrawableWrapper

-keepattributes LineNumberTable,SourceFile
-renamesourcefileattribute SourceFile
-repackageclasses
-allowaccessmodification
-overloadaggressively
