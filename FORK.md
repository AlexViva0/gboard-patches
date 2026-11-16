# Fork auditado de Gboard Patches

Base: [jasonwu1994/Gboard-patches](https://github.com/jasonwu1994/Gboard-patches) v4.3.0,
commit `463269a` (2026-11-12). Licencia GPLv3, igual que el original.

## Por qué existe este fork

Un teclado ve todo lo que escribes. El repositorio original es un *mirror* público
de un repositorio privado (cada commit trae cientos de archivos a la vez), sus
releases no van firmadas y Morphe Manager actualiza solo. Este fork se compila
localmente desde código revisado y no recibe cambios del autor original sin
revisarlos antes.

## Resultado de la auditoría de `463269a`

No se encontró telemetría, analíticas ni envío de texto, portapapeles o
identificadores del dispositivo. Conexiones salientes que quedan:

| Destino | Para qué | Integridad |
|---|---|---|
| `www.gstatic.com` | Diccionarios y modelos oficiales de Google (lo mismo que baja Gboard) | De Google |
| `raw.githubusercontent.com/GboardThemes/PackRepoBeta` | Temas de Rboard, solo al importarlos | Commit fijo + SHA-256 |
| `github.com/jasonwu1994/Gboard-patches/releases` | Vistas previas de temas, solo al abrir la galería | SHA-256 fijo |

## Qué se eliminó

| Componente | Motivo |
|---|---|
| **Web Clipboard** | Servidor HTTP en toda la red WiFi que expone el portapapeles; código de 4 dígitos (`0000` por defecto) sin límite de intentos. Incluía un bundle React minificado sin código fuente. |
| **FTP Server** | Por defecto: anónimo, lectura/escritura sobre todo `/sdcard`; añadía `MANAGE_EXTERNAL_STORAGE` y servicios en primer plano. |
| **Motor QuickJS** (`libgboardpatches_quickjs.so` + `QuickJsNgBridge`) | Binario nativo precompilado sin fuentes ni build reproducible, y un puente `httpGet`/`httpPost` que recibía el texto seleccionado. |
| `GboardTilePreferencesActivity` | Solo la usaban los tiles de Web Clipboard y FTP. |
| `.github/FUNDING.yml` | Pertenece al autor original. |

Los slots de la **fila superior deslizable** siguen funcionando, pero solo como
texto: los slots JavaScript se convierten en texto, la casilla está oculta y el
runtime lanza `UnsupportedOperationException` si algo intentara ejecutar JS.

## Cómo integrar cambios del original

```bash
git fetch upstream
git log --stat 463269a..upstream/main          # qué cambió
git diff 463269a upstream/main -- extensions/  # revisar sobre todo el código que corre en el teléfono
```

Antes de mezclar, buscar como mínimo: `URL(`, `openConnection`, `Socket`,
`System.load`, `DexClassLoader`, `Runtime.getRuntime`, `Base64.decode`,
`getTextBeforeCursor`, `getPrimaryClip`, archivos `.so`/`.jar`/`.js` nuevos y
permisos nuevos en los parches de manifest.

## Compilar

Necesita un token de GitHub con `read:packages` (para el plugin de Morphe):

```bash
gh auth refresh -h github.com -s read:packages
JAVA_HOME=/usr/lib/jvm/java-21-openjdk GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew :patches:buildAndroid
```

Requiere JDK 21: con JDK 27 el plugin de Android falla en `androidJdkImage`.

El resultado queda en `patches/build/libs/patches-*.mpp`.

## Releases y firma

Las releases salen de `.github/workflows/release.yml` al subir un tag `vX.Y.Z` que
coincida con `version` en `gradle.properties` y con `patches-bundle.json`:

1. Compila y ejecuta todos los tests en GitHub Actions.
2. Firma el `.mpp` con Ed25519 (secret `MORPHE_SIGNING_KEY`); la firma va embebida en el
   archivo y Morphe Manager la verifica contra `public_key` de `patches-bundle.json`
   (= `morphe-signing.pub`).
3. Falla si el `.mpp` no está firmado con esa clave.
4. Publica una atestación de origen del build (`gh attestation verify patches-X.Y.Z.mpp -R AlexViva0/gboard-patches`).

`morphe-signing.key` **nunca** se sube al repo (está en `.gitignore`). Si se pierde o se filtra,
hay que generar otra con `./gradlew generateSigningKey` y los usuarios tendrán que aceptar la
nueva clave en Morphe Manager.

Fuente para Morphe Manager: `https://github.com/AlexViva0/gboard-patches`
