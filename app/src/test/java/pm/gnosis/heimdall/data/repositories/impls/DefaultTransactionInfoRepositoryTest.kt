package pm.gnosis.heimdall.data.repositories.impls

import io.reactivex.Single
import io.reactivex.functions.Predicate
import io.reactivex.observers.TestObserver
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import pm.gnosis.heimdall.ERC20Contract
import pm.gnosis.heimdall.GnosisSafePersonalEdition
import pm.gnosis.heimdall.data.db.ApplicationDb
import pm.gnosis.heimdall.data.db.daos.DescriptionsDao
import pm.gnosis.heimdall.data.db.models.TransactionDescriptionDb
import pm.gnosis.heimdall.data.db.models.TransactionPublishStatusDb
import pm.gnosis.heimdall.data.repositories.RestrictedTransactionException
import pm.gnosis.heimdall.data.repositories.TransactionData
import pm.gnosis.heimdall.data.repositories.TransactionExecutionRepository.Operation.CALL
import pm.gnosis.heimdall.data.repositories.TransactionExecutionRepository.Operation.DELEGATE_CALL
import pm.gnosis.heimdall.data.repositories.TransactionInfo
import pm.gnosis.heimdall.data.repositories.models.ERC20Token
import pm.gnosis.heimdall.data.repositories.models.SafeTransaction
import pm.gnosis.heimdall.data.repositories.toInt
import pm.gnosis.model.Solidity
import pm.gnosis.models.Transaction
import pm.gnosis.models.Wei
import pm.gnosis.tests.utils.ImmediateSchedulersRule
import pm.gnosis.utils.asEthereumAddress
import java.math.BigInteger
import java.util.*

@RunWith(MockitoJUnitRunner::class)
class DefaultTransactionInfoRepositoryTest {

    @JvmField
    @Rule
    val rule = ImmediateSchedulersRule()

    @Mock
    lateinit var dbMock: ApplicationDb

    @Mock
    lateinit var descriptionsDaoMock: DescriptionsDao

    lateinit var repository: DefaultTransactionInfoRepository

    @Before
    fun setUp() {
        given(dbMock.descriptionsDao()).willReturn(descriptionsDaoMock)
        repository = DefaultTransactionInfoRepository(dbMock)
    }

    @Test
    fun checkRestrictedTransaction() {
        val testData = mapOf(
            RestrictedTransactionException.ChangeThreshold::class to (
                    RestrictedTransactionException.ChangeThreshold to
                            listOf(
                                SafeTransaction(
                                    Transaction(
                                        address = "0x0".asEthereumAddress()!!,
                                        data = GnosisSafePersonalEdition.ChangeThreshold.encode(Solidity.UInt8(BigInteger.ONE))
                                    ), CALL
                                )
                            )
                    ),
            RestrictedTransactionException.ChangeMasterCopy::class to (
                    RestrictedTransactionException.ChangeMasterCopy to
                            listOf(
                                SafeTransaction(
                                    Transaction(
                                        address = "0x0".asEthereumAddress()!!,
                                        data = GnosisSafePersonalEdition.ChangeMasterCopy.encode("0x1".asEthereumAddress()!!)
                                    ), CALL
                                )
                            )
                    ),
            RestrictedTransactionException.ModifyOwners::class to (
                    RestrictedTransactionException.ModifyOwners to
                            listOf(
                                SafeTransaction(
                                    Transaction(
                                        address = "0x0".asEthereumAddress()!!,
                                        data = GnosisSafePersonalEdition.AddOwnerWithThreshold.encode(
                                            "0x1".asEthereumAddress()!!,
                                            Solidity.UInt8(BigInteger.ZERO)
                                        )
                                    ), CALL
                                ),
                                SafeTransaction(
                                    Transaction(
                                        address = "0x0".asEthereumAddress()!!,
                                        data = GnosisSafePersonalEdition.SwapOwner.encode(
                                            "0x1".asEthereumAddress()!!,
                                            "0x2".asEthereumAddress()!!,
                                            "0x3".asEthereumAddress()!!
                                        )
                                    ), CALL
                                ),
                                SafeTransaction(
                                    Transaction(
                                        address = "0x0".asEthereumAddress()!!,
                                        data = GnosisSafePersonalEdition.RemoveOwner.encode(
                                            "0x1".asEthereumAddress()!!,
                                            "0x2".asEthereumAddress()!!,
                                            Solidity.UInt8(BigInteger.ZERO)
                                        )
                                    ), CALL
                                )
                            )
                    ),
            RestrictedTransactionException.ModifyModules::class to (
                    RestrictedTransactionException.ModifyModules to
                            listOf(
                                SafeTransaction(
                                    Transaction(
                                        address = "0x0".asEthereumAddress()!!,
                                        data = GnosisSafePersonalEdition.EnableModule.encode(
                                            "0x1".asEthereumAddress()!!
                                        )
                                    ), CALL
                                ),
                                SafeTransaction(
                                    Transaction(
                                        address = "0x0".asEthereumAddress()!!,
                                        data = GnosisSafePersonalEdition.DisableModule.encode(
                                            "0x1".asEthereumAddress()!!,
                                            "0x2".asEthereumAddress()!!
                                        )
                                    ), CALL
                                )
                            )
                    ),
            RestrictedTransactionException.DelegateCall::class to (
                    RestrictedTransactionException.DelegateCall to
                            listOf(
                                SafeTransaction(
                                    Transaction(
                                        address = "0x0".asEthereumAddress()!!,
                                        value = Wei.ether("12")
                                    ), DELEGATE_CALL
                                ),
                                SafeTransaction(
                                    Transaction(
                                        address = "0x0".asEthereumAddress()!!,
                                        data = ERC20Contract.Transfer.encode(
                                            "0x1".asEthereumAddress()!!,
                                            Solidity.UInt256(BigInteger.ONE)
                                        )
                                    ), DELEGATE_CALL
                                )
                            )
                    )
        )
        RestrictedTransactionException::class.nestedClasses.forEach {
            val (expected, tests) = testData[it] ?: throw IllegalStateException("Missing tests for ${it.simpleName}")
            if (tests.isEmpty()) throw IllegalStateException("Missing tests for ${it.simpleName}")
            tests.forEach {
                val testObserver = TestObserver<SafeTransaction>()
                repository.checkRestrictedTransaction(it).subscribe(testObserver)
                testObserver.assertFailure(Predicate { it == expected })
            }
        }

        listOf(
            SafeTransaction(
                Transaction(
                    address = "0x0".asEthereumAddress()!!,
                    value = Wei.ether("12")
                ), CALL
            ),
            SafeTransaction(
                Transaction(
                    address = "0x0".asEthereumAddress()!!,
                    data = ERC20Contract.Transfer.encode(
                        "0x1".asEthereumAddress()!!,
                        Solidity.UInt256(BigInteger.ONE)
                    )
                ), CALL
            )
        ).forEach {
            val validObserver = TestObserver<SafeTransaction>()
            repository.checkRestrictedTransaction(it).subscribe(validObserver)
            validObserver.assertResult(it)
        }
    }

    @Test
    fun parseTransactionData() {
        TransactionData::class.nestedClasses.filter { it != TransactionData.Companion::class }.forEach { klass ->
            TEST_DATA_TRANSACTION_INFO[klass]?.let { tests ->
                if (tests.isEmpty()) throw IllegalStateException("Missing tests for ${klass.simpleName}")
                tests.forEach {
                    val testObserver = TestObserver<TransactionData>()
                    repository.parseTransactionData(it.transaction).subscribe(testObserver)
                    testObserver.assertResult(it.expected)
                }
            } ?: throw IllegalStateException("Missing tests for ${klass.simpleName}")
        }
    }

    @Test
    fun loadTransactionInfo() {
        TransactionData::class.nestedClasses.filter { it != TransactionData.Companion::class }.forEach { klass ->
            TEST_DATA_TRANSACTION_INFO[klass]?.let { tests ->
                if (tests.isEmpty()) throw IllegalStateException("Missing tests for ${klass.simpleName}")
                tests.forEach {
                    val testId = UUID.randomUUID().toString()
                    val testSubmitDate = System.currentTimeMillis()
                    given(descriptionsDaoMock.loadDescription(testId)).willReturn(
                        Single.just(
                            TransactionDescriptionDb(
                                testId,
                                TEST_SAFE,
                                it.transaction.wrapped.address,
                                it.transaction.wrapped.value?.value ?: BigInteger.ZERO,
                                it.transaction.wrapped.data ?: "",
                                it.transaction.operation.toInt().toBigInteger(),
                                BigInteger.TEN,
                                BigInteger.ZERO,
                                ERC20Token.ETHER_TOKEN.address,
                                BigInteger.ZERO,
                                it.transaction.wrapped.nonce ?: BigInteger.ZERO,
                                testSubmitDate,
                                "some-tx-hash"
                            )
                        )
                    )
                    given(descriptionsDaoMock.loadStatus(testId)).willReturn(
                        Single.just(
                            TransactionPublishStatusDb(
                                testId,
                                "chain-hash",
                                null,
                                null
                            )
                        )
                    )
                    val testObserver = TestObserver<TransactionInfo>()
                    repository.loadTransactionInfo(testId).subscribe(testObserver)
                    testObserver.assertResult(
                        TransactionInfo(
                            testId,
                            "chain-hash",
                            TEST_SAFE,
                            it.expected,
                            testSubmitDate,
                            BigInteger.valueOf(32010),
                            BigInteger.ZERO,
                            ERC20Token.ETHER_TOKEN.address
                        )
                    )
                }
            } ?: throw IllegalStateException("Missing tests for ${klass.simpleName}")
        }
    }

    private data class TestData(val transaction: SafeTransaction, val expected: TransactionData)

    companion object {
        private val TEST_SAFE = "0xA7e15e2e76Ab469F8681b576cFF168F37Aa246EC".asEthereumAddress()!!
        private val TEST_ADDRESS = "0xc257274276a4e539741ca11b590b9447b26a8051".asEthereumAddress()!!
        private val TEST_ETHER_TOKEN = Solidity.Address(BigInteger.ZERO)
        private val TEST_ETH_AMOUNT = Wei.ether("23")
        private val TEST_TOKEN_ADDRESS = "0xa7e15e2e76ab469f8681b576cff168f37aa246ec".asEthereumAddress()!!
        private val TEST_TOKEN_AMOUNT = BigInteger("230000000000")
        private val TEST_DATA_TRANSACTION_INFO = mapOf(
            TransactionData.AssetTransfer::class to
                    listOf(
                        TestData(
                            SafeTransaction(
                                Transaction(
                                    TEST_ADDRESS, value = TEST_ETH_AMOUNT
                                ), CALL
                            ),
                            TransactionData.AssetTransfer(TEST_ETHER_TOKEN, TEST_ETH_AMOUNT.value, TEST_ADDRESS)
                        ),
                        TestData(
                            SafeTransaction(
                                Transaction(
                                    TEST_TOKEN_ADDRESS, data = ERC20Contract.Transfer.encode(TEST_ADDRESS, Solidity.UInt256(TEST_TOKEN_AMOUNT))
                                ), CALL
                            ),
                            TransactionData.AssetTransfer(TEST_TOKEN_ADDRESS, TEST_TOKEN_AMOUNT, TEST_ADDRESS)
                        )
                    ),
            TransactionData.Generic::class to
                    listOf(
                        TestData(
                            SafeTransaction(
                                Transaction(
                                    TEST_ADDRESS, value = TEST_ETH_AMOUNT, data = "0x468721a8", nonce = BigInteger.valueOf(13)
                                ), CALL
                            ),
                            TransactionData.Generic(TEST_ADDRESS, TEST_ETH_AMOUNT.value, "0x468721a8")
                        ),
                        TestData(
                            SafeTransaction(
                                Transaction(
                                    TEST_TOKEN_ADDRESS,
                                    value = TEST_ETH_AMOUNT,
                                    data = ERC20Contract.Transfer.encode(TEST_ADDRESS, Solidity.UInt256(TEST_TOKEN_AMOUNT))
                                ), CALL
                            ),
                            TransactionData.Generic(
                                TEST_TOKEN_ADDRESS,
                                TEST_ETH_AMOUNT.value,
                                ERC20Contract.Transfer.encode(TEST_ADDRESS, Solidity.UInt256(TEST_TOKEN_AMOUNT))
                            )
                        ),
                        TestData(
                            SafeTransaction(
                                Transaction(
                                    TEST_ADDRESS, value = TEST_ETH_AMOUNT, data = "0x468721a7", nonce = BigInteger.valueOf(23)
                                ), DELEGATE_CALL
                            ),
                            TransactionData.Generic(TEST_ADDRESS, TEST_ETH_AMOUNT.value, "0x468721a7")
                        )
                    )
        )
    }
}
