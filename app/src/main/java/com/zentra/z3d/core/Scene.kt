package com.zentra.z3d.core

/** Cena do projeto: objetos, materiais e animacao. Mutacoes devem ocorrer sob um lock externo. */
class Scene(var name: String = "Untitled") {
    var objects: MutableList<SceneObject> = mutableListOf()
    var materials: MutableList<MaterialData> = mutableListOf()
    var animation: AnimationData = AnimationData()

    fun find(id: String?): SceneObject? {
        if (id == null) return null
        return objects.find { it.id == id }
    }

    fun uniqueName(base: String): String {
        val taken = objects.map { it.name }.toSet()
        if (base !in taken) return base
        var i = 2
        while ("$base.$i" in taken) i++
        return "$base.$i"
    }

    fun uniqueMaterialName(base: String): String {
        val taken = materials.map { it.name }.toSet()
        if (base !in taken) return base
        var i = 2
        while ("$base.$i" in taken) i++
        return "$base.$i"
    }

    fun materialOf(obj: SceneObject): MaterialData? =
        materials.find { it.id == obj.materialId }

    fun defaultMaterial(): MaterialData {
        if (materials.isEmpty()) {
            materials.add(MaterialData(name = "Material"))
        }
        return materials.first()
    }

    fun mainCamera(): SceneObject? =
        objects.find { it.type == ObjectType.CAMERA && it.camera?.isMain == true }
            ?: objects.find { it.type == ObjectType.CAMERA }

    fun lights(): List<SceneObject> = objects.filter { it.type == ObjectType.LIGHT && it.visible }

    fun childrenOf(id: String): List<SceneObject> = objects.filter { it.parentId == id }

    /** Matriz mundo compondo hierarquia de pais. */
    fun worldMatrix(obj: SceneObject): Mat4 {
        var result = obj.transform.matrix()
        var parentId = obj.parentId
        var guard = 0
        while (parentId != null && guard++ < 32) {
            val parent = find(parentId) ?: break
            result = parent.transform.matrix().multiplied(result)
            parentId = parent.parentId
        }
        return result
    }

    fun worldPosition(obj: SceneObject): Vec3 = worldMatrix(obj).translationPart()

    fun triCountTotal(): Int {
        var n = 0
        for (o in objects) n += o.mesh?.triCount() ?: 0
        return n
    }

    fun deepCopy(): Scene {
        val s = Scene(name)
        s.objects = objects.map { it.deepCopy() }.toMutableList()
        s.materials = materials.map { it.deepCopy() }.toMutableList()
        s.animation = animation.deepCopy()
        return s
    }

    fun restoreFrom(other: Scene) {
        name = other.name
        objects = other.objects.map { it.deepCopy() }.toMutableList()
        materials = other.materials.map { it.deepCopy() }.toMutableList()
        animation = other.animation.deepCopy()
    }

    companion object {
        fun newDefault(name: String): Scene {
            val scene = Scene(name)
            val mat = MaterialData(name = "Material")
            scene.materials.add(mat)
            val cube = Primitives.createObject(PrimitiveKind.CUBE, mat.id)
            cube.name = "Cube"
            scene.objects.add(cube)
            val light = Primitives.createObject(PrimitiveKind.LIGHT_POINT, null)
            light.transform.position = Vec3(4f, 5f, 3f)
            scene.objects.add(light)
            val cam = Primitives.createObject(PrimitiveKind.CAMERA, null)
            cam.transform.position = Vec3(5.5f, 4f, 6.5f)
            cam.transform.rotation = Vec3(-22f, 38f, 0f)
            cam.camera?.isMain = true
            scene.objects.add(cam)
            return scene
        }
    }
}
