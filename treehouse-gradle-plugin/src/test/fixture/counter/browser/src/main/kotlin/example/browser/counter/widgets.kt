package example.browser.counter

import example.counter.widget.CounterBox
import example.counter.widget.CounterButton
import example.counter.widget.CounterText
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLSpanElement

class HtmlSunspotBox(
  override val value: HTMLElement,
) : SunspotBox<HTMLElement> {
  override val children = HTMLElementChildren(value)
}

class HtmlSunspotText(
  override val value: HTMLSpanElement,
) : SunspotText<HTMLElement> {
  override fun text(text: String?) {
    value.textContent = text
  }

  override fun color(color: String) {
    value.style.color = color
  }
}

class HtmlSunspotButton(
  override val value: HTMLButtonElement,
) : SunspotButton<HTMLElement> {
  override fun text(text: String?) {
    value.textContent = text
  }

  override fun enabled(enabled: Boolean) {
    value.disabled = !enabled
  }

  override fun onClick(onClick: (() -> Unit)?) {
    value.onclick = if (onClick != null) {
      { onClick() }
    } else {
      null
    }
  }
}
