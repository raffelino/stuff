Minimaler Ersatz für `androidx.test:monitor` und `espresso-idling-resource`, genau die
Schnittstelle, die Robolectric 4.14 zur Laufzeit benötigt. Wird nur von der SDK-freien
Pipeline (`tools/nosdk`) verwendet, weil Google Maven dort nicht erreichbar ist.
In Android Studio kommen die Original-Artefakte über `google()` zum Einsatz.
