package com.monekx.curfewnotifier.data

import org.simpleframework.xml.Element
import org.simpleframework.xml.ElementList
import org.simpleframework.xml.Root

// Модель для элемента <item> в RSS-ленте
@Root(name = "item", strict = false)
data class RssItem @JvmOverloads constructor(
    @field:Element(name = "title", required = false)
    @param:Element(name = "title", required = false)
    var title: String? = null,

    @field:Element(name = "link", required = false)
    @param:Element(name = "link", required = false)
    var link: String? = null,

    @field:Element(name = "description", required = false)
    @param:Element(name = "description", required = false)
    var description: String? = null,

    @field:Element(name = "pubDate", required = false)
    @param:Element(name = "pubDate", required = false)
    var pubDate: String? = null
)

// Модель для элемента <channel> в RSS-ленте
@Root(name = "channel", strict = false)
data class RssChannel @JvmOverloads constructor(
    @field:Element(name = "title", required = false)
    @param:Element(name = "title", required = false)
    var title: String? = null,

    @field:Element(name = "link", required = false)
    @param:Element(name = "link", required = false)
    var link: String? = null,

    @field:Element(name = "description", required = false)
    @param:Element(name = "description", required = false)
    var description: String? = null,

    @field:ElementList(inline = true, name = "item", required = false)
    @param:ElementList(inline = true, name = "item", required = false)
    var items: List<RssItem>? = null
)

// Модель для корневого элемента <rss>
@Root(name = "rss", strict = false)
data class RssFeed @JvmOverloads constructor(
    @field:Element(name = "channel", required = false)
    @param:Element(name = "channel", required = false)
    var channel: RssChannel? = null
)