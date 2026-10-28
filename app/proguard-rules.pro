# Regras do R8 específicas do app (as bibliotecas já trazem as próprias regras).

# O WorkManager instancia por reflexão o banco gerado pelo Room (WorkDatabase_Impl).
# Sem esta regra o R8 remove o construtor e o app fecha ao abrir (NoSuchMethodException).
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# O Glance associa cada widget aos seus ids pelo nome da classe. Small/Medium/LargeWidget são quase
# idênticas e o R8 as funde numa só, então o updateAll de uma atualizava os ids das três
# (o 4x2 passava a mostrar o conteúdo do 2x2).
-keep class * extends androidx.glance.appwidget.GlanceAppWidget

# O Glance guarda só o nome da classe do callback na ação e o instancia por reflexão ao tocar no widget.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
