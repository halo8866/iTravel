package com.itravel.app.map

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex

/**
 * 高德地图路网瓦片源（无需 API Key，国内可直连，加载快）。
 * 用于替代默认的 OpenStreetMap，避免国内网络访问慢导致地图空白。
 */
object GaodeRoadTileSource : OnlineTileSourceBase(
    "gaode-road",
    1,
    19,
    256,
    ".png",
    arrayOf(
        "https://webrd01.is.autonavi.com",
        "https://webrd02.is.autonavi.com",
        "https://webrd03.is.autonavi.com",
        "https://webrd04.is.autonavi.com"
    ),
    "iTravel"
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        val z = MapTileIndex.getZoom(pMapTileIndex)
        val server = ((x + y) % 4) + 1
        return "https://webrd0$server.is.autonavi.com/appmaptile" +
                "?lang=zh_cn&size=1&scale=1&style=8&x=$x&y=$y&z=$z"
    }
}
