package com.example.shadowvault.models

import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class FileListViewModel(
    private val savedStateHandle: String
) : ViewModel() {

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _files = MutableStateFlow<List<File>>(emptyList())
    val files: StateFlow<List<File>> = _files

    private val _selected = MutableStateFlow<Set<File>>(emptySet())
    val selected: StateFlow<Set<File>> = _selected

    enum class ClipboardAction { DUPLICATE, CUT }
    private val _clipboardFiles = MutableStateFlow<List<File>>(emptyList())
    private val _clipboardAction = MutableStateFlow<ClipboardAction?>(null)
    val clipboardFiles: StateFlow<List<File>> = _clipboardFiles
    val clipboardAction: StateFlow<ClipboardAction?> = _clipboardAction

    fun load(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
            val root = File(path)
            val list = root.listFiles()?.toList() ?: emptyList()
            Log.d("FILES", "Recebidos: ${list.size}")
            Log.d("SWIPE", "emitindo lista: ${list.size}")

            _files.value = list
            _loading.value = false
        }
    }

    fun toggleSelection(file: File) {
        val current = _selected.value.toMutableSet()
        if (current.contains(file)) current.remove(file)
        else current.add(file)
        _selected.value = current
    }

    fun clearSelection() {
        _selected.value = emptySet()
    }

    fun selectAll() {
        _selected.value = _files.value.toSet()
    }

    fun deleteSelected() {
        viewModelScope.launch(Dispatchers.IO) {
            val toDelete = _selected.value.toList()
            toDelete.forEach { it.delete() }
            val updated = _files.value.filter { it !in toDelete }
            _files.value = updated
            _selected.value = emptySet()
        }
    }

    fun renameFile(file: File, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val newFile = File(file.parent, newName)
            if (file.renameTo(newFile)) {
                _files.value = _files.value.map { if (it == file) newFile else it }
                _selected.value = emptySet()
            }
        }
    }

    fun sizeSelected(): Int {
        return _selected.value.size
    }

    private fun resolveNameConflict(destinationDir: File, source: File): File {
        val name = source.nameWithoutExtension
        val ext = source.extension.takeIf { it.isNotEmpty() }?.let { ".$it" } ?: ""

        var index = 0
        var candidate: File

        do {
            val suffix = if (index == 0) "" else " ($index)"
            candidate = File(destinationDir, "$name$suffix$ext")
            index++
        } while (candidate.exists())

        return candidate
    }

    fun clearClipboard() {
        _clipboardFiles.value = emptyList()
        _clipboardAction.value = null
    }

    fun duplicateSelection() {
        _clipboardFiles.value = _selected.value.toList()
        _clipboardAction.value = ClipboardAction.DUPLICATE
        _selected.value = emptySet()
    }

    fun cutSelection() {
        _clipboardFiles.value = _selected.value.toList()
        _clipboardAction.value = ClipboardAction.CUT
        _selected.value = emptySet()
    }

    fun duplicateFiles(destinationPath: String) {
        val filesToPaste = _clipboardFiles.value

        viewModelScope.launch(Dispatchers.IO) {
            val destinationDir = File(destinationPath)

            filesToPaste.forEach { source ->
                val target = resolveNameConflict(destinationDir, source)
                source.copyRecursively(target, overwrite = true)
            }

            _clipboardFiles.value = emptyList()

            load(destinationPath)
            clearSelection()
        }
    }

    fun pasteClipboard(destinationPath: String) {

        val filesToPaste = _clipboardFiles.value
        val action = _clipboardAction.value

        viewModelScope.launch(Dispatchers.IO) {
            val destinationDir = File(destinationPath)

            filesToPaste.forEach { source ->
                when (action) {
                    ClipboardAction.DUPLICATE -> {
                        val target = File(destinationDir, source.name)
                        source.copyRecursively(target, overwrite = true)
                    }
                    ClipboardAction.CUT -> {
                        val target = File(destinationDir, source.name)
                        source.renameTo(target)
                    }
                    else -> {}
                }
            }

            _clipboardFiles.value = emptyList()
            _clipboardAction.value = null

            load(destinationPath)
            clearSelection()
        }
    }
}
