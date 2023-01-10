package net.termer.twinemedia.util

import net.termer.twinemedia.dataobject.RowIdPair
import net.termer.twinemedia.model.AccountsModel
import net.termer.twinemedia.service.CryptoService

/**
 * Updates an account's password
 * @param internalId The account's internal ID
 * @param password The new password
 * @param cryptoService The [CryptoService] instance to use for hashing the password (defaults to [CryptoService.INSTANCE])
 * @param accountsModel The [AccountsModel] instance to use for updating the account in the database (defaults to [AccountsModel.INSTANCE])
 * @since 2.0.0
 */
suspend fun updateAccountPassword(
    internalId: Int,
    password: String,
    cryptoService: CryptoService = CryptoService.INSTANCE,
    accountsModel: AccountsModel = AccountsModel.INSTANCE
) {
	val hash = cryptoService.hashPassword(password)

	accountsModel.updateOne(
		AccountsModel.UpdateValues(hash = some(hash)),
		AccountsModel.Filters(whereInternalIdIs = some(internalId))
	)
}

/**
 * Creates a new account
 * @param email The new account's email
 * @param name The new account's name
 * @param isAdmin Whether the account will be an administrator
 * @param permissions The new account's permissions (defaults to empty)
 * @param password The new account's password
 * @param defaultSourceInternalId The internal ID of the new account's default file source, or null for none (defaults to null)
 * @param accountsModel The [AccountsModel] instance to use for creating the account in the database (defaults to [AccountsModel.INSTANCE])
 * @return The newly created account's IDs
 * @since 2.0.0
 */
suspend fun createAccount(
    email: String,
    name: String,
    isAdmin: Boolean,
    permissions: Array<String> = emptyArray(),
    password: String,
    defaultSourceInternalId: Int? = null,
    accountsModel: AccountsModel = AccountsModel.INSTANCE
): RowIdPair {
	val hash = CryptoService.INSTANCE.hashPassword(password)

	return accountsModel.createRow(email, name, isAdmin, permissions, hash, defaultSourceInternalId)
}