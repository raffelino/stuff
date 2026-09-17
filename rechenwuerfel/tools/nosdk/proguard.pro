# ProGuard-Regeln für die SDK-freie Pipeline.
# Zweck: (1) Kotlin-Stdlib auf das Benötigte schrumpfen,
#        (2) invokedynamic-Lambdas (LambdaMetafactory) in echte Klassen
#            zurückportieren – der alte dx-Compiler reicht sie sonst als
#            invoke-custom in die Dex durch und Android stürzt zur Laufzeit ab.
-target 1.7
-dontobfuscate
-dontoptimize
-verbose
-ignorewarnings
-dontnote **
-dontwarn kotlin.**
-dontwarn org.jetbrains.annotations.**

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# Der gesamte App-Code bleibt erhalten (Activities/Views werden über Manifest und Layouts referenziert)
-keep class com.raffelino.rechenwuerfel.** { *; }

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
