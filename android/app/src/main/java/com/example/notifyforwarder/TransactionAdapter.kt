package com.example.notifyforwarder

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionAdapter(
    private var items: List<TransactionRecord>,
    private val onItemClick: ((TransactionRecord) -> Unit)? = null
) : RecyclerView.Adapter<TransactionAdapter.ViewHolder>() {

    private val timeFormatter = SimpleDateFormat("h:mm:ss a", Locale.getDefault())
    private val dateFormatter = SimpleDateFormat("dd-MMM-yyyy hh:mm:ss a", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val itemAppName: TextView = view.findViewById(R.id.itemAppName)
        val itemAppPkg: TextView = view.findViewById(R.id.itemAppPkg)
        val itemTimestamp: TextView = view.findViewById(R.id.itemTimestamp)
        val itemAmount: TextView = view.findViewById(R.id.itemAmount)
        val itemDirectionBadge: TextView = view.findViewById(R.id.itemDirectionBadge)
        val itemForwardedBadge: TextView = view.findViewById(R.id.itemForwardedBadge)
        val itemSenderAndUtr: TextView = view.findViewById(R.id.itemSenderAndUtr)
        val itemRawSnippet: TextView = view.findViewById(R.id.itemRawSnippet)
        val itemServerStatus: TextView = view.findViewById(R.id.itemServerStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        // App Name & Package
        holder.itemAppName.text = if (item.appName.isNotBlank()) item.appName else item.packageName
        holder.itemAppPkg.text = item.packageName
        holder.itemTimestamp.text = timeFormatter.format(Date(item.timestamp))

        // Amount with exact paise
        if (item.amountPaise > 0L) {
            holder.itemAmount.text = "${item.amount} (${item.amountPaise} paise)"
        } else if (item.amount != "Not available in notification") {
            holder.itemAmount.text = item.amount
        } else {
            holder.itemAmount.text = "No amount"
        }

        // Direction Badge
        when (item.classification) {
            "INCOMING" -> {
                holder.itemDirectionBadge.text = "INCOMING"
                holder.itemDirectionBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#DCFCE7"))
                holder.itemDirectionBadge.setTextColor(Color.parseColor("#16A34A"))
            }
            "OUTGOING" -> {
                holder.itemDirectionBadge.text = "OUTGOING"
                holder.itemDirectionBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEE2E2"))
                holder.itemDirectionBadge.setTextColor(Color.parseColor("#DC2626"))
            }
            else -> {
                holder.itemDirectionBadge.text = "OTHER"
                holder.itemDirectionBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F1F5F9"))
                holder.itemDirectionBadge.setTextColor(Color.parseColor("#475569"))
            }
        }

        // Forwarding Badge
        when (item.forwardingStatus) {
            "YES" -> {
                holder.itemForwardedBadge.text = "Forwarded: YES"
                holder.itemForwardedBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#DCFCE7"))
                holder.itemForwardedBadge.setTextColor(Color.parseColor("#16A34A"))
            }
            "PENDING_RETRY" -> {
                holder.itemForwardedBadge.text = "Retrying (${item.retryCount})"
                holder.itemForwardedBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEF3C7"))
                holder.itemForwardedBadge.setTextColor(Color.parseColor("#D97706"))
            }
            "IGNORED" -> {
                holder.itemForwardedBadge.text = "Ignored"
                holder.itemForwardedBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F1F5F9"))
                holder.itemForwardedBadge.setTextColor(Color.parseColor("#475569"))
            }
            else -> {
                holder.itemForwardedBadge.text = "Forwarded: NO"
                holder.itemForwardedBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEE2E2"))
                holder.itemForwardedBadge.setTextColor(Color.parseColor("#DC2626"))
            }
        }

        // Sender & UTR
        val senderPart = if (item.sender != "Not available in notification") "Sender: ${item.sender}" else "Sender: N/A"
        val utrPart = if (item.utr != "Not available in notification") "Ref: ${item.utr}" else "Ref: N/A"
        holder.itemSenderAndUtr.text = "$senderPart | $utrPart"

        // Snippet
        val combinedSnippet = if (item.title.isNotBlank()) "${item.title}: ${item.text}" else item.text
        holder.itemRawSnippet.text = combinedSnippet

        // Server Status
        holder.itemServerStatus.text = "Server: ${item.serverStatus}"

        holder.itemView.setOnClickListener {
            if (onItemClick != null) {
                onItemClick.invoke(item)
            } else {
                showDetailDialog(context, item)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<TransactionRecord>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    private fun showDetailDialog(context: Context, item: TransactionRecord) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_transaction_detail, null)
        val dialog = AlertDialog.Builder(context).setView(dialogView).create()

        dialogView.findViewById<TextView>(R.id.diagTimestamp).text =
            "Timestamp: " + dateFormatter.format(Date(item.timestamp))
        dialogView.findViewById<TextView>(R.id.diagAppAndPkg).text =
            "${if (item.appName.isNotBlank()) item.appName else "App"} (${item.packageName})"
        dialogView.findViewById<TextView>(R.id.diagTitle).text =
            if (item.title.isNotBlank()) item.title else "(No title)"
        dialogView.findViewById<TextView>(R.id.diagFullText).text =
            if (item.text.isNotBlank()) item.text else "(No body text)"
        dialogView.findViewById<TextView>(R.id.diagAmountInfo).text =
            "${item.amount} (${item.amountPaise} paise)"
        dialogView.findViewById<TextView>(R.id.diagPattern).text =
            if (item.matchedPattern.isNotBlank()) "Matched Pattern: ${item.matchedPattern}" else "No pattern match"
        dialogView.findViewById<TextView>(R.id.diagDirection).text =
            "${item.classification} (isPayment = ${item.isPayment})"
        dialogView.findViewById<TextView>(R.id.diagSenderUtr).text =
            "Sender: ${item.sender}\nUPI Ref / UTR: ${item.utr}"
        dialogView.findViewById<TextView>(R.id.diagForwardResult).text =
            "${item.forwardingStatus} (${item.serverStatus})"
        dialogView.findViewById<TextView>(R.id.diagResponseBody).text =
            if (item.serverResponseBody.isNotBlank()) item.serverResponseBody else "(No response body)"

        dialogView.findViewById<Button>(R.id.btnCloseDiag).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}
