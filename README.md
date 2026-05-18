# NodeCast TV 📺

App nativo Android TV para Xtream Codes — sem servidor externo, tudo roda no próprio APK.

## Funcionalidades

- 📺 **TV ao Vivo** — categorias + canais com busca
- 🎬 **Filmes (VOD)** — grid com poster e busca
- 📺 **Séries** — navegação por temporada e episódio
- ▶️ **Player ExoPlayer** — HLS/MPEG-TS, controle por D-pad
- 🔍 **Busca em tempo real** em qualquer lista
- 🎮 **Otimizado para controle remoto** Android TV

## Como gerar o APK

### Opção 1 — GitHub Actions (recomendado, sem instalar nada)

1. Faça um fork deste repositório no GitHub
2. Vá em **Actions** → **Build APK** → **Run workflow**
3. Aguarde o build (~3 minutos)
4. Baixe o `nodecast-tv-debug.apk` em **Artifacts**
5. Instale na Android TV via **ADB** ou sideload

### Opção 2 — Android Studio (local)

```bash
# Requisitos: Android Studio + JDK 17
git clone https://github.com/SEU_USUARIO/nodecast-tv-android.git
cd nodecast-tv-android
./gradlew assembleDebug
# APK em: app/build/outputs/apk/debug/app-debug.apk
```

## Instalando na Android TV

### Via ADB (PC na mesma rede Wi-Fi)

```bash
# 1. Habilitar depuração ADB na TV:
#    Configurações → Sobre → clicar 7x em "Build Number" → Opções do Desenvolvedor → Depuração ADB

# 2. Descobrir o IP da TV:
#    Configurações → Rede → Ver IP

# 3. Conectar e instalar:
adb connect IP_DA_TV:5555
adb install app-debug.apk
```

### Via USB
```bash
adb install -t app-debug.apk
```

### Via Downloader App
1. Instale o app "Downloader" na Android TV
2. Hospede o APK em algum lugar (Google Drive, Dropbox, etc.)
3. Abra o link no Downloader

## Configuração

Na primeira execução, preencha:

| Campo | Exemplo |
|-------|---------|
| URL do Servidor | `http://meu.servidor.com:8080` |
| Usuário | `meuusuario` |
| Senha | `minhasenha` |

## Stack

- **Kotlin** + Android TV Leanback
- **ExoPlayer / Media3** — player HLS + MPEG-TS
- **OkHttp** — cliente HTTP para API Xtream Codes
- **Coil** — carregamento de imagens/posters
- **Coroutines** — operações assíncronas

## Licença

GPL-3.0
