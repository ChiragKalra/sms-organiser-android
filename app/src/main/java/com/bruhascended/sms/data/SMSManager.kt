package com.bruhascended.sms.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import com.bruhascended.sms.R
import com.bruhascended.sms.db.*
import com.bruhascended.sms.mainViewModel
import com.bruhascended.sms.ui.start.StartViewModel
import kotlin.math.roundToInt


val labelText = arrayOf(
    R.string.tab_text_1,
    R.string.tab_text_2,
    R.string.tab_text_3,
    R.string.tab_text_4,
    R.string.tab_text_5,
    R.string.tab_text_6
)

/*
const val MESSAGE_TYPE_ALL = 0
const val MESSAGE_TYPE_INBOX = 1
const val MESSAGE_TYPE_SENT = 2
const val MESSAGE_TYPE_DRAFT = 3
const val MESSAGE_TYPE_OUTBOX = 4
const val MESSAGE_TYPE_FAILED = 5 // for failed outgoing messages
const val MESSAGE_TYPE_QUEUED = 6 // for messages to send later
*/

class SMSManager (context: Context) {
    private var mContext: Context = context

    private val messages = HashMap<String, ArrayList<Message>>()
    private val labels = Array(5){ArrayList<String>()}

    private lateinit var senderNameMap: HashMap<String, String>

    fun getMessages() {
        val sp = mContext.getSharedPreferences("local", Context.MODE_PRIVATE)
        val lastDate = sp.getLong("last", 0).toString()

        val cm = ContactsManager(mContext)

        val cursor = mContext.contentResolver.query(
            Uri.parse("content://sms/"),
            null,
            "date" + ">?",
            arrayOf(lastDate),
            "date ASC"
        )

        if (cursor != null && cursor.moveToFirst()) {
            val nameID = cursor.getColumnIndex("address")
            val messageID = cursor.getColumnIndex("body")
            val dateID = cursor.getColumnIndex("date")
            val typeID = cursor.getColumnIndex("type")

            do {
                var name = cursor.getString(nameID)
                if (name != null) {
                    name = cm.getRaw(name)
                    val message = Message(
                        null,
                        name,
                        cursor.getString(messageID),
                        cursor.getString(typeID).toInt(),
                        cursor.getString(dateID).toLong(),
                        -1
                    )

                    if (messages.containsKey(name)) {
                        messages[name]?.add(message)
                    } else {
                        messages[name] = arrayListOf(message)
                    }
                }
            } while (cursor.moveToNext())

            cursor.close()
        }
    }

    fun getLabels(pageViewModel: StartViewModel?) {
        val nn = OrganizerModel(mContext)
        val fe = FeatureExtractor(mContext)

        senderNameMap = ContactsManager(mContext).getContactList()

        var total = 0
        for ((_, msgs) in messages) {
            total += msgs.size
        }

        var done = 0
        for ((sender, msgs) in messages) {
            if (senderNameMap.containsKey(sender)) {
                labels[0].add(sender)
            } else {
                val prediction = nn.getPredictions(msgs, fe.getFeatureMatrix(msgs))
                if ((!sender.first().isDigit()) && prediction == 0)
                    labels[1].add(sender)
                else
                    labels[prediction].add(sender)
            }
            done += msgs.size
            pageViewModel?.progress?.postValue((done * 100f / total).roundToInt())
        }
    }

    fun saveMessages() {
        for (i in 0..4) {
            val db: ConversationDao = if (mainViewModel == null) {
                Room.databaseBuilder(
                    mContext, ConversationDatabase::class.java,
                    mContext.resources.getString(labelText[i])
                ).build().manager()
            } else {
                mainViewModel!!.daos[i]
            }
            for (conversation in labels[i]) {
                db.insert(
                    Conversation (
                        null,
                        conversation,
                        senderNameMap[conversation],
                        "",
                        true,
                        messages[conversation]!!.last().time,
                        messages[conversation]!!.last().text,
                        i
                    )
                )

                val mdb = Room.databaseBuilder(mContext, MessageDatabase::class.java, conversation)
                    .build().manager()
                for (message in messages[conversation]!!)
                    mdb.insert(message)
            }
        }
        mContext.getSharedPreferences("local", Context.MODE_PRIVATE).edit()
            .putLong("last", System.currentTimeMillis()).apply()
    }
}