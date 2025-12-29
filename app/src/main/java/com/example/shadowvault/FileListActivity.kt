package com.example.shadowvault

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.shadowvault.models.FileListViewModel
import androidx.lifecycle.repeatOnLifecycle
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.shadowvault.factories.FileListViewModelFactory
import kotlinx.coroutines.launch
import java.io.File

class FileListActivity : AppCompatActivity() {
    private lateinit var taskbarController: TaskbarController

    private val viewModel: FileListViewModel by viewModels() {
        FileListViewModelFactory(intent.getStringExtra("path")!!)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_file_list)

        val path = intent.getStringExtra("path") ?: return
        viewModel.load(path)

        val swipe = findViewById<SwipeRefreshLayout>(R.id.swipe_refresh)

        swipe.setOnRefreshListener {
            viewModel.load(path)
        }

        val prop = findViewById<ImageButton>(R.id.info_btn)
        prop.isEnabled = false
        prop.alpha = 0.4f
        val recycler = findViewById<RecyclerView>(R.id.recycler_view)
        val adapter = MyAdapter(viewModel, this)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        taskbarController = TaskbarController(
            topTaskbar = findViewById(R.id.top_taskbar_selection),
            bottomTaskbar = findViewById(R.id.default_bottom_taskbar),
            copyPasteTaskbar = findViewById(R.id.copy_paste_taskbar),
            selectedCountText = findViewById(R.id.selected_count)
        )

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                // observe file list
                launch {
                    viewModel.files.collect { list ->
                        adapter.submitList(list)
                        findViewById<TextView>(R.id.nofiles_textview)
                            .visibility = if (list.isEmpty()) View.VISIBLE else View.GONE

                        swipe.isRefreshing = false
                    }
                }

                // observe selection
                launch {
                    viewModel.selected.collect { selected ->
                        val count = selected.size

                        if (count > 0) {
                            taskbarController.show(count)
                        } else {
                            taskbarController.hide()
                        }

                        adapter.notifySelectionChanged(selected)
                    }
                }
                // observe loading
                launch {
                    viewModel.loading.collect { isLoading ->
                        swipe.isRefreshing = isLoading
                    }
                }

            }
        }

        val pasteBtn = findViewById<ImageButton>(R.id.paste_copy_btn)

        // copy paste actions
        findViewById<ImageButton>(R.id.cancel_copy_btn).setOnClickListener {
            viewModel.clearClipboard()
            viewModel.clearSelection()
            taskbarController.hide_copy_paste()
        }
        findViewById<ImageButton>(R.id.paste_copy_btn).setOnClickListener {
            //viewModel.pasteSelected()
            viewModel.clearSelection()
            Toast.makeText(this, "Items pasted", Toast.LENGTH_SHORT).show()
            taskbarController.hide_copy_paste()
        }

        // bottom taskbar actions
        findViewById<ImageButton>(R.id.cancel_btn).setOnClickListener {
            viewModel.clearSelection()
        }

        findViewById<ImageButton>(R.id.select_all_btn).setOnClickListener {
            viewModel.selectAll()
        }

        findViewById<ImageButton>(R.id.copy_btn).setOnClickListener {
            viewModel.clearSelection()
            taskbarController.show_copy_paste()
        }

        findViewById<ImageButton>(R.id.cut_btn).setOnClickListener {
            viewModel.clearSelection()
            val newPath = intent.getStringExtra("path")
            if (path == newPath) {
                pasteBtn.isEnabled = false
                pasteBtn.alpha = 0.4f
            }
            taskbarController.show_copy_paste()
        }

        findViewById<ImageButton>(R.id.rename_btn).setOnClickListener {
            val sel = viewModel.selected.value.firstOrNull() ?: return@setOnClickListener
            showRenameDialog(sel)
        }

        findViewById<ImageButton>(R.id.delete_btn).setOnClickListener {
            showDeleteDialog()
        }
    }

    private fun showDeleteDialog() {
        val selectedItems = viewModel.sizeSelected()
        if (selectedItems > 1) {
            AlertDialog.Builder(this)
                .setTitle("$selectedItems items will be deleted, are you sure?")
                .setPositiveButton("Delete") { _, _ ->
                    viewModel.deleteSelected()
                    Toast.makeText(this, "$selectedItems items deleted", Toast.LENGTH_SHORT)
                        .show()
                }
                .setNegativeButton("Cancel", null)
                .create()
                .show()
        } else {
            val file = viewModel.selected.value.firstOrNull() ?: return
            AlertDialog.Builder(this)
                .setTitle("Delete ${file.name}?")
                .setPositiveButton("Delete") { _, _ ->
                    viewModel.deleteSelected()
                    Toast.makeText(this, "${file.name} deleted", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showRenameDialog(file: File) {
        val edit = EditText(this)
        val oldName = file.name
        edit.setText(file.name)

        AlertDialog.Builder(this)
            .setTitle("Rename")
            .setView(edit)
            .setPositiveButton("OK") { _, _ ->
                val name = edit.text.toString()
                if (name.isNotBlank()) {
                    viewModel.renameFile(file, name)
                    Toast.makeText(this, "$oldName renamed to ${name}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
