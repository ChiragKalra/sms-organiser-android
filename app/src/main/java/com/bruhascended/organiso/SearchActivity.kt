package com.bruhascended.organiso

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.bruhascended.organiso.db.Conversation
import com.bruhascended.organiso.db.Message
import com.bruhascended.organiso.db.MessageDbProvider
import com.bruhascended.organiso.ui.common.ScrollEffectFactory
import com.bruhascended.organiso.ui.search.SearchRecyclerAdaptor
import com.bruhascended.organiso.ui.search.SearchResultViewHolder.ResultItem
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_search.*
import java.util.*

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

class SearchActivity : AppCompatActivity() {

    private lateinit var mContext: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var categories: Array<Int>
    private lateinit var mAdaptor: SearchRecyclerAdaptor
    private lateinit var result: ArrayList<ResultItem>

    private var searchThread = Thread{}

    private fun showResults(key: String) {
        searchRecycler.scrollToPosition(0)
        searchRecycler.isVisible = true
        searchThread.interrupt()
        mAdaptor.searchKey = key
        mAdaptor.refresh()
        searchThread = Thread {
            val displayedSenders = arrayListOf<String>()
            for (category in categories) {
                if (searchThread.isInterrupted) return@Thread
                val cons = mainViewModel.daos[category].search("$key%", "% $key%")
                if (cons.isNotEmpty()) {ResultItem(4, categoryHeader = category)
                    cons.forEach { displayedSenders.add(it.sender) }
                    searchRecycler.post {
                        mAdaptor.addItems(listOf(ResultItem(4, categoryHeader = category)))
                        mAdaptor.addItems(
                            List(cons.size) {
                                ResultItem(0, conversation = cons[it])
                            }
                        )
                    }
                }
            }

            var otherDisplayed = false
            mainViewModel.contacts.value?.forEach {  contact ->
                val name = contact.name.toLowerCase(Locale.ROOT)
                if (!Regex("\\b${key}").matches(name))
                    return@forEach

                for (sender in displayedSenders) {
                    if (sender == contact.number) {
                        return@forEach
                    }
                }

                if (!otherDisplayed) {
                    otherDisplayed = true
                    searchRecycler.post {
                        mAdaptor.addItems(listOf(ResultItem(4, categoryHeader = 42)))
                    }
                }
                searchRecycler.post {
                    mAdaptor.addItems(listOf(ResultItem(1,
                        conversation = Conversation(contact.number, contact.name)
                    )))
                }
            }

            for (category in categories) {
                var isEmpty = true
                if (searchThread.isInterrupted) return@Thread
                for (con in mainViewModel.daos[category].loadAllSync()) {
                    var msgs: List<Message>
                    if (searchThread.isInterrupted) return@Thread
                    MessageDbProvider(mContext).of(con.sender).apply {
                        msgs = manager().search("$key%", "% $key%")
                        close()
                    }
                    if (!msgs.isNullOrEmpty()) {
                        if (isEmpty) {
                            isEmpty = false
                            searchRecycler.post {
                                mAdaptor.addItems(listOf(ResultItem(4, categoryHeader = 10+category)))
                            }
                        }
                        searchRecycler.post {
                            mAdaptor.addItems(listOf(ResultItem(1, conversation = con)))
                            mAdaptor.addItems(
                                List(msgs.size) {
                                    ResultItem(2, conversation = con, message = msgs[it])
                                }
                            )
                        }
                    }
                    if (searchThread.isInterrupted) return@Thread
                }
            }

            searchRecycler.post {
                mAdaptor.doOnLoaded()
            }
        }
        searchThread.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requireMainViewModel(this)

        prefs = PreferenceManager.getDefaultSharedPreferences(this)

        if (prefs.getBoolean("dark_theme", false)) setTheme(R.style.DarkTheme)
        else setTheme(R.style.LightTheme)
        setContentView(R.layout.activity_search)
        searchEditText.requestFocus()

        mContext = this

        result = arrayListOf(ResultItem(5))
        mAdaptor = SearchRecyclerAdaptor(mContext, result)
        mAdaptor.doOnConversationClick = {
            startActivity(
                Intent(mContext, ConversationActivity::class.java)
                    .putExtra("ye", it)
            )
            finish()
        }

        mAdaptor.doOnMessageClick = {
            startActivity(
                Intent(mContext, ConversationActivity::class.java)
                    .putExtra("ye", it.second)
                    .putExtra("ID", it.first)
            )
            finish()
        }

        searchRecycler.adapter = mAdaptor

        val visible = Gson().fromJson(
            prefs.getString("visible_categories", ""), Array<Int>::class.java
        )
        val hidden = Gson().fromJson(
            prefs.getString("hidden_categories", ""), Array<Int>::class.java
        )
        categories = if (prefs.getBoolean("show_hidden_results", false))
            visible + hidden else visible

        clear_text.setOnClickListener{
            searchEditText.setText("")
            searchEditText.onEditorAction(EditorInfo.IME_ACTION_SEARCH)
        }

        backButton.setOnClickListener{
            onBackPressed()
        }

        searchEditText.doOnTextChanged { key, _, _, _ ->
            clear_text.visibility = if (key.isNullOrEmpty()) View.GONE else View.VISIBLE
        }

        searchRecycler.apply {
            layoutManager = LinearLayoutManager(mContext).apply {
                orientation = LinearLayoutManager.VERTICAL
            }
            edgeEffectFactory = ScrollEffectFactory()
            addOnScrollListener(ScrollEffectFactory.OnScrollListener())
        }

        searchEditText.setOnEditorActionListener { _, i, _ ->
            if (i != EditorInfo.IME_ACTION_SEARCH) return@setOnEditorActionListener true
            val key = searchEditText.text.toString().trim().toLowerCase(Locale.ROOT)

            searchRecycler.isVisible = !key.isBlank()
            if (key.isBlank()) {
                return@setOnEditorActionListener true
            } else {
                showResults(key)
            }

            true
        }
    }

    override fun onBackPressed() {
        setResult(RESULT_CANCELED)
        if (intent.action != null) startActivityIfNeeded(
            Intent(mContext, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT), 0
        )
        finish()
        overridePendingTransition(R.anim.hold, android.R.anim.fade_out)
    }
}