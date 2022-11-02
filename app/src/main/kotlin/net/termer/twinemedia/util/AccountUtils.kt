package net.termer.twinemedia.util

import net.termer.twinemedia.dataobject.RowIdPair
import net.termer.twinemedia.model.AccountsModel

/**
 * Updates an account's password
 * @param internalId The account's internal ID
 * @param password The new password
 * @since 2.0.0
 */
suspend fun updateAccountPassword(internalId: Int, password: String) {
	val hash = Crypto.INSTANCE.hashPassword(password)

	AccountsModel.INSTANCE.updateOne(
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
 * @param crypto The [Crypto] instance to use for hashing the password (defaults to [Crypto.INSTANCE])
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
	crypto: Crypto = Crypto.INSTANCE,
	accountsModel: AccountsModel = AccountsModel.INSTANCE
): RowIdPair {
	val hash = crypto.hashPassword(password)

	return accountsModel.createRow(email, name, isAdmin, permissions, hash, defaultSourceInternalId)
}