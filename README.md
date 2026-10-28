# Clima

App de clima para Android com a identidade visual do sistema (Material 3, cores dinâmicas do Material You) e três widgets de tela inicial.

## Recursos

- Tela principal com o dia atual em destaque (8 indicadores) e os próximos dias compactos.
- Três widgets: pequeno (2x2), médio (4x2) e grande (4x4).
- Acento dos widgets que muda com o clima (sol, chuva, tempestade); o restante segue o papel de parede.
- Busca de cidade e localização atual.
- Dados da [Open-Meteo](https://open-meteo.com): gratuita e sem chave de API.

## Tecnologias

Kotlin, Jetpack Compose, Material 3, Jetpack Glance (widgets), WorkManager (atualização a cada 30 min).

## Como rodar

Requer JDK 17 e Android SDK 35. `minSdk` 31 (Android 12).

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Crie um `local.properties` com `sdk.dir=/caminho/do/Android/sdk` se o Android Studio não o gerar.

## Licença

[PolyForm Noncommercial 1.0.0](LICENSE): você pode usar, estudar, alterar e compartilhar o código para fins não comerciais. Para qualquer uso comercial (inclusive cobrar pelo app ou por uma versão derivada), é preciso uma licença comercial do autor: abra uma issue neste repositório para combinar.

## Créditos

Dados meteorológicos: [Open-Meteo.com](https://open-meteo.com) (CC BY 4.0).
