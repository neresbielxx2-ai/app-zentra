package com.zentra.z3d.core

/** Undo/redo por snapshots profundos da cena. */
class UndoRedo(private val maxSteps: Int = 60) {
    private val undoStack = ArrayDeque<Scene>()
    private val redoStack = ArrayDeque<Scene>()

    fun push(scene: Scene) {
        undoStack.addLast(scene.deepCopy())
        while (undoStack.size > maxSteps) undoStack.removeFirst()
        redoStack.clear()
    }

    fun undo(current: Scene): Scene? {
        if (undoStack.isEmpty()) return null
        redoStack.addLast(current.deepCopy())
        return undoStack.removeLast()
    }

    fun redo(current: Scene): Scene? {
        if (redoStack.isEmpty()) return null
        undoStack.addLast(current.deepCopy())
        return redoStack.removeLast()
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()
    fun clear() { undoStack.clear(); redoStack.clear() }
}
