package com.bruhascended.sms.data

import android.content.Context
import android.os.Bundle
import androidx.room.Room
import com.bruhascended.db.*
import com.bruhascended.sms.ui.*
import com.bruhascended.sms.ml.OrganizerModel
import com.bruhascended.sms.ui.main.MainViewModel
import com.google.firebase.analytics.FirebaseAnalytics

class IncomingSMSManager(context: Context) {
    private var mContext: Context = context

    fun putMessage(sender: String, body: String): Pair<Message, Conversation> {
        if (isMainViewModelNull()) {
            mainViewModel = MainViewModel()
            mainViewModel.daos = Array(6){
                Room.databaseBuilder(
                    mContext, ConversationDatabase::class.java,
                    mContext.resources.getString(labelText[it])
                ).allowMainThreadQueries().build().manager()
            }
        }

        val rawNumber = ContactsManager(mContext).getRaw(sender)
        val nn = OrganizerModel(mContext)
        val senderNameMap = ContactsManager(mContext).getContactsHashMap()

        val message = Message(
            null,
            rawNumber,
            body,
            1,
            System.currentTimeMillis(),
            -1
        )

        val bundle = Bundle()
        bundle.putString(
            FirebaseAnalytics.Param.METHOD,
            "background"
        )
        FirebaseAnalytics.getInstance(mContext).logEvent("conversation_organised", bundle)

        var conversation: Conversation? = null
        for (i in 0..4) {
            val got = mainViewModel.daos[i].findBySender(rawNumber)
            if (got.isNotEmpty()) {
                conversation = got.first()
                for (item in got.slice(1 until got.size))
                    mainViewModel.daos[i].delete(item)
                break
            }
        }

        var mProbs: FloatArray? = null
        val prediction = if (senderNameMap.containsKey(rawNumber)) 0
        else if (conversation != null && conversation.forceLabel != -1) conversation.forceLabel
        else {
            mProbs = nn.getPrediction(message)
            if (conversation != null) for (j in 0..4) conversation.probs[j] += mProbs[j]
            mProbs.toList().indexOf(mProbs.maxOrNull())
        }
        nn.close()


        conversation = if (conversation != null) {
            conversation.apply {
                read = false
                time = message.time
                lastSMS = message.text
                label = prediction
                probs = mProbs ?: probs
                name = senderNameMap[rawNumber]
                mainViewModel.daos[prediction].update(this)
            }
            conversation
        } else {
            val con = Conversation(
                null,
                rawNumber,
                senderNameMap[rawNumber],
                "",
                false,
                message.time,
                message.text,
                prediction,
                -1,
                FloatArray(5) {
                    if (it == 0) 1f else 0f
                }
            )
            mainViewModel.daos[prediction].insert(con)
            con.id = mainViewModel.daos[prediction].findBySender(rawNumber).first().id
            con
        }

        val mdb = if (conversationSender == rawNumber) conversationDao
        else Room.databaseBuilder(
            mContext, MessageDatabase::class.java, rawNumber
        ).build().manager()

        mdb.insert(message)

        return mdb.search(message.time).first() to conversation
    }
}