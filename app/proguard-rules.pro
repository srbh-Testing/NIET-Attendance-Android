# Gson uses reflection to read/write field names, so R8/ProGuard must not
# rename or strip the fields of any class we deserialize JSON into.
# Without this, release builds parse attendance JSON silently wrong/empty
# even though debug builds work fine.

-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Gson's TypeToken generic signatures must survive for List<T> deserialization.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Keep every model class this app hands to Gson, and their field names.
-keep class com.example.nietattendance.AttendanceSubject { *; }
-keep class com.example.nietattendance.ScheduleEntry { *; }
-keep class com.example.nietattendance.TodayScheduleWrapper { *; }
-keep class com.example.nietattendance.SubjectAttendanceRecord { *; }
-keep class com.example.nietattendance.AttendanceParams { *; }
-keep class com.example.nietattendance.DaySnapshot { *; }
-keep class com.example.nietattendance.SubjectSnapshot { *; }

# Gson's @SerializedName / other annotations must stay attached to fields.
-keepclassmembers class com.example.nietattendance.** {
    @com.google.gson.annotations.SerializedName <fields>;
}
