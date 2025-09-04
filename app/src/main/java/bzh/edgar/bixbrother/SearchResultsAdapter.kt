package bzh.edgar.bixbrother

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.Disposable
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.core.net.toUri
import androidx.recyclerview.widget.DiffUtil

private const val ITEM_VIEW_TYPE_HEADER = 0
private const val ITEM_VIEW_TYPE_RESULT = 1

class SearchResultsAdapter(private val activity: ComponentActivity) : RecyclerView.Adapter<SearchResultsAdapter.ViewHolder>() {
    var items = listOf<Station>()
        set(newValue) {
            val oldValue = field
            val result = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize() = oldValue.size + 1
                override fun getNewListSize() = newValue.size + 1

                override fun areItemsTheSame(
                    oldItemPosition: Int,
                    newItemPosition: Int
                ) = if (oldItemPosition == 0 || newItemPosition == 0) {
                    oldItemPosition == 0 && newItemPosition == 0
                } else {
                    oldValue[oldItemPosition - 1] == newValue[newItemPosition - 1]
                }

                override fun areContentsTheSame(
                    oldItemPosition: Int,
                    newItemPosition: Int
                ) = true
            }, false)
            field = newValue
            result.dispatchUpdatesTo(this)
        }

    var onClickListener: ((Station) -> Unit)? = null

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ) = if (viewType == ITEM_VIEW_TYPE_HEADER) {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.widget_configure_search_header, parent, false)
        HeaderViewHolder(view)
    } else {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.widget_configure_search_entry, parent, false)
        ResultViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) = if (position > 0) {
        (holder as ResultViewHolder).bind(items[position - 1])
    } else {}

    override fun getItemViewType(position: Int) = if (position == 0) {
        ITEM_VIEW_TYPE_HEADER
    } else {
        ITEM_VIEW_TYPE_RESULT
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.unbind()
    }

    override fun getItemCount() = items.size + 1

    abstract class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        open fun unbind() {}
    }

    class HeaderViewHolder(itemView: View) : ViewHolder(itemView)

    inner class ResultViewHolder(itemView: View) : ViewHolder(itemView) {
        val thumbnailView = itemView.findViewById<ShapeableImageView>(R.id.search_result_thumbnail)!!
        val nameView = itemView.findViewById<TextView>(R.id.search_result_name)!!

        var thumbnailJob: Job? = null
        var thumbnailDispose: Disposable? = null

        val currentStation get() = items[adapterPosition - 1]

        init {
            itemView.setOnClickListener {
                onClickListener?.let { it -> it(currentStation) }
            }
            itemView.setOnCreateContextMenuListener { menu, view, info ->
                activity.menuInflater.inflate(R.menu.search_entry, menu)
                menu.findItem(R.id.menu_item_copy).setOnMenuItemClickListener {
                    val station = currentStation
                    val clipboard = activity.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText(station.name, station.externalId.toString())
                    clipboard.setPrimaryClip(clip)
                    true
                }
                menu.findItem(R.id.menu_item_open_map).setOnMenuItemClickListener {
                    val station = currentStation
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.data = "geo:0,0?u=5&q=${station.lat},${station.lon}(Bixi)".toUri()
                    activity.startActivity(intent)
                    true
                }
            }
        }

        fun bind(station: Station) {
            nameView.text = station.name

            thumbnailJob = activity.lifecycleScope.launch {
                val thumbUrl = itemView.context.bixApp.apiClient.getStationThumbnail(station.externalId)
                thumbnailDispose = thumbnailView.load(thumbUrl)
            }
        }

        override fun unbind() {
            thumbnailJob?.cancel()
            thumbnailDispose?.dispose()
            thumbnailView.setImageDrawable(null)
        }
    }
}
