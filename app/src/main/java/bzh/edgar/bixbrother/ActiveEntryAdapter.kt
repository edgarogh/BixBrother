package bzh.edgar.bixbrother

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import java.util.Collections

class ActiveEntryAdapter(private val touchHelper: ItemTouchHelper) : RecyclerView.Adapter<ActiveEntryAdapter.ViewHolder>() {
    var items = mutableListOf<Station>()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.widget_configure_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    fun onRowMoved(fromPos: Int, toPos: Int) {
        if (fromPos < toPos) {
            for (i in fromPos..<toPos) {
                Collections.swap(items, i, i + 1)
            }
        } else {
            for (i in fromPos downTo toPos + 1) {
                Collections.swap(items, i, i - 1)
            }
        }
        notifyItemMoved(fromPos, toPos)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameView = itemView.findViewById<TextView>(R.id.active_entry_name)!!
        val handleView = itemView.findViewById<LinearLayout>(R.id.active_entry_handle)!!

        var boundStation: Station? = null

        // TODO(accessibility): add custom accessibility actions to move up and down
        @SuppressLint("ClickableViewAccessibility")
        fun bind(station: Station) {
            boundStation = station
            nameView.text = station.name
            handleView.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    touchHelper.startDrag(this)
                }
                return@setOnTouchListener false
            }
        }
    }
}
