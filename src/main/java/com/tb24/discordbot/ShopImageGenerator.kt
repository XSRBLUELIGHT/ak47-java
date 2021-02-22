package com.tb24.discordbot

import com.tb24.discordbot.managers.CatalogManager
import com.tb24.discordbot.util.*
import com.tb24.fn.EpicApi
import com.tb24.fn.model.EItemShopTileSize
import com.tb24.fn.model.FortCmsData
import com.tb24.fn.model.gamesubcatalog.CatalogDownload
import com.tb24.fn.model.gamesubcatalog.CatalogOffer
import com.tb24.fn.util.Formatters
import com.tb24.fn.util.format
import com.tb24.fn.util.getPreviewImagePath
import com.tb24.uasset.AssetManager
import com.tb24.uasset.loadObject
import me.fungames.jfortniteparse.fort.enums.EFortRarity
import me.fungames.jfortniteparse.fort.exports.CatalogMessaging
import me.fungames.jfortniteparse.fort.exports.FortMtxOfferData
import me.fungames.jfortniteparse.fort.exports.FortRarityData
import me.fungames.jfortniteparse.fort.exports.FortShopOfferDisplayData
import me.fungames.jfortniteparse.fort.objects.FortColorPalette
import me.fungames.jfortniteparse.ue4.assets.exports.mats.UMaterialInstanceConstant
import me.fungames.jfortniteparse.ue4.assets.exports.tex.UTexture2D
import me.fungames.jfortniteparse.ue4.converters.textures.toBufferedImage
import me.fungames.jfortniteparse.ue4.objects.core.math.FVector2D
import me.fungames.jfortniteparse.util.drawCenteredString
import me.fungames.jfortniteparse.util.toPngArray
import okhttp3.OkHttpClient
import okhttp3.Request
import java.awt.*
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileReader
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.system.exitProcess

fun main() {
	AssetManager.INSTANCE.loadPaks()
	File("out.png").writeBytes(generateShopImage().toPngArray())
	exitProcess(0)
}

fun generateShopImage(): BufferedImage {
	val rarityData = loadObject<FortRarityData>("/Game/Balance/RarityData.RarityData")!!
	val catalogManager = CatalogManager()
	catalogManager.catalogData = FileReader("D:\\Downloads\\shop-25-12-2020-en.json").use { EpicApi.GSON.fromJson(it, CatalogDownload::class.java) }
	catalogManager.sectionsData = OkHttpClient().newCall(Request.Builder().url("https://fortnitecontent-website-prod07.ol.epicgames.com/content/api/pages/fortnite-game/shop-sections").build()).exec().to<FortCmsData.ShopSectionsData>()
	catalogManager.validate()
	val itemSpacingH = 24f
	val itemSpacingV = 24f
	val sectionSpacing = 72f
	val normalTileW = 318f
	val normalTileH = 551f
	val sectionMarginH = 40f
	val sectionMarginV = 40f
	val tileSizes = mapOf(
		EItemShopTileSize.Mini to FVector2D(normalTileW, normalTileH / 3f - itemSpacingV / 1.5f),
		EItemShopTileSize.Small to FVector2D(normalTileW, normalTileH / 2f - itemSpacingV / 2f),
		EItemShopTileSize.Normal to FVector2D(normalTileW, normalTileH),
		EItemShopTileSize.DoubleWide to FVector2D(normalTileW * 2f + itemSpacingH, normalTileH),
		EItemShopTileSize.TripleWide to FVector2D(normalTileW * 3f + itemSpacingH, normalTileH)
	)

	var imageW = 0f
	var imageH = 0f

	val violatorPalettes = mapOf(
		EViolatorIntensity.High to FViolatorColorPalette(0xFFFFFF, 0xFFFF00, 0x00062B),
		EViolatorIntensity.Low to FViolatorColorPalette(0xFF2C78, 0xCF0067, 0xFFFFFF),
		// medium is not implemented
	)

	val catalogMessages = loadObject<CatalogMessaging>("/Game/Athena/UI/Frontend/CatalogMessages.CatalogMessages")!!
	val sectionsToDisplay = if (true) catalogManager.athenaSections.values.filter { it.items.isNotEmpty() && it.sectionData.sectionId != "LimitedTime" } else listOf(catalogManager.athenaSections.values.filter { it.items.isNotEmpty() }.first())
	val titleFont = ResourcesContext.burbankBigRegularBlack.deriveFont(Font.ITALIC, 160f)
	val titleText = "ITEM SHOP"
	val sectionContainers = mutableListOf<FShopSectionContainer>()

	// region Measure & Layout
	val dummyFrc = FontRenderContext(AffineTransform(), true, true)
	val titleBounds = titleFont.getStringBounds(titleText, dummyFrc)
	val titleHeight = titleBounds.height.toFloat()

	for ((i, section) in sectionsToDisplay.withIndex()) {
		val rowStartY = titleHeight + i * (normalTileH + sectionSpacing)
		val layoutHelper = FShopLayoutHelper(EItemShopTileSize.Normal, mutableMapOf())
		var tileX = sectionMarginH
		var tileY = rowStartY
		val limit = normalTileH + itemSpacingV * 2 + rowStartY
		val sectionContainer = FShopSectionContainer(section).apply { x = tileX; y = tileY }
		sectionContainers.add(sectionContainer)

		for (entry in section.items) {
			val tileSize = EItemShopTileSize.valueOf(entry.getMeta("TileSize") ?: throw RuntimeException("No TileSize specified"))
			val currentTileSize = tileSizes[tileSize]!!
			val tileW = currentTileSize.x
			val tileH = currentTileSize.y
			if (tileY + tileH > limit) {
				tileY = rowStartY
				tileX += tileW + itemSpacingH
				layoutHelper.quants[tileSize] = 0
			}
			val entryContainer = FShopEntryContainer(entry, section).apply {
				x = tileX; y = tileY
				w = tileW; h = tileH
				this.tileSize = tileSize
			}
			sectionContainer.entries.add(entryContainer)
			val newQuant = layoutHelper.quants.getOrPut(tileSize) { 0 } + 1
			layoutHelper.quants[tileSize] = newQuant
			when (tileSize) {
				EItemShopTileSize.Mini ->
					if (layoutHelper.quants[tileSize]!! >= 3) {
						tileX += tileW + itemSpacingH
						tileY -= (tileH + itemSpacingV) * 2
						layoutHelper.quants[tileSize] = 0
					} else {
						tileY += tileH + itemSpacingV
					}
				EItemShopTileSize.Small ->
					if (layoutHelper.quants[tileSize]!! >= 2) {
						tileX += tileW + itemSpacingH
						tileY -= tileH + itemSpacingV
						layoutHelper.quants[tileSize] = 0
					} else {
						tileY += tileH + itemSpacingV
					}
				EItemShopTileSize.Normal -> tileX += tileW + itemSpacingH
				EItemShopTileSize.DoubleWide -> tileX += tileW + itemSpacingH
				EItemShopTileSize.TripleWide -> tileX += tileW + itemSpacingH
			}
			if (tileY < rowStartY) {
				tileY = rowStartY
			}
			layoutHelper.name = tileSize

			imageW = max(imageW, tileX - itemSpacingH)
			imageH = max(imageH, tileY + tileH)
		}
	}

	imageW += sectionMarginH
	imageH += sectionMarginV
	// endregion

	// region Draw
	return createAndDrawCanvas(imageW.toInt(), imageH.toInt()) { ctx ->
		// Background
		ctx.drawRadialGradient(0xFF099AFE, 0xFF0942B4, 0, 0, imageW.toInt(), imageH.toInt())

		// Title
		if (false) {
			ctx.color = Color.BLACK
			ctx.fillRect(0, 0, titleBounds.width.toInt(), titleBounds.height.toInt())
		}
		ctx.font = titleFont
		ctx.color = Color.WHITE
		ctx.drawCenteredString(titleText, imageW.toInt() / 2, ctx.fontMetrics.ascent)

		// Sections
		for (sectionContainer in sectionContainers) {
			val section = sectionContainer.section
			val sectionX = sectionContainer.x
			val sectionY = sectionContainer.y

			ctx.color = Color.WHITE
			ctx.font = ResourcesContext.burbankBigRegularBlack.deriveFont(Font.ITALIC, 40f)
			val sectionTitleText = section.sectionData.sectionDisplayName?.toUpperCase() ?: ""
			ctx.drawString(sectionTitleText, sectionX + 27, sectionY - 18)

			if (section.sectionData.bShowTimer) {
				ctx.color = 0x89F0FF.awtColor()
				val textWidth = ctx.fontMetrics.stringWidth(sectionTitleText)
				ctx.drawTimer((sectionX + 27 + textWidth + 5 + 6).toInt(), (sectionY - 46).toInt(), 32)
			}

			ctx.color = Color.BLACK
			for (offerContainer in sectionContainer.entries) {
				val offer = offerContainer.offer.holder()
				val tileSize = offerContainer.tileSize
				val tileX = offerContainer.x
				val tileY = offerContainer.y
				val tileW = offerContainer.w
				val tileH = offerContainer.h
				val firstItem = offer.ce.itemGrants.firstOrNull() ?: continue
				val xOffset = when {
					firstItem.primaryAssetType == "AthenaDance" -> tileSize.offsets().emoteX
					firstItem.primaryAssetType == "AthenaGlider" && tileSize == EItemShopTileSize.Small -> tileSize.offsets().gliderX
					else -> 0
				}
				val seriesExists = false
				firstItem.defData?.Series?.value?.let {
					// fuck the series bg
				}
				val endsWithIcon = false//imageLink.endsWith("icon.png")
				var multi = if (tileSize == EItemShopTileSize.Normal && endsWithIcon) 0.5f else 1.0f
				multi = if (tileSize == EItemShopTileSize.Small && firstItem.primaryAssetType != "AthenaDance" && firstItem.primaryAssetType != "AthenaItemWrap") 2f else multi
				multi = if (tileSize == EItemShopTileSize.Small && firstItem.primaryAssetType == "AthenaDance") 1.4f else multi
				multi = if (tileSize == EItemShopTileSize.Normal && firstItem.primaryAssetType == "AthenaDance" && !endsWithIcon) 1.1f else multi
				multi = if (tileSize == EItemShopTileSize.Small && firstItem.primaryAssetType == "AthenaCharacter" && endsWithIcon) 1.2f else multi
				multi = if (tileSize == EItemShopTileSize.Small && firstItem.primaryAssetType == "AthenaPickaxe" && endsWithIcon) 1f else multi
				multi = if (tileSize == EItemShopTileSize.Small && firstItem.primaryAssetType == "AthenaGlider" && endsWithIcon) 1f else multi
				val multi2 = multi * 2

				// TODO draw item img
				// background
				ctx.drawRadialGradient(
					0xFF40AFFF, 0xFF227FD5,
					tileX.toInt(), tileY.toInt(),
					tileW.toInt(), tileH.toInt()
				)

				// item image
				val itemImage = offerContainer.image
				if (itemImage != null) {
					val cropOffsetRatio = (1f - tileW / tileH) / 2f
					ctx.drawImage(itemImage,
						tileX.toInt(), tileY.toInt(),
						(tileX + tileW).toInt(), (tileY + tileH).toInt(),
						(cropOffsetRatio * itemImage.width).toInt(), 0,
						((1f - cropOffsetRatio) * itemImage.width).toInt(), itemImage.height,
						null
					)
				}

				val path = Path2D.Float()

				// rarity
				if (!offer.getMeta("HideRarityBorder").equals("true", true)) {
					val palette = rarityData.forRarity(firstItem.rarity)
					ctx.color = palette.Color1.toColor()
					path.moveTo(tileX, tileY + tileH - 72)
					path.lineTo(tileX + tileW, tileY + tileH - 82)
					path.lineTo(tileX + tileW, tileY + tileH - 74)
					path.lineTo(tileX, tileY + tileH - 67)
					path.closePath()
					ctx.fill(path)
				}

				// text bg
				ctx.color = 0x1E1E1E.awtColor()
				path.reset()
				path.moveTo(tileX, tileY + tileH - 67)
				path.lineTo(tileX + tileW, tileY + tileH - 74)
				path.lineTo(tileX + tileW, tileY + tileH)
				path.lineTo(tileX, tileY + tileH)
				path.closePath()
				ctx.fill(path)

				// bottom
				ctx.color = 0x0E0E0E.awtColor()
				path.reset()
				path.moveTo(tileX, tileY + tileH - 26)
				path.lineTo(tileX + tileW, tileY + tileH - 28)
				path.lineTo(tileX + tileW, tileY + tileH)
				path.lineTo(tileX, tileY + tileH)
				path.closePath()
				ctx.fill(path)

				offer.resolve()
				val priceNum = offer.price.basePrice
				val priceText = Formatters.num.format(priceNum)

				ctx.color = 0xA7B8BC.awtColor()
				ctx.font = ResourcesContext.burbankBigRegularBlack.deriveFont(Font.ITALIC, 16f)
				ctx.drawString(priceText, tileX + tileW - 8 - ctx.fontMetrics.stringWidth(priceText), tileY + tileH - 9)

				ctx.color = Color.WHITE
				ctx.font = ctx.font.deriveFont(Font.ITALIC, 20f)
				val entryTitleText = offerContainer.title?.toUpperCase().orEmpty()
				ctx.drawCenteredString(entryTitleText, (tileX + tileW / 2).toInt(), (tileY + tileH - 40).toInt())

				val violatorIntensity = runCatching { EViolatorIntensity.valueOf(offer.getMeta("ViolatorIntensity")!!) }.getOrNull()
				if (violatorIntensity != null) {
					check(violatorIntensity != EViolatorIntensity.Medium) {
						"medium is not implemented"
					}
					ctx.font = ResourcesContext.burbankSmallBold.deriveFont(16f)

					val violatorTag = offer.getMeta("ViolatorTag")
					val violatorText = (catalogMessages.StoreToast_Body[violatorTag]?.format() ?: violatorTag ?: "?!?!?!").toUpperCase()
					// yeah dynamic bundle later lets get the basic stuff first
					val xOffsetText = ctx.fontMetrics.stringWidth(violatorText)

					//outline
					ctx.color = violatorPalettes[violatorIntensity]!!.outline.awtColor()
					path.reset()
					path.moveTo(tileX - 12, tileY - 9)
					path.lineTo(tileX + 22 + xOffsetText, tileY - 12)
					path.lineTo(tileX + 14 + xOffsetText, tileY + 27)
					path.lineTo(tileX - 8, tileY + 26)
					path.closePath()
					ctx.fill(path)

					//inside
					ctx.color = violatorPalettes[violatorIntensity]!!.inside.awtColor()
					path.reset()
					path.moveTo(tileX - 6, tileY - 4)
					path.lineTo(tileX + 15 + xOffsetText, tileY - 6)
					path.lineTo(tileX + 9 + xOffsetText, tileY + 22)
					path.lineTo(tileX - 3, tileY + 21)
					path.closePath()
					ctx.fill(path)

					//text
					ctx.color = violatorPalettes[violatorIntensity]!!.text.awtColor()
					ctx.drawString(violatorText, (tileX - 6) + (tileX + 10 + xOffsetText - (tileX - 6)) / 2, tileY - 10 + (tileY + 26 - (tileY - 10)) / 2 + ctx.fontMetrics.ascent / 2)
				}
			}
		}
	}
	// endregion
}

fun Graphics2D.drawRadialGradient(innerColor: Number, outerColor: Number, x: Int, y: Int, w: Int, h: Int, fac: Float = .3f) {
	paint = RadialGradientPaint(
		Rectangle(x, y, w, h).apply { grow((w * fac).toInt(), (h * fac).toInt()) },
		floatArrayOf(0f, 1f),
		arrayOf(innerColor.awtColor(), outerColor.awtColor()),
		MultipleGradientPaint.CycleMethod.NO_CYCLE)
	fillRect(x, y, w, h)
}

fun Graphics2D.drawTimer(iconX: Int, iconY: Int, iconSize: Int = 28) {
	val fillColor = color.rgb and 0xFFFFFF
	val timerIcon = ImageIO.read(File("C:\\Users\\satri\\Desktop\\ui_timer_64x.png"))
	val tW = timerIcon.width
	val tH = timerIcon.height
	val pixels = timerIcon.getRGB(0, 0, tW, tH, null, 0, tW)
	val handPixels = IntArray(pixels.size)
	for ((i, it) in pixels.withIndex()) {
		var outAlpha = (it shr 16) and 0xFF // red channel: base
		outAlpha -= (it shr 8) and 0xFF // green channel: inner
		outAlpha = max(outAlpha, 0)
		pixels[i] = (outAlpha shl 24) or fillColor
		handPixels[i] = ((it and 0xFF) shl 24) or fillColor // blue channel: hand
	}
	val frame = BufferedImage(tW, tH, BufferedImage.TYPE_INT_ARGB)
	frame.setRGB(0, 0, tW, tH, pixels, 0, tW)
	val hand = BufferedImage(tW, tH, BufferedImage.TYPE_INT_ARGB)
	hand.setRGB(0, 0, tW, tH, handPixels, 0, tW)
	drawImage(frame, iconX, iconY, iconSize, iconSize, null)
	val saveT = transform
	val oX = 0.49 * iconSize
	val oY = 0.575 * iconSize
	val currentSecondsInHour = (System.currentTimeMillis() / 1000) % (60 * 60)
	rotate(Math.toRadians(currentSecondsInHour.toDouble() / (60 * 60) * 360), iconX + oX, iconY + oY)
	drawImage(hand, iconX, iconY, iconSize, iconSize, null)
	transform = saveT
}

class FViolatorColorPalette(val outline: Int, val inside: Int, val text: Int)

class FShopLayoutHelper(var name: EItemShopTileSize, val quants: MutableMap<EItemShopTileSize, Int>)

class FShopSectionContainer(val section: CatalogManager.ShopSection) {
	var x = 0f
	var y = 0f
	val entries = mutableListOf<FShopEntryContainer>()
}

class FShopEntryContainer(val offer: CatalogOffer, val section: CatalogManager.ShopSection) {
	var x = 0f
	var y = 0f
	var w = 0f
	var h = 0f
	var tileSize = EItemShopTileSize.Normal
	var image: BufferedImage? = null
	var title: String? = null
	var rarity: EFortRarity? = null

	init {
		offer.itemGrants.firstOrNull()?.let { item ->
			image = item.getPreviewImagePath()?.load<UTexture2D>()?.toBufferedImage()
			title = item.displayName
			rarity = item.defData.Rarity
		}
		if (!offer.displayAssetPath.isNullOrEmpty()) {
			loadObject<FortMtxOfferData>(offer.displayAssetPath)?.let {
				it.DisplayName?.run { title = format() }
			}
		}
		val newDisplayAssetPath = offer.getMeta("NewDisplayAssetPath")
		if (newDisplayAssetPath != null) {
			val newDisplayAsset = loadObject<FortShopOfferDisplayData>(newDisplayAssetPath)
			if (newDisplayAsset != null) {
				val firstPresentation = newDisplayAsset.Presentations.first().load<UMaterialInstanceConstant>()
				if (firstPresentation != null) {
					image = (firstPresentation.TextureParameterValues.first { it.ParameterInfo.Name.toString() == "OfferImage" }.ParameterValue.value as? UTexture2D)?.toBufferedImage()
					// TODO wen eta fancy gradient shit
				}
			}
		}
	}
}

fun FortRarityData.forRarity(rarity: EFortRarity): FortColorPalette {
	val h = RarityCollection[rarity.ordinal]
	return FortColorPalette().apply {
		Color1 = h.Color1
		Color2 = h.Color2
		Color3 = h.Color3
		Color4 = h.Color4
		Color5 = h.Color5
	}
}

enum class EViolatorIntensity {
	Low,
	Medium,
	High
}

class FOffsets(val emoteX: Int, val gliderX: Int)

fun EItemShopTileSize.offsets() = when (this) {
	EItemShopTileSize.Mini -> FOffsets(0, 0)
	EItemShopTileSize.Small -> FOffsets(43, 15)
	EItemShopTileSize.Normal -> FOffsets(30, 0)
	EItemShopTileSize.DoubleWide -> FOffsets(0, 0)
	EItemShopTileSize.TripleWide -> FOffsets(0, 0)
}