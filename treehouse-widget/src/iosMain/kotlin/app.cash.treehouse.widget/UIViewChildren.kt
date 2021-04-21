package app.cash.treehouse.widget

import app.cash.treehouse.widget.Widget.Children.Companion.validateInsert
import app.cash.treehouse.widget.Widget.Children.Companion.validateMove
import app.cash.treehouse.widget.Widget.Children.Companion.validateRemove
import kotlinx.cinterop.convert
import platform.UIKit.UIView
import platform.UIKit.insertSubview
import platform.UIKit.removeFromSuperview
import platform.UIKit.subviews
import platform.darwin.NSInteger

class UIViewChildren(
  private val root: UIView,
) : Widget.Children<UIView> {
  override fun insert(index: Int, widget: UIView) {
    validateInsert(root.subviews.size, index)

    root.insertSubview(widget, index.convert<NSInteger>())
  }

  override fun move(fromIndex: Int, toIndex: Int, count: Int) {
    validateMove(root.subviews.size, fromIndex, toIndex, count)

    val views = Array(count) {
      val subview = root.subviews[fromIndex] as UIView
      subview.removeFromSuperview()
      subview
    }

    val newIndex = if (toIndex > fromIndex) {
      toIndex - count
    } else {
      toIndex
    }
    views.forEachIndexed { offset, view ->
      root.insertSubview(view, (newIndex + offset).convert<NSInteger>())
    }
  }

  override fun remove(index: Int, count: Int) {
    validateRemove(root.subviews.size, index, count)

    repeat(count) {
      (root.subviews[index] as UIView).removeFromSuperview()
    }
  }

  override fun clear() {
    for (subview in root.subviews) {
      (subview as UIView).removeFromSuperview()
    }
  }
}
