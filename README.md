# Zentra 3D

**Editor 3D mobile do ecossistema Zentra XR** — um "Blender simplificado para celular", com identidade própria: escuro, moderno, minimalista e touch-friendly.

Pacote: `com.zentra.z3d` · minSdk 26 · OpenGL ES 3.0 · Kotlin + Jetpack Compose + Material 3.

## ✨ Funcionalidades

### Editor 3D
- **Viewport 3D** com grid, iluminação Phong em tempo real, sombras suaves e helpers de cena
- **Criar objetos**: cubo, esfera, cilindro, cone, plano, torus, cápsula, texto 3D, empty, luzes e câmeras
- **Selecionar / Mover / Rotacionar / Escalar** com gizmo de eixos e trava X/Y/Z
- **Edit Mode**: vértices, edges e faces — Extrude, Inset, Bevel, Subdivide, Loop Cut, Merge, Dissolve, Delete, Separate
- **Undo / Redo** (60 passos)

### Materiais, luzes e câmeras
- Materiais PBR: cor base, rugosidade, metálico, emissivo, opacidade + preview
- Texturas: importar da galeria, repetir/deslocar UV, remover
- Luzes: Point, Spot, Direcional e Área (cor, intensidade, alcance, ângulo)
- Câmeras: FOV, near/far, câmera principal, **View Camera**

### Animação
- Timeline com keyframes de Position / Rotation / Scale, play/pause/rewind

### Projetos e arquivos
- Projetos locais com thumbnail, renomear/duplicar/excluir
- **Autosave** periódico + recuperação de trabalho
- **Importar**: GLB, GLTF, OBJ, STL (com limite de polígonos e simplificação)
- **Exportar**: GLB, GLTF, OBJ, STL (cena ou seleção)

### Mobile-first
- Layouts responsivos retrato/paisagem, áreas seguras (notch/punch-hole), alvos de toque ≥ 48dp
- Qualidade gráfica Auto/Baixa/Média/Alta com redução automática sob FPS baixo
- Gestos: 1 dedo (selecionar/manipular/orbitar), 2 dedos (pinch zoom + pan), 3 dedos (orbitar)

## 🏗️ Arquitetura

```
app/src/main/java/com/zentra/z3d/
├── core/        Math3D, SceneModels, Scene, Primitives, MeshOps, UndoRedo
├── render/      ZentraRenderer (GL ES 3.0), CameraAndPicking, RenderBridge
├── io/          ObjStlIO, GltfIO (GLB/GLTF), ModelImporter/Exporter
├── projects/    ProjectStore (JSON + texturas + thumbnails + autosave)
├── animation/   AnimationEngine (keyframes)
├── settings/    AppSettings + PerformanceManager
└── ui/          theme, components, home, editor (ViewModel + sheets + viewport)
```

- Sem dependências de engine 3D: matemática, malhas e renderizador próprios.
- Estado central no `EditorViewModel`; thread GL sincronizada via `RenderBridge.lock`.
- Serialização de projetos em JSON (Gson) com escrita atômica (temp + rename).

## 🔨 Build

### GitHub Actions (oficial)
Todo push/PR compila **debug + release** e publica os APKs em *Artifacts*:
`.github/workflows/android.yml` → `zentra-3d-debug` e `zentra-3d-release-unsigned`.

### Android Studio
Abra a pasta do projeto e aguarde o sync (Gradle 8.7, JDK 17, AGP 8.5).

### Linha de comando
```bash
export ANDROID_HOME=/caminho/para/android-sdk
gradle assembleDebug   # app/build/outputs/apk/debug/app-debug.apk
```

## 🎮 Atalhos de uso
`Adicionar → Selecionar → Mover → Editar → Material → Salvar` — fluxo guiado na aba **Ajuda** do app.

## 📄 Licença
Projeto do ecossistema Zentra XR. Todos os direitos reservados.
