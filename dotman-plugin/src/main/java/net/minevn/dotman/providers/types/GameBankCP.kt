package net.minevn.dotman.providers.types

import net.minevn.dotman.DotMan
import net.minevn.dotman.card.Card
import net.minevn.dotman.card.CardResult
import net.minevn.dotman.card.CardType
import net.minevn.dotman.database.LogDAO
import net.minevn.dotman.providers.CardProvider
import net.minevn.dotman.utils.Utils.Companion.warning
import net.minevn.libs.asIntOrNull
import net.minevn.libs.asStringOrNull
import net.minevn.libs.get
import net.minevn.libs.gson.JsonParser
import net.minevn.libs.parseJson
import java.io.IOException
import java.net.Authenticator
import java.net.PasswordAuthentication

open class GameBankCP(
    private val merchantId: Int,
    private val apiUser: String,
    private val apiPassword: String
) : CardProvider() {

    override fun getApiUrl() = "https://sv.gamebank.vn/api/card"
    override fun getStatusUrl() = "https://sv.gamebank.vn/trang-thai-he-thong-2"


    override fun getRequestParameters(playerName: String, card: Card) = mapOf(
        "merchant_id" to merchantId.toString(),
        "pin" to card.pin,
        "seri" to card.seri,
        "card_type" to card.type.getTypeId().toString(),
        "price_guest" to card.price.value.toString(),
        "note" to "$playerName from ${DotMan.instance.config.server}"
    )

    override fun getRequestHeaders(playerName: String, card: Card): Map<String, String>? {
        Authenticator.setDefault(object : Authenticator() {
            override fun getPasswordAuthentication() = PasswordAuthentication(apiUser, apiPassword.toCharArray())
        })
        return null
    }

    override fun parseResponse(card: Card, response: String) = CardResult(card).apply {
        response.parseJson().asJsonObject.let {
            isSuccess = it["code"].asStringOrNull() == "0"
            acceptedAmount = it["info_card"].asIntOrNull()
            message = it["msg"].asStringOrNull()

            // Cập nhật mã giao dịch
            it["transaction_id"].asStringOrNull()?.let { transactionId ->
                LogDAO.getInstance().setTransactionId(card.logId!!, transactionId, isSuccess)
            }
        }
    }

    @Throws(IOException::class)
    fun getStatusString() = get(getStatusUrl())

    fun getStatusSet(statusJson: String) = JsonParser.parseString(statusJson)
        .asJsonArray[0]!!
        .asJsonObject
        .asMap()
        .entries
        .filter { CardType[it.key] != null }
        .associate { CardType[it.key]!! to (it.value.asString == "1") }

    override fun updateStatus() {
        try {
            statusCards = getStatusSet(getStatusString())
        } catch (ex: IOException) {
            warning("Không thể cập nhật trạng thái thẻ: ${getStatusUrl()}")
        }
    }

    private fun CardType.getTypeId() = when (this) {
        CardType.VIETTEL -> 1
        CardType.MOBIFONE -> 2
        CardType.VINAPHONE -> 3
        CardType.GATE -> 4
        CardType.VIETNAMOBILE -> 6
        CardType.ZING -> 7
        CardType.GARENA -> 8
        CardType.ONCASH -> 9
        else -> -1
    }
}
