// app/src/main/java/com/UIN/Tool/ui/screen/dev/CodeEditorActivity.kt
package com.UIN.Tool.ui.screen.dev

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.UIN.Tool.ui.theme.UINToolTheme

class CodeEditorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val fileList = intent.getStringArrayListExtra("file_list") ?: emptyList()
        val fileContents = (intent.getSerializableExtra("file_contents") as? HashMap<String, String>) ?: hashMapOf()
        val uiType = intent.getStringExtra("ui_type") ?: "native"
        val mainClass = intent.getStringExtra("main_class") ?: ""
        val pluginName = intent.getStringExtra("plugin_name") ?: ""
        val pluginId = intent.getStringExtra("plugin_id") ?: ""

        setContent {
            UINToolTheme {
                CodeEditorScreen(
                    fileList = fileList,
                    fileContents = fileContents,
                    uiType = uiType,
                    mainClass = mainClass,
                    pluginName = pluginName,
                    pluginId = pluginId,
                    onSave = { updatedFiles, updatedContents ->
                        val result = android.os.Bundle().apply {
                            putStringArrayList("file_list", ArrayList(updatedFiles))
                            putSerializable("file_contents", HashMap(updatedContents))
                        }
                        setResult(RESULT_OK, intent.apply { putExtras(result) })
                        finish()
                    },
                    onCancel = {
                        setResult(RESULT_CANCELED)
                        finish()
                    }
                )
            }
        }
    }
}