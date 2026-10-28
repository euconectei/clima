# Regras do R8 específicas do app (as bibliotecas já trazem as próprias regras).

# O WorkManager instancia por reflexão o banco gerado pelo Room (WorkDatabase_Impl).
# Sem esta regra o R8 remove o construtor e o app fecha ao abrir (NoSuchMethodException).
-keep class * extends androidx.room.RoomDatabase { <init>(); }
