package pm.gnosis.heimdall.data.db.models

import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey
import pm.gnosis.model.Solidity

@Entity(tableName = ERC20TokenDb.TABLE_NAME)
data class ERC20TokenDb(
    @PrimaryKey
    @ColumnInfo(name = COL_ADDRESS)
    var address: Solidity.Address,

    @ColumnInfo(name = COL_NAME)
    var name: String,

    @ColumnInfo(name = COL_SYMBOL)
    var symbol: String,

    @ColumnInfo(name = COL_DECIMALS)
    var decimals: Int,

    @ColumnInfo(name = COL_LOGO_URL)
    var logoUrl: String
) {
    companion object {
        const val TABLE_NAME = "erc20_tokens"
        const val COL_ADDRESS = "address"
        const val COL_NAME = "name"
        const val COL_SYMBOL = "symbol"
        const val COL_DECIMALS = "decimals"
        const val COL_LOGO_URL = "logoUrl"
    }
}
