package com.courier.example.fragments

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.coroutineScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.courier.android.Courier
import com.courier.android.models.*
import com.courier.android.modules.*
import com.courier.example.R
import com.courier.example.toJson
import kotlinx.coroutines.launch


class CustomInboxFragment: Fragment(R.layout.fragment_custom_inbox) {

    private lateinit var stateTextView: TextView
    private lateinit var inboxListener: CourierInboxListener
    private lateinit var recyclerView: RecyclerView

    private val messagesAdapter = MessagesAdapter()
    private val loadingAdapter = LoadingAdapter()
    private val adapter = ConcatAdapter(messagesAdapter)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        stateTextView = view.findViewById(R.id.stateTextView)

        // Get the menu
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        toolbar.setOnMenuItemClickListener { item ->
            return@setOnMenuItemClickListener if (item.itemId == R.id.readAll) {

                Courier.shared.readAllInboxMessages(
                    onSuccess = {
                        print("Messages are read")
                    },
                    onFailure = { error ->
                        print(error)
                    }
                )

                true
            } else {
                false
            }
        }

        // Create the list
        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.adapter = adapter
        recyclerView.addItemDecoration(DividerItemDecoration(activity, DividerItemDecoration.VERTICAL))

        // Handle pull to refresh
        val refreshLayout = view.findViewById<SwipeRefreshLayout>(R.id.refreshLayout)
        refreshLayout.setOnRefreshListener {

            Courier.shared.refreshInbox {
                refreshLayout.isRefreshing = false
            }

        }

        // Setup the listener
        inboxListener = Courier.shared.addInboxListener(
            onInitialLoad = {

                stateTextView.isVisible = false

                refreshAdapters(
                    showLoading = true
                )

            },
            onError = { e ->

                stateTextView.text = e.message
                stateTextView.isVisible = true

                print(e)
                refreshAdapters()

            },
            onMessagesChanged = { messages, unreadMessageCount, totalMessageCount, canPaginate ->

                stateTextView.isVisible = messages.isEmpty()
                stateTextView.text = "No messages found"

                refreshAdapters(
                    showMessages = messages.isNotEmpty(),
                    showLoading = canPaginate
                )

            }
        )

    }

    @SuppressLint("NotifyDataSetChanged")
    private fun refreshAdapters(showMessages: Boolean = false, showLoading: Boolean = false) {
        if (showMessages) adapter.addAdapter(0, messagesAdapter) else adapter.removeAdapter(messagesAdapter)
        if (showLoading) adapter.addAdapter(loadingAdapter) else adapter.removeAdapter(loadingAdapter)
        messagesAdapter.notifyDataSetChanged()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        inboxListener.remove()
    }

}

/**
 * List Item
 */

class MessageItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    val textView: TextView

    init {
        textView = itemView.findViewById(R.id.textView)
    }

}

class MessagesAdapter : RecyclerView.Adapter<MessageItemViewHolder>() {

    private val messages get() = Courier.shared.inboxMessages.orEmpty()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.message_item, parent, false)
        return MessageItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageItemViewHolder, position: Int) {
        val inboxMessage = messages[position]
        val isRead = inboxMessage.isRead
        holder.textView.text = inboxMessage.toJson()
        holder.textView.setBackgroundColor(if (isRead) Color.TRANSPARENT else Color.GREEN)
        holder.textView.setOnClickListener {
            if (isRead) inboxMessage.markAsUnread() else inboxMessage.markAsRead()
        }
    }

    override fun getItemCount(): Int {
        return messages.size
    }

}

/**
 * Loading Item
 */

class LoadingItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
class LoadingAdapter : RecyclerView.Adapter<LoadingItemViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LoadingItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.loading_item, parent, false)
        return LoadingItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: LoadingItemViewHolder, position: Int) {
        Courier.shared.fetchNextPageOfMessages(
            onSuccess = { newMessages ->
                print(newMessages)
            },
            onFailure = { error ->
                print(error)
            }
        )
    }

    override fun getItemCount(): Int {
        return 1
    }

}