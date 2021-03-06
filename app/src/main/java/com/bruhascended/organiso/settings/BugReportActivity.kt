package com.bruhascended.organiso.settings

import android.content.Intent
import android.database.Cursor
import android.graphics.drawable.AnimatedVectorDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import com.bruhascended.organiso.analytics.AnalyticsLogger
import com.bruhascended.organiso.R
import com.bruhascended.organiso.common.setPrefTheme
import com.bruhascended.organiso.common.setupToolbar
import kotlinx.android.synthetic.main.activity_bug_report.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
                    Copyright 2020 Chirag Kalra

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

*/

class BugReportActivity : AppCompatActivity() {

    private var fileUri: Uri? = null

    private val loadMediaResult = registerForActivityResult(StartActivityForResult()) {
        it.data.apply {
            if (this != null && data != null) {
                fileUri = data!!
                fileName.text = getFileName(fileUri!!)
                fileName.visibility = View.VISIBLE
                addFile.apply {
                    setImageResource(R.drawable.close)
                    setOnClickListener {
                        hideMediaPreview()
                    }
                }
            }
        }
    }

    private fun loadMedia() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "image/*"
        loadMediaResult.launch(intent)
    }

    private fun fadeAway(vararg views: View) {
        for (view in views) view.apply {
            if (visibility == View.VISIBLE) {
                alpha = 1f
                animate()
                    .alpha(0f)
                    .setDuration(300)
                    .start()
                GlobalScope.launch {
                    delay(400)
                    runOnUiThread {
                        alpha = 1f
                        visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun hideMediaPreview() {
        fadeAway(fileName)
        addFile.setImageResource(R.drawable.close_to_add)
        (addFile.drawable as AnimatedVectorDrawable).start()
        addFile.setOnClickListener {
            loadMedia()
        }
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme.equals("content")) {
            val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
            }
            cursor?.close()
        }
        if (result == null) {
            result = uri.path
            val cut = result!!.lastIndexOf('/')
            if (cut >= 0) result = result.substring(cut + 1)
        }
        return result
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPrefTheme()
        setContentView(R.layout.activity_bug_report)
        setupToolbar(toolbar)

        titleEditText.requestFocus()

        addFile.setOnClickListener {
            loadMedia()
        }

        submit.setOnClickListener {
            if (titleEditText.text.toString() != "" && full.text.toString() != "") {
                AnalyticsLogger(this).reportBug(
                    titleEditText.text.toString(),
                    full.text.toString(),
                    fileUri
                )
                Toast.makeText(this, getString(R.string.bug_report_sent), Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, getString(R.string.empty_fields), Toast.LENGTH_SHORT).show()
            }
        }
    }
}