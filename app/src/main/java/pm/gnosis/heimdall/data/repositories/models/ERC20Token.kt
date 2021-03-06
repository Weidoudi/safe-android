package pm.gnosis.heimdall.data.repositories.models

import pm.gnosis.heimdall.data.db.models.ERC20TokenDb
import pm.gnosis.model.Solidity
import pm.gnosis.utils.stringWithNoTrailingZeroes
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

data class ERC20Token(
    val address: Solidity.Address,
    val name: String,
    val symbol: String,
    val decimals: Int,
    val logoUrl: String = "",
    val displayDecimals: Int = decimals
) {
    fun convertAmount(unscaledAmount: BigInteger): BigDecimal =
        BigDecimal(unscaledAmount).setScale(decimals).div(BigDecimal.TEN.pow(decimals))

    fun displayString(amount: BigInteger, decimalsToDisplay: Int = displayDecimals) =
        "${convertAmount(amount).setScale(decimalsToDisplay, RoundingMode.UP).stringWithNoTrailingZeroes()} $symbol"

    companion object {
        val ETHER_TOKEN = ERC20Token(Solidity.Address(BigInteger.ZERO), decimals = 18, symbol = "ETH", name = "Ether", displayDecimals = 5)
    }
}

data class ERC20TokenWithBalance(val token: ERC20Token, val balance: BigInteger? = null) {
    fun displayString() =
        balance?.let {
            token.displayString(it)
        } ?: "-"
}

fun ERC20Token.toDb() = ERC20TokenDb(address, name, symbol, decimals, logoUrl)
fun ERC20TokenDb.fromDb() = ERC20Token(address, name, symbol, decimals, logoUrl)
