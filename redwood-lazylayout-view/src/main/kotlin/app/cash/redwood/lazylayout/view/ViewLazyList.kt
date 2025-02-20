/*
 * Copyright (C) 2022 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:Suppress("FunctionName")

package app.cash.redwood.lazylayout.view

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import androidx.core.view.doOnDetach
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePaddingRelative
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import app.cash.redwood.Modifier
import app.cash.redwood.layout.api.Constraint
import app.cash.redwood.layout.api.CrossAxisAlignment
import app.cash.redwood.lazylayout.api.ScrollItemIndex
import app.cash.redwood.lazylayout.widget.RefreshableLazyList
import app.cash.redwood.lazylayout.widget.WindowedChildren
import app.cash.redwood.lazylayout.widget.WindowedLazyList
import app.cash.redwood.ui.Density
import app.cash.redwood.ui.Margin
import app.cash.redwood.widget.Widget
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

private const val VIEW_TYPE_PLACEHOLDER = 1
private const val VIEW_TYPE_ITEM = 2

internal class Placeholders(
  private val recycledViewPool: RecyclerView.RecycledViewPool,
) : Widget.Children<View> {
  private var poolSize = 0
  private val pool = ArrayDeque<Widget<View>>()

  fun takeOrNull(): Widget<View>? = pool.removeLastOrNull()

  override fun insert(index: Int, widget: Widget<View>) {
    poolSize++
    pool += widget
    recycledViewPool.setMaxRecycledViews(VIEW_TYPE_PLACEHOLDER, poolSize)
  }

  override fun move(fromIndex: Int, toIndex: Int, count: Int) {}
  override fun remove(index: Int, count: Int) {}
  override fun onModifierUpdated() {}
}

internal open class ViewLazyList private constructor(
  internal val recyclerView: RecyclerView,
  override val placeholder: Placeholders = Placeholders(recyclerView.recycledViewPool),
  private val adapter: LazyContentItemListAdapter = LazyContentItemListAdapter(placeholder),
) : WindowedLazyList<View>(RecyclerViewAdapterListUpdateCallback(adapter)) {
  private val scope = MainScope()

  override var modifier: Modifier = Modifier

  private val density = Density(recyclerView.context.resources)
  private val linearLayoutManager = object : LinearLayoutManager(recyclerView.context) {
    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams? = when (orientation) {
      RecyclerView.HORIZONTAL -> RecyclerView.LayoutParams(WRAP_CONTENT, MATCH_PARENT)
      RecyclerView.VERTICAL -> RecyclerView.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
      else -> null
    }
  }

  override val value: View get() = recyclerView

  constructor(context: Context) : this(RecyclerView(context))

  init {
    adapter.items = items
    recyclerView.apply {
      layoutManager = linearLayoutManager
      layoutParams = ViewGroup.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)

      // TODO Dynamically set the max recycled views for VIEW_TYPE_ITEM
      recycledViewPool.setMaxRecycledViews(VIEW_TYPE_ITEM, 30)
      addOnScrollListener(
        object : RecyclerView.OnScrollListener() {
          override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            updateViewport(
              linearLayoutManager.findFirstVisibleItemPosition(),
              linearLayoutManager.findLastVisibleItemPosition(),
            )
          }
        },
      )

      doOnDetach {
        scope.cancel()
      }
    }
    recyclerView.adapter = adapter
  }

  override fun width(width: Constraint) {
    recyclerView.updateLayoutParams {
      this.width = if (width == Constraint.Fill) MATCH_PARENT else WRAP_CONTENT
    }
  }

  override fun height(height: Constraint) {
    recyclerView.updateLayoutParams {
      this.height = if (height == Constraint.Fill) MATCH_PARENT else WRAP_CONTENT
    }
  }

  override fun margin(margin: Margin) {
    with(density) {
      recyclerView.updatePaddingRelative(
        start = margin.start.toPx().toInt(),
        top = margin.top.toPx().toInt(),
        end = margin.end.toPx().toInt(),
        bottom = margin.bottom.toPx().toInt(),
      )
    }
  }

  override fun crossAxisAlignment(crossAxisAlignment: CrossAxisAlignment) {
    adapter.crossAxisAlignment = crossAxisAlignment
    adapter.notifyItemRangeChanged(0, adapter.itemCount)
  }

  override fun scrollItemIndex(scrollItemIndex: ScrollItemIndex) {
    recyclerView.scrollToPosition(scrollItemIndex.index)
  }

  override fun isVertical(isVertical: Boolean) {
    linearLayoutManager.orientation = if (isVertical) RecyclerView.VERTICAL else RecyclerView.HORIZONTAL
  }

  @SuppressLint("NotifyDataSetChanged")
  override fun itemsBefore(itemsBefore: Int) {
    items.itemsBefore = itemsBefore

    // TODO Replace notifyDataSetChanged with atomic change events
    //  notifyItemRangeInserted causes an onScrolled event to be emitted.
    //  This incorrectly updates the viewport, which then shifts the loaded items window.
    //  This then increases the value of itemsBefore,
    //  and the cycle continues until the backing dataset is exhausted.
    adapter.notifyDataSetChanged()
  }

  private class LazyContentItemListAdapter(
    val placeholders: Placeholders,
  ) : RecyclerView.Adapter<ViewHolder>() {
    var crossAxisAlignment = CrossAxisAlignment.Start
    lateinit var items: WindowedChildren<View>

    /**
     * When we haven't loaded enough placeholders for the viewport height, we set a blank view while
     * we load request more placeholders. This "meta" placeholder needs a non-zero height, so we
     * don't load an infinite number of zero height meta placeholders.
     *
     * We set this height to the last available item height, or a hardcoded value in the case when
     * no views have been laid out, but a meta placeholder has been requested.
     */
    private var lastItemHeight = 100
      set(value) {
        if (value > 0) {
          field = value
        }
      }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int {
      return if (items[position] != null) VIEW_TYPE_ITEM else VIEW_TYPE_PLACEHOLDER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
      val container = FrameLayout(parent.context)
      // [onBindViewHolder] is invoked before the default layout params are set, so
      // [View.getLayoutParams] will be null unless explicitly set.
      container.layoutParams = (parent as RecyclerView).layoutManager!!.generateDefaultLayoutParams()
      return when (viewType) {
        VIEW_TYPE_PLACEHOLDER -> ViewHolder.Placeholder(container)
        VIEW_TYPE_ITEM -> ViewHolder.Item(container)
        else -> error("Unrecognized viewType: $viewType")
      }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
      lastItemHeight = holder.itemView.height
      val layoutParams = if (crossAxisAlignment == CrossAxisAlignment.Stretch) {
        FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
      } else {
        FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
      }
      layoutParams.apply {
        gravity = when (crossAxisAlignment) {
          CrossAxisAlignment.Start -> Gravity.START
          CrossAxisAlignment.Center -> Gravity.CENTER
          CrossAxisAlignment.End -> Gravity.END
          CrossAxisAlignment.Stretch -> Gravity.START
          else -> throw AssertionError()
        }
      }
      when (holder) {
        is ViewHolder.Placeholder -> {
          if (holder.container.childCount == 0) {
            val placeholder = placeholders.takeOrNull()
            if (placeholder != null) {
              placeholder.value.layoutParams = layoutParams
              holder.container.addView(placeholder.value)
              holder.itemView.updateLayoutParams { height = WRAP_CONTENT }
            } else if (holder.container.height == 0) {
              // This occurs when the ViewHolder has been freshly created, so we set the container
              // to a non-zero height so that it's visible.
              holder.itemView.updateLayoutParams { height = lastItemHeight }
            }
          } else {
            holder.container.getChildAt(0).layoutParams = layoutParams
          }
        }
        is ViewHolder.Item -> {
          val view = items[position]!!.value
          holder.container.removeAllViews()
          (view.parent as? FrameLayout)?.removeAllViews()
          view.layoutParams = layoutParams
          holder.container.addView(view)
        }
      }
    }
  }

  sealed class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    class Placeholder(val container: FrameLayout) : ViewHolder(container)
    class Item(val container: FrameLayout) : ViewHolder(container)
  }
}

internal class ViewRefreshableLazyList(
  context: Context,
) : ViewLazyList(context), RefreshableLazyList<View> {

  private val swipeRefreshLayout = SwipeRefreshLayout(context)

  override val value: View get() = swipeRefreshLayout

  init {
    swipeRefreshLayout.apply {
      addView(recyclerView)
      // TODO Dynamically update width and height of RefreshableViewLazyList when set
      layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
    }
  }

  override fun refreshing(refreshing: Boolean) {
    swipeRefreshLayout.isRefreshing = refreshing
  }

  override fun onRefresh(onRefresh: (() -> Unit)?) {
    swipeRefreshLayout.isEnabled = onRefresh != null
    swipeRefreshLayout.setOnRefreshListener(onRefresh)
  }

  override fun pullRefreshContentColor(@ColorInt pullRefreshContentColor: UInt) {
    swipeRefreshLayout.setColorSchemeColors(pullRefreshContentColor.toInt())
  }
}
