# ProGuard / R8 rules for PATHY.
# Minification is currently disabled for release (isMinifyEnabled = false). When it is enabled,
# keep the Gson DTOs and Firestore models below, since they are created by reflection.

-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod

# Firestore / Gson models are read by name
-keepclassmembers class com.example.pathy.** {
    <fields>;
}
